package com.hr.presence.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture référentiel avec le JWT de l'appelant (D-P04) : sa fiche et son valideur D-H03.
 * L'identité vient du jeton, pas de l'en-tête {@code X-Collaborateur-Id} fourni par le client.
 */
@Component
public class ReferentielClient {

	private static final Logger log = LoggerFactory.getLogger(ReferentielClient.class);
	private static final String EN_TETE_JETON_INTERNE = "X-Internal-Token";

	private final RestClient restClient;
	private final String jetonInterne;

	public ReferentielClient(
			PresenceProperties properties,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		this.restClient = RestClient.builder().baseUrl(properties.getReferentiel().getBaseUrl()).build();
		this.jetonInterne = jetonInterne;
	}

	/**
	 * Résolution des bénéficiaires d'heures sup (compte, matricule, direction, valideur D-H03) par appel interne.
	 * Un identifiant inconnu est absent de la réponse. Référentiel injoignable : 503, aucune création en mode dégradé.
	 */
	public List<CollaborateurResolu> resoudreCollaborateurs(List<UUID> collaborateurIds) {
		try {
			CollaborateurResolu[] body = restClient.post()
					.uri("/internal/referentiel/collaborateurs/resolution-pointage")
					.header(EN_TETE_JETON_INTERNE, jetonInterne)
					.body(Map.of("collaborateur_ids", collaborateurIds))
					.retrieve()
					.body(CollaborateurResolu[].class);
			return body == null ? List.of() : List.of(body);
		} catch (RestClientException ex) {
			throw referentielIndisponible(ex);
		}
	}

	/** Collaborateurs actifs dont le valideur D-H03 est {@code ficheId} : aide de saisie du RO. */
	public List<CollaborateurResolu> perimetreValidation(UUID ficheId) {
		try {
			CollaborateurResolu[] body = restClient.get()
					.uri("/internal/referentiel/collaborateurs/{ficheId}/perimetre-validation", ficheId)
					.header(EN_TETE_JETON_INTERNE, jetonInterne)
					.retrieve()
					.body(CollaborateurResolu[].class);
			return body == null ? List.of() : List.of(body);
		} catch (RestClientException ex) {
			throw referentielIndisponible(ex);
		}
	}

	private static PresenceMetierException referentielIndisponible(RestClientException ex) {
		log.warn("Référentiel indisponible : {}", ex.getMessage());
		return PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE",
				"Le référentiel RH est momentanément indisponible, réessayez plus tard");
	}

	public ValideurPointage monValideurPointage() {
		ValideurPointage body;
		try {
			body = restClient.get()
					.uri("/api/referentiel/v1/collaborateurs/moi/valideur-pointage")
					.header(HttpHeaders.AUTHORIZATION, bearer())
					.retrieve()
					.body(ValideurPointage.class);
		} catch (HttpClientErrorException ex) {
			// 4xx du référentiel : pas de fiche exploitable pour ce compte, ce n'est pas une panne
			throw new SecurityException("Fiche collaborateur introuvable pour ce compte");
		} catch (RestClientException ex) {
			throw referentielIndisponible(ex);
		}
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

	/** Collaborateur résolu par le référentiel (endpoints {@code /internal}). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CollaborateurResolu(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom") String nom,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("direction_id") UUID directionId,
			@JsonProperty("direction_libelle") String directionLibelle,
			@JsonProperty("valideur_id") UUID valideurId,
			@JsonProperty("valideur_compte_id") UUID valideurCompteId
	) {
	}
}
