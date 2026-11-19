package com.hr.referentiel.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hr.commun.societe.SocietesActives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client HTTP local vers le registre des sociétés de {@code svc-identite-acces} (§ 9.2.1, § 9.2.3).
 * Implémente {@link SocietesActives} avec un cache Caffeine de 60 s.
 */
@Component
public class IdentiteSocieteClient implements SocietesActives {

	private static final Logger log = LoggerFactory.getLogger(IdentiteSocieteClient.class);
	private static final String CLE_ACTIVES = "actives";

	public record SocieteResume(
			@JsonProperty("id") UUID id,
			@JsonProperty("code") String code,
			@JsonProperty("statut") String statut,
			@JsonProperty("fuseau") String fuseau
	) {
	}

	private final RestClient client;
	private final String jetonInterne;
	private final Cache<String, List<UUID>> cacheActives;
	private final Cache<String, UUID> cacheParCode;

	public IdentiteSocieteClient(
			@Value("${referentiel.identite.base-url:http://localhost:8081}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			@Value("${referentiel.identite.delai-secondes:3}") long delaiSecondes) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(delaiSecondes)).build();
		JdkClientHttpRequestFactory usine = new JdkClientHttpRequestFactory(http);
		usine.setReadTimeout(Duration.ofSeconds(delaiSecondes));
		this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(usine).build();
		this.jetonInterne = jetonInterne;
		this.cacheActives = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).maximumSize(1).build();
		this.cacheParCode = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(100).build();
	}

	@Override
	public List<UUID> lister() {
		List<UUID> resultat = cacheActives.getIfPresent(CLE_ACTIVES);
		if (resultat != null) {
			return resultat;
		}
		List<SocieteResume> societes = client.get()
				.uri("/internal/identite/v1/societes?statut=ACTIVE")
				.header("X-Internal-Token", jetonInterne)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});
		List<UUID> ids = societes == null ? List.of() : societes.stream().map(SocieteResume::id).toList();
		cacheActives.put(CLE_ACTIVES, ids);
		return ids;
	}

	public void invaliderCacheActives() {
		cacheActives.invalidateAll();
	}

	public Optional<UUID> trouverIdParCode(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		String codeNormalise = code.trim().toUpperCase(java.util.Locale.ROOT);
		UUID cache = cacheParCode.getIfPresent(codeNormalise);
		if (cache != null) {
			return Optional.of(cache);
		}
		try {
			SocieteResume s = client.get()
					.uri("/internal/identite/v1/societes/par-code/{code}", codeNormalise)
					.header("X-Internal-Token", jetonInterne)
					.retrieve()
					.body(SocieteResume.class);
			if (s != null && s.id() != null) {
				cacheParCode.put(codeNormalise, s.id());
				return Optional.of(s.id());
			}
		} catch (RuntimeException e) {
			log.debug("Échec lecture société par code {} : {}", codeNormalise, e.getMessage());
		}
		return Optional.empty();
	}

	/**
	 * § 9.2.3 : attend la société par défaut auprès de {@code svc-identite-acces} (réessai toutes les 5 s pendant 5 min).
	 */
	public UUID attendreSocieteParCode(String code, Duration delaiMax, Duration intervalle) {
		long fin = System.nanoTime() + delaiMax.toNanos();
		RuntimeException derniereErreur = null;
		while (true) {
			try {
				Optional<UUID> id = trouverIdParCode(code);
				if (id.isPresent()) {
					return id.get();
				}
			} catch (RuntimeException e) {
				derniereErreur = e;
			}
			if (System.nanoTime() >= fin) {
				throw new IllegalStateException(
						"Impossible d'obtenir l'identifiant de la société par défaut '" + code
								+ "' auprès de svc-identite-acces dans le délai imparti (" + delaiMax.toMinutes() + " min).",
						derniereErreur);
			}
			log.info("En attente de la société par défaut '{}' auprès de svc-identite-acces...", code);
			try {
				Thread.sleep(intervalle.toMillis());
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Attente de la société par défaut interrompue", ie);
			}
		}
	}
}
