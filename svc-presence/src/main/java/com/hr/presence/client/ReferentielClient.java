package com.hr.presence.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.config.PresenceProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Lecture référentiel avec le JWT de l'appelant (D-P04) : sa fiche et son valideur D-H03.
 * L'identité vient du jeton, pas de l'en-tête {@code X-Collaborateur-Id} fourni par le client.
 */
@Component
public class ReferentielClient {

	private final RestClient restClient;

	public ReferentielClient(PresenceProperties properties) {
		this.restClient = RestClient.builder().baseUrl(properties.getReferentiel().getBaseUrl()).build();
	}

	public ValideurPointage monValideurPointage() {
		ValideurPointage body = restClient.get()
				.uri("/api/referentiel/v1/collaborateurs/moi/valideur-pointage")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.retrieve()
				.body(ValideurPointage.class);
		if (body == null || body.collaborateurId() == null) {
			throw new SecurityException("Fiche collaborateur introuvable pour ce compte");
		}
		return body;
	}

	private static String bearer() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) {
			return "Bearer " + jwt.getToken().getTokenValue();
		}
		throw new SecurityException("Jeton d'authentification absent");
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ValideurPointage(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("collaborateur_compte_id") UUID collaborateurCompteId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("valideur_id") UUID valideurId,
			@JsonProperty("valideur_compte_id") UUID valideurCompteId,
			@JsonProperty("valideur_nom") String valideurNom
	) {
	}
}
