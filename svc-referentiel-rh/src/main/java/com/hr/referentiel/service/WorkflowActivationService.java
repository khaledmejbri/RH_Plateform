package com.hr.referentiel.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Lecture du mode d'activation d'un type d'objet ({@code HERITE} ou {@code MOTEUR}, D-W5-03).
 * Cache Caffeine 30 s par type ; en cas d'erreur / moteur injoignable, renvoie {@code HERITE} sans mettre en cache.
 */
@Service
public class WorkflowActivationService {

	public static final String MODE_HERITE = "HERITE";
	public static final String MODE_MOTEUR = "MOTEUR";

	private static final Logger log = LoggerFactory.getLogger(WorkflowActivationService.class);

	private final WorkflowMoteurClient client;
	private final Cache<String, String> cache;

	@Autowired
	public WorkflowActivationService(WorkflowMoteurClient client) {
		this(client, Ticker.systemTicker());
	}

	WorkflowActivationService(WorkflowMoteurClient client, Ticker ticker) {
		this.client = client;
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofSeconds(30))
				.maximumSize(64)
				.ticker(ticker)
				.build();
	}

	public String mode(TypeDemandeAdministrativeRh type) {
		return type == null ? MODE_HERITE : mode(type.name());
	}

	public boolean estMoteur(TypeDemandeAdministrativeRh type) {
		return MODE_MOTEUR.equals(mode(type));
	}

	public String mode(String objetType) {
		if (objetType == null || objetType.isBlank()) {
			return MODE_HERITE;
		}
		String enCache = cache.getIfPresent(objetType);
		if (enCache != null) {
			return enCache;
		}
		Optional<String> lu = client.modeActivation(objetType);
		if (lu.isEmpty()) {
			log.warn("Mode workflow indisponible pour objetType={} : repli {} non mis en cache", objetType, MODE_HERITE);
			return MODE_HERITE;
		}
		String valeur = lu.get();
		if (!MODE_HERITE.equals(valeur) && !MODE_MOTEUR.equals(valeur)) {
			log.warn("Mode workflow illisible pour objetType={} : repli {} non mis en cache", objetType, MODE_HERITE);
			return MODE_HERITE;
		}
		cache.put(objetType, valeur);
		return valeur;
	}

	/** Permet d'invalider le cache (tests). */
	public void invaliderCache() {
		cache.invalidateAll();
	}
}
