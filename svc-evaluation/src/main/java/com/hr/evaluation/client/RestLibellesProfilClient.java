package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /internal/referentiel/libelles-profil} (jeton interne). Résultat gardé 60 s ; un échec est gardé 10 s
 * pour ne pas payer le délai d'attente à chaque requête.
 */
@Component
public class RestLibellesProfilClient implements LibellesProfilClient {

	private static final Logger log = LoggerFactory.getLogger(RestLibellesProfilClient.class);
	private static final Duration DUREE_SUCCES = Duration.ofSeconds(60);
	private static final Duration DUREE_ECHEC = Duration.ofSeconds(10);

	private final RestClient restClient;
	private final String jetonInterne;
	private volatile LibellesProfil cache = LibellesProfil.VIDE;
	private volatile Instant expireLe = Instant.MIN;

	public RestLibellesProfilClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			@Value("${evaluation.referentiel.timeout-connexion-ms}") int delaiConnexionMs,
			@Value("${evaluation.referentiel.timeout-lecture-ms}") int delaiLectureMs) {
		this.restClient = ReferentielRestClients.creer(baseUrl, delaiConnexionMs, delaiLectureMs);
		this.jetonInterne = jetonInterne;
	}

	@Override
	public LibellesProfil lire() {
		if (Instant.now().isBefore(expireLe)) {
			return cache;
		}
		synchronized (this) {
			if (Instant.now().isBefore(expireLe)) {
				return cache;
			}
			try {
				LibellesProfil lu = charger();
				if (lu != null) {
					cache = lu;
					expireLe = Instant.now().plus(DUREE_SUCCES);
				} else {
					expireLe = Instant.now().plus(DUREE_ECHEC);
				}
			} catch (RuntimeException ex) {
				// On garde le dernier cache valide : seule la prochaine tentative est repoussée.
				log.warn("Libellés de profil indisponibles : {}", ex.getMessage());
				expireLe = Instant.now().plus(DUREE_ECHEC);
			}
			return cache;
		}
	}

	/** Appel du référentiel ; {@code null} si la réponse est vide. Redéfinissable pour les tests. */
	LibellesProfil charger() {
		Dto dto = restClient.get().uri("/internal/referentiel/libelles-profil")
				.header("X-Internal-Token", jetonInterne).retrieve().body(Dto.class);
		return dto == null ? null : new LibellesProfil(
				dto.familles == null ? Map.of() : dto.familles, dto.niveaux == null ? Map.of() : dto.niveaux);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("familles")
		Map<String, String> familles;
		@JsonProperty("niveaux")
		Map<String, String> niveaux;
	}
}
