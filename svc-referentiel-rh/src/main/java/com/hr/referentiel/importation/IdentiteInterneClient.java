package com.hr.referentiel.importation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client interne de svc-identite-acces (D-I07) : « ces identifiants ont-ils déjà un compte ? ». Délai de 3 s, adresse directe. Appelé à
 * l'analyse seulement, jamais pendant la transaction de confirmation. Jeton interne partagé, aucun secret en dur.
 */
@Component
public class IdentiteInterneClient {

	/** Panne ou délai dépassé : l'analyse continue avec l'avertissement {@code VERIFICATION_COMPTES_INDISPONIBLE}. */
	public static class IdentiteIndisponibleException extends RuntimeException {
		public IdentiteIndisponibleException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private record Requete(@JsonProperty("identifiants") List<String> identifiants) {
	}

	private record Reponse(@JsonProperty("existants") List<String> existants) {
	}

	private final RestClient client;
	private final String jeton;

	/**
	 * Client construit localement (aucun bean {@code RestClient.Builder} exposé : un builder équilibré par Eureka était
	 * récupéré par le client Eureka lui-même et empêchait l'enregistrement). Adresse directe, comme svc-presence et
	 * svc-evaluation vers leurs services voisins.
	 */
	public IdentiteInterneClient(
			@Value("${referentiel.identite.base-url:http://localhost:8081}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jeton,
			@Value("${referentiel.identite.delai-secondes:3}") long delaiSecondes) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(delaiSecondes)).build();
		JdkClientHttpRequestFactory usine = new JdkClientHttpRequestFactory(http);
		usine.setReadTimeout(Duration.ofSeconds(delaiSecondes));
		this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(usine).build();
		this.jeton = jeton;
	}

	/** Identifiants (en minuscules) qui ont déjà un compte, parmi ceux demandés. */
	public Set<String> identifiantsExistants(Collection<String> identifiants) {
		if (identifiants.isEmpty()) {
			return Set.of();
		}
		try {
			Reponse r = client.post().uri("/internal/users/identifiants-existants").header("X-Internal-Token", jeton)
					.body(new Requete(List.copyOf(identifiants))).retrieve().body(Reponse.class);
			return r == null || r.existants() == null ? Set.of()
					: r.existants().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		} catch (RuntimeException e) {
			throw new IdentiteIndisponibleException("Service d'identité indisponible", e);
		}
	}
}
