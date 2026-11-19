package com.hr.referentiel.config;

import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.importation.parametrage.ParametrageImportCache;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Amorçage idempotent du paramétrage des champs d'import (ADR-IMPORT-CHAMPS § 4). Rejouable à chaque démarrage : une
 * ligne existante n'est jamais modifiée, seuls les manques sont comblés (champs ajoutés au code, synonymes par défaut
 * ajoutés au code). N'incrémente pas la version et n'écrit pas au journal. Sous verrou consultatif PostgreSQL : deux
 * instances qui démarrent ensemble ne s'amorcent pas deux fois.
 */
@Component
public class ImportChampsAmorceRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ImportChampsAmorceRunner.class);

	/** Clé du verrou consultatif PostgreSQL (propre à cet amorçage, distincte de celle des paramètres du lot 3). */
	static final long VERROU_AMORCAGE = 7_001_903_002L;

	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final ParametrageImportCache cache;

	public ImportChampsAmorceRunner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			ParametrageImportCache cache) {
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.cache = cache;
	}

	@Override
	public void afterSingletonsInstantiated() {
		amorcer();
	}

	/** Point d'entrée rejouable (aussi appelé par les tests). */
	public void amorcer() {
		verifierCoherence();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			if (!verrouiller()) {
				log.info("Paramétrage des champs d'import : amorçage confié à une autre instance");
				return;
			}
			int champs = 0;
			int synonymes = 0;
			for (TypeImportParametrable type : TypeImportParametrable.values()) {
				amorcerLigneVersion(type);
				champs += amorcerChamps(type);
				synonymes += amorcerSynonymes(type);
				signalerCodesDisparus(type);
			}
			if (champs + synonymes > 0) {
				log.info("Paramétrage des champs d'import amorcé : {} champ(s), {} synonyme(s) par défaut", champs, synonymes);
			}
		});
		for (TypeImportParametrable type : TypeImportParametrable.values()) {
			cache.invalider(type);
		}
	}

	private boolean verrouiller() {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		if (produit == null || !produit.toLowerCase(Locale.ROOT).contains("postgres")) {
			return true;
		}
		Boolean obtenu = jdbc.queryForObject("select pg_try_advisory_xact_lock(" + VERROU_AMORCAGE + ")", Boolean.class);
		return Boolean.TRUE.equals(obtenu);
	}

	private void amorcerLigneVersion(TypeImportParametrable type) {
		Integer n = jdbc.queryForObject("select count(*) from rh_import_parametrage where type_import = ?", Integer.class,
				type.name());
		if (n == null || n == 0) {
			jdbc.update("insert into rh_import_parametrage (type_import, version) values (?, 0)", type.name());
		}
	}

	/** Champ du code sans ligne : inséré aux valeurs du code, en fin d'ordre. Une ligne existante n'est jamais modifiée. */
	private int amorcerChamps(TypeImportParametrable type) {
		Set<String> existants = new HashSet<>(jdbc.queryForList(
				"select champ_code from rh_import_champ where type_import = ?", String.class, type.name()));
		Integer max = jdbc.queryForObject("select max(ordre) from rh_import_champ where type_import = ?", Integer.class,
				type.name());
		int ordre = max == null ? 0 : max + 1;
		int inseres = 0;
		for (ChampImport c : type.champs()) {
			if (existants.contains(c.name())) {
				continue;
			}
			jdbc.update("insert into rh_import_champ (id, type_import, champ_code, en_tete, dans_modele, exigence, ordre) "
					+ "values (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), type.name(), c.name(), c.libelle(), true,
					c.exigenceDefaut().name(), ordre++);
			inseres++;
		}
		return inseres;
	}

	/**
	 * Synonymes par défaut du code : inséré si {@code (type, champ, forme)} n'existe pas ; inactif (avec WARN) si la forme
	 * est déjà un nom actif d'un autre champ du type (jamais de conflit créé par l'amorçage).
	 */
	private int amorcerSynonymes(TypeImportParametrable type) {
		Set<String> connus = new HashSet<>();
		Map<String, Set<String>> nomsActifs = new java.util.HashMap<>(); // forme -> champs qui la portent
		jdbc.query("select champ_code, forme_normalisee, actif from rh_import_synonyme where type_import = ?", rs -> {
			String champ = rs.getString(1);
			String forme = rs.getString(2);
			connus.add(champ + "|" + forme);
			if (rs.getBoolean(3)) {
				nomsActifs.computeIfAbsent(forme, k -> new HashSet<>()).add(champ);
			}
		}, type.name());
		jdbc.query("select champ_code, en_tete from rh_import_champ where type_import = ?", rs -> {
			nomsActifs.computeIfAbsent(ReconnaissanceEnTetes.normaliser(rs.getString(2)), k -> new HashSet<>())
					.add(rs.getString(1));
		}, type.name());
		int inseres = 0;
		for (ChampImport c : type.champs()) {
			for (String s : new LinkedHashSet<>(c.synonymes())) {
				String forme = ReconnaissanceEnTetes.normaliser(s);
				if (forme.isEmpty() || !connus.add(c.name() + "|" + forme)) {
					continue;
				}
				boolean conflit = nomsActifs.getOrDefault(forme, Set.of()).stream().anyMatch(autre -> !autre.equals(c.name()));
				if (conflit) {
					log.warn("Synonyme par défaut {}.{} « {} » déjà reconnu pour un autre champ : inséré inactif", type.name(),
							c.name(), s);
				} else {
					nomsActifs.computeIfAbsent(forme, k -> new HashSet<>()).add(c.name());
				}
				jdbc.update("insert into rh_import_synonyme (id, type_import, champ_code, texte, forme_normalisee, origine, "
						+ "actif, cree_le, cree_par_nom) values (?, ?, ?, ?, ?, ?, ?, ?, null)", UUID.randomUUID(),
						type.name(), c.name(), s, forme, "DEFAUT", !conflit, Timestamp.from(Instant.now()));
				inseres++;
			}
		}
		return inseres;
	}

	/** Un code présent en base mais plus dans le code est ignoré à la lecture, jamais supprimé. */
	private void signalerCodesDisparus(TypeImportParametrable type) {
		for (String code : codesDisparus(type)) {
			log.warn("Champ d'import {}.{} absent du code applicatif : laissé en place, ignoré", type.name(), code);
		}
	}

	/** Codes en base ni dans le code applicatif ni parmi les champs personnalisés (lignes {@code PERSO_…}). */
	List<String> codesDisparus(TypeImportParametrable type) {
		Set<String> attendus = new HashSet<>();
		type.champs().forEach(c -> attendus.add(c.name()));
		attendus.addAll(jdbc.queryForList("select code from rh_champ_perso where type_import = ?", String.class, type.name()));
		return jdbc.queryForList("select champ_code from rh_import_champ where type_import = ?", String.class, type.name())
				.stream().filter(code -> !attendus.contains(code)).toList();
	}

	/** Garde-fous sur les enums du code (log ERROR, sans arrêt) : le libellé est un synonyme, aucune forme partagée. */
	void verifierCoherence() {
		for (TypeImportParametrable type : TypeImportParametrable.values()) {
			Map<String, String> proprietaire = new java.util.HashMap<>();
			for (ChampImport c : type.champs()) {
				Set<String> formes = new HashSet<>();
				c.synonymes().forEach(s -> formes.add(ReconnaissanceEnTetes.normaliser(s)));
				if (!formes.contains(ReconnaissanceEnTetes.normaliser(c.libelle()))) {
					log.error("Champ d'import {}.{} : le libellé « {} » n'est pas dans ses synonymes par défaut", type.name(),
							c.name(), c.libelle());
				}
				for (String f : formes) {
					String autre = proprietaire.putIfAbsent(f, c.name());
					if (autre != null && !autre.equals(c.name())) {
						log.error("Synonyme par défaut « {} » partagé par {}.{} et {}.{}", f, type.name(), autre, type.name(),
								c.name());
					}
				}
			}
		}
	}
}
