package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.entity.ParametreValeur;
import com.hr.referentiel.repository.ParametreValeurRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Instantané immuable de tous les paramètres (ADR-UAT-lot3 § 4) dans un cache Caffeine dédié, une seule clé, durée de
 * vie 30 s (D-R02.j : un changement est visible partout en moins d'une minute, y compris sur une autre instance qui ne
 * reçoit aucune invalidation). L'écriture invalide l'instantané local après commit.
 */
@Component
public class ParametresCache {

	static final String CLE = "instantane";

	/** Vue en lecture seule d'une ligne (jamais l'entité JPA). */
	public record ParametreVue(String referentiel, String code, NatureParametre nature, String libelle,
			String description, String couleur, int ordre, boolean actif, boolean systeme, Integer slaHeures,
			Boolean pieceJointeObligatoire, Boolean pieceJointeVerrouillee) {

		public static ParametreVue de(ParametreValeur p) {
			return new ParametreVue(p.getReferentiel(), p.getCode(), p.getNature(), p.getLibelle(),
					p.getDescription(), p.getCouleur(), p.getOrdre(), p.isActif(), p.isSysteme(), p.getSlaHeures(),
					p.getPieceJointeObligatoire(), p.getPieceJointeVerrouillee());
		}
	}

	/** Tous les paramètres d'un coup. */
	public static final class Instantane {

		private final Map<String, List<ParametreVue>> parReferentiel;
		private final Map<String, Map<String, ParametreVue>> index;
		private final String empreinte;

		Instantane(List<ParametreVue> vues) {
			Map<String, List<ParametreVue>> liste = new LinkedHashMap<>();
			Map<String, Map<String, ParametreVue>> idx = new HashMap<>();
			vues.stream()
					.sorted(Comparator.comparing(ParametreVue::referentiel)
							.thenComparingInt(ParametreVue::ordre)
							.thenComparing(ParametreVue::libelle, String.CASE_INSENSITIVE_ORDER))
					.forEach(v -> {
						liste.computeIfAbsent(v.referentiel(), k -> new java.util.ArrayList<>()).add(v);
						idx.computeIfAbsent(v.referentiel(), k -> new HashMap<>()).put(v.code(), v);
					});
			liste.replaceAll((k, v) -> List.copyOf(v));
			this.parReferentiel = Collections.unmodifiableMap(liste);
			this.index = idx;
			this.empreinte = calculerEmpreinte(liste);
		}

		public List<ParametreVue> liste(String referentiel) {
			return parReferentiel.getOrDefault(referentiel, List.of());
		}

		public Map<String, List<ParametreVue>> tout() {
			return parReferentiel;
		}

		public Optional<ParametreVue> vue(String referentiel, String code) {
			if (code == null) {
				return Optional.empty();
			}
			return Optional.ofNullable(index.getOrDefault(referentiel, Map.of()).get(code));
		}

		/** Libellé courant d'un code ; {@code null} si le code n'existe pas. */
		public String libelle(String referentiel, String code) {
			return vue(referentiel, code).map(ParametreVue::libelle).orElse(null);
		}

		/** Empreinte du contenu : sert d'ETag aux endpoints de libellés. */
		public String empreinte() {
			return empreinte;
		}

		private static String calculerEmpreinte(Map<String, List<ParametreVue>> liste) {
			StringBuilder sb = new StringBuilder();
			liste.forEach((ref, vues) -> vues.forEach(v -> sb.append(ref).append('|').append(v.code()).append('|')
					.append(v.libelle()).append('|').append(v.description()).append('|').append(v.couleur())
					.append('|').append(v.ordre()).append('|').append(v.actif()).append('|').append(v.slaHeures())
					.append('|').append(v.pieceJointeObligatoire()).append('\n')));
			try {
				byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
				StringBuilder hex = new StringBuilder();
				for (int i = 0; i < 8; i++) {
					hex.append(String.format("%02x", h[i]));
				}
				return hex.toString();
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private final CacheManager cacheManager;
	private final ParametreValeurRepository repository;

	public ParametresCache(CacheManager cacheManager, ParametreValeurRepository repository) {
		this.cacheManager = cacheManager;
		this.repository = repository;
	}

	public Instantane instantane() {
		Cache cache = cacheManager.getCache(CacheConfig.CACHE_PARAMETRES);
		if (cache == null) {
			return charger();
		}
		Instantane i = cache.get(CLE, this::charger);
		return i != null ? i : charger();
	}

	/** Invalidation après commit si une transaction est active (sinon, immédiate). */
	public void invaliderApresCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalider();
				}
			});
		} else {
			invalider();
		}
	}

	public void invalider() {
		Cache cache = cacheManager.getCache(CacheConfig.CACHE_PARAMETRES);
		if (cache != null) {
			cache.evict(CLE);
		}
	}

	private Instantane charger() {
		return new Instantane(repository.findAll().stream().map(ParametreVue::de).toList());
	}
}
