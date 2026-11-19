package com.hr.referentiel.config;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.importation.parametrage.ParametrageImportCache;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
@Order(1)
public class ImportChampsAmorceRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImportChampsAmorceRunner.class);

	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final ParametrageImportCache cache;
	private final SocietesActives societesActives;

	@Autowired
	public ImportChampsAmorceRunner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			ParametrageImportCache cache, SocietesActives societesActives) {
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.cache = cache;
		this.societesActives = societesActives;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (UUID societeId : societesActives.lister()) {
			amorcerPourSociete(societeId);
		}
	}

	public void amorcerPourSociete(UUID societeId) {
		SocieteContexte.executer(societeId, this::amorcer);
	}

	/** Point d'entrée rejouable (aussi appelé par les tests, sous {@link SocieteContexte}). */
	public void amorcer() {
		UUID societeId = SocieteContexte.courante();
		verifierCoherence();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			if (!verrouiller(societeId)) {
				log.info("Paramétrage des champs d'import ({}) : amorçage confié à une autre instance", societeId);
				return;
			}
			int champs = 0;
			int synonymes = 0;
			for (TypeImportParametrable type : TypeImportParametrable.values()) {
				amorcerLigneVersion(societeId, type);
				champs += amorcerChamps(societeId, type);
				synonymes += amorcerSynonymes(societeId, type);
				signalerCodesDisparus(societeId, type);
			}
			if (champs + synonymes > 0) {
				log.info("Paramétrage des champs d'import amorcé pour {} : {} champ(s), {} synonyme(s) par défaut",
						societeId, champs, synonymes);
			}
		});
		for (TypeImportParametrable type : TypeImportParametrable.values()) {
			cache.invalider(type);
		}
	}

	private boolean verrouiller(UUID societeId) {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		if (produit == null || !produit.toLowerCase(Locale.ROOT).contains("postgres")) {
			return true;
		}
		Boolean obtenu = jdbc.queryForObject(
				"select pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, "amorce-import-champs:" + societeId);
		return Boolean.TRUE.equals(obtenu);
	}

	private void amorcerLigneVersion(UUID societeId, TypeImportParametrable type) {
		Integer n = jdbc.queryForObject(
				"select count(*) from rh_import_parametrage where societe_id = ? and type_import = ?",
				Integer.class, societeId, type.name());
		if (n == null || n == 0) {
			jdbc.update("insert into rh_import_parametrage (societe_id, type_import, version) values (?, ?, 0)",
					societeId, type.name());
		}
	}

	/** Champ du code sans ligne : inséré aux valeurs du code, en fin d'ordre. Une ligne existante n'est jamais modifiée. */
	private int amorcerChamps(UUID societeId, TypeImportParametrable type) {
		Set<String> existants = new HashSet<>(jdbc.queryForList(
				"select champ_code from rh_import_champ where societe_id = ? and type_import = ?",
				String.class, societeId, type.name()));
		Integer max = jdbc.queryForObject(
				"select max(ordre) from rh_import_champ where societe_id = ? and type_import = ?",
				Integer.class, societeId, type.name());
		int ordre = max == null ? 0 : max + 1;
		int inseres = 0;
		for (ChampImport c : type.champs()) {
			if (existants.contains(c.name())) {
				continue;
			}
			jdbc.update("insert into rh_import_champ (id, societe_id, type_import, champ_code, en_tete, dans_modele, exigence, ordre) "
					+ "values (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), societeId, type.name(), c.name(), c.libelle(), true,
					c.exigenceDefaut().name(), ordre++);
			inseres++;
		}
		return inseres;
	}

	/**
	 * Synonymes par défaut du code : inséré si {@code (type, champ, forme)} n'existe pas ; inactif (avec WARN) si la forme
	 * est déjà un nom actif d'un autre champ du type (jamais de conflit créé par l'amorçage).
	 */
	private int amorcerSynonymes(UUID societeId, TypeImportParametrable type) {
		Set<String> connus = new HashSet<>();
		Map<String, Set<String>> nomsActifs = new java.util.HashMap<>(); // forme -> champs qui la portent
		jdbc.query("select champ_code, forme_normalisee, actif from rh_import_synonyme where societe_id = ? and type_import = ?", rs -> {
			String champ = rs.getString(1);
			String forme = rs.getString(2);
			connus.add(champ + "|" + forme);
			if (rs.getBoolean(3)) {
				nomsActifs.computeIfAbsent(forme, k -> new HashSet<>()).add(champ);
			}
		}, societeId, type.name());
		jdbc.query("select champ_code, en_tete from rh_import_champ where societe_id = ? and type_import = ?", rs -> {
			nomsActifs.computeIfAbsent(ReconnaissanceEnTetes.normaliser(rs.getString(2)), k -> new HashSet<>())
					.add(rs.getString(1));
		}, societeId, type.name());
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
				jdbc.update("insert into rh_import_synonyme (id, societe_id, type_import, champ_code, texte, forme_normalisee, origine, "
						+ "actif, cree_le, cree_par_nom) values (?, ?, ?, ?, ?, ?, ?, ?, ?, null)", UUID.randomUUID(), societeId,
						type.name(), c.name(), s, forme, "DEFAUT", !conflit, Timestamp.from(Instant.now()));
				inseres++;
			}
		}
		return inseres;
	}

	/** Un code présent en base mais plus dans le code est ignoré à la lecture, jamais supprimé. */
	private void signalerCodesDisparus(UUID societeId, TypeImportParametrable type) {
		for (String code : codesDisparus(societeId, type)) {
			log.warn("Champ d'import {}.{} absent du code applicatif : laissé en place, ignoré", type.name(), code);
		}
	}

	/** Codes en base ni dans le code applicatif ni parmi les champs personnalisés (lignes {@code PERSO_…}). */
	List<String> codesDisparus(TypeImportParametrable type) {
		return codesDisparus(SocieteContexte.courante(), type);
	}

	List<String> codesDisparus(UUID societeId, TypeImportParametrable type) {
		Set<String> attendus = new HashSet<>();
		type.champs().forEach(c -> attendus.add(c.name()));
		attendus.addAll(jdbc.queryForList(
				"select code from rh_champ_perso where societe_id = ? and type_import = ?",
				String.class, societeId, type.name()));
		return jdbc.queryForList(
						"select champ_code from rh_import_champ where societe_id = ? and type_import = ?",
						String.class, societeId, type.name())
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
