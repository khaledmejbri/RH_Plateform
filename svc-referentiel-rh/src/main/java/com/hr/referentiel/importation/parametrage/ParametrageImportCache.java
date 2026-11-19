package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ChampPerso;
import com.hr.referentiel.champsperso.ChampPersoRepository;
import com.hr.referentiel.champsperso.ChampPersoValeurListe;
import com.hr.referentiel.champsperso.ChampPersoValeurListeRepository;
import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Instantané du paramétrage des champs d'import par type (ADR § 6), dans un cache Caffeine dédié de même durée de vie
 * que celui des paramètres (D-R02.j) : un changement est visible partout en moins d'une minute. L'écriture invalide
 * l'instantané local après commit. La lecture de l'écran (P1) ne passe pas par ici.
 */
@Component
public class ParametrageImportCache {

	private static final Logger log = LoggerFactory.getLogger(ParametrageImportCache.class);

	private final CacheManager cacheManager;
	private final ImportChampRegleRepository champRepository;
	private final ImportSynonymeRepository synonymeRepository;
	private final ImportParametrageRepository parametrageRepository;
	private final ChampPersoRepository champPersoRepository;
	private final ChampPersoValeurListeRepository valeurListeRepository;

	public ParametrageImportCache(CacheManager cacheManager, ImportChampRegleRepository champRepository,
			ImportSynonymeRepository synonymeRepository, ImportParametrageRepository parametrageRepository,
			ChampPersoRepository champPersoRepository, ChampPersoValeurListeRepository valeurListeRepository) {
		this.champPersoRepository = champPersoRepository;
		this.valeurListeRepository = valeurListeRepository;
		this.cacheManager = cacheManager;
		this.champRepository = champRepository;
		this.synonymeRepository = synonymeRepository;
		this.parametrageRepository = parametrageRepository;
	}

	public ParametrageImport courant(TypeImportParametrable type) {
		Cache cache = cacheManager.getCache(CacheConfig.CACHE_IMPORT_PARAMETRAGE);
		if (cache == null) {
			return charger(type);
		}
		ParametrageImport p = cache.get(CacheConfig.cle(type), () -> charger(type));
		return p != null ? p : charger(type);
	}

	/** En-têtes du modèle (D-I19) : champs « dans le modèle », dans l'ordre réglé, avec leur en-tête réglé. */
	public List<String> enTetesDuModele(TypeImportParametrable type) {
		return courant(type).champsDuModele().stream().map(ParametrageImport.ChampRegle::enTete).toList();
	}

	/** Invalidation après commit si une transaction est active (sinon, immédiate). */
	public void invaliderApresCommit(TypeImportParametrable type) {
		CacheConfig.CleSociete cleSociete = CacheConfig.cle(type);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalider(cleSociete);
				}
			});
		} else {
			invalider(cleSociete);
		}
	}

	public void invalider(TypeImportParametrable type) {
		invalider(CacheConfig.cle(type));
	}

	private void invalider(CacheConfig.CleSociete cleSociete) {
		Cache cache = cacheManager.getCache(CacheConfig.CACHE_IMPORT_PARAMETRAGE);
		if (cache != null) {
			cache.evict(cleSociete);
		}
	}

	/** Trois requêtes (version, champs, synonymes actifs) ; un champ absent de la base prend les valeurs du code. */
	ParametrageImport charger(TypeImportParametrable type) {
		String t = type.name();
		long version = parametrageRepository.findById(t).map(ImportParametrage::getVersion).orElse(0L);
		Map<String, ImportChampRegle> regles = champRepository.findByTypeImportOrderByOrdreAsc(t).stream()
				.collect(Collectors.toMap(ImportChampRegle::getChampCode, r -> r, (a, b) -> a));
		Map<String, List<ImportSynonyme>> actifs = synonymeRepository.findByTypeImportAndActifTrue(t).stream()
				.collect(Collectors.groupingBy(ImportSynonyme::getChampCode));
		ParametrageImport defaut = ParametrageImport.parDefaut(type);
		List<ParametrageImport.ChampRegle> champs = new ArrayList<>();
		int suite = regles.values().stream().mapToInt(ImportChampRegle::getOrdre).max().orElse(-1) + 1;
		for (ChampImport c : type.champs()) {
			ImportChampRegle r = regles.get(c.name());
			if (r == null) {
				log.warn("Champ d'import {}.{} sans réglage en base : valeurs du code", t, c.name());
				ParametrageImport.ChampRegle d = defaut.champ(c.name());
				champs.add(new ParametrageImport.ChampRegle(d.code(), d.enTete(), d.dansModele(), d.exigence(), suite++,
						d.formesActives()));
				continue;
			}
			Set<String> formes = new LinkedHashSet<>();
			actifs.getOrDefault(c.name(), List.of()).stream().sorted(Comparator.comparing(ImportSynonyme::getFormeNormalisee))
					.forEach(s -> formes.add(s.getFormeNormalisee()));
			formes.remove(ReconnaissanceEnTetes.normaliser(r.getEnTete()));
			champs.add(new ParametrageImport.ChampRegle(c.name(), r.getEnTete(), r.isDansModele(), r.getExigence(),
					r.getOrdre(), List.copyOf(formes)));
		}
		// champs personnalisés actifs (P2-2) : 2 requêtes de plus ; les inactifs sont absents de l'instantané
		List<ParametrageImport.DefinitionPerso> definitions = new ArrayList<>();
		List<ChampPerso> persos = champPersoRepository.findByTypeImportAndActifTrue(t);
		if (!persos.isEmpty()) {
			Map<java.util.UUID, List<ChampPersoValeurListe>> valeurs = valeurListeRepository
					.findByChampPersoIdInOrderByOrdreAsc(persos.stream().map(ChampPerso::getId).toList()).stream()
					.collect(Collectors.groupingBy(ChampPersoValeurListe::getChampPersoId));
			for (ChampPerso p : persos) {
				ImportChampRegle r = regles.get(p.getCode());
				if (r == null) {
					log.warn("Champ personnalisé {}.{} sans réglage en base : ignoré", t, p.getCode());
					continue;
				}
				Set<String> formes = new LinkedHashSet<>();
				actifs.getOrDefault(p.getCode(), List.of()).stream().sorted(Comparator.comparing(ImportSynonyme::getFormeNormalisee))
						.forEach(s -> formes.add(s.getFormeNormalisee()));
				formes.remove(ReconnaissanceEnTetes.normaliserComplet(r.getEnTete()));
				champs.add(new ParametrageImport.ChampRegle(p.getCode(), r.getEnTete(), r.isDansModele(), r.getExigence(),
						r.getOrdre(), List.copyOf(formes), true));
				definitions.add(new ParametrageImport.DefinitionPerso(p.getCode(), p.getLibelle(), p.getTypeValeur(), p.regles(),
						valeurs.getOrDefault(p.getId(), List.of()).stream()
								.map(v -> new ParametrageImport.ValeurListe(v.getCode(), v.getLibelle(), v.isActif())).toList()));
			}
		}
		champs.sort(Comparator.comparingInt(ParametrageImport.ChampRegle::ordre));
		return new ParametrageImport(t, version, List.copyOf(champs), List.copyOf(definitions));
	}
}
