package com.hr.presence.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
		// Délais d'attente configurables : un référentiel lent ne doit pas bloquer indéfiniment une requête de présence.
		PresenceProperties.Referentiel ref = properties.getReferentiel();
		java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
				.connectTimeout(java.time.Duration.ofMillis(ref.getTimeoutConnexionMs()))
				.build();
		JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
		fabrique.setReadTimeout(java.time.Duration.ofMillis(ref.getTimeoutLectureMs()));
		this.restClient = RestClient.builder().baseUrl(ref.getBaseUrl()).requestFactory(fabrique).build();
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

	/** Nombre maximal d'identifiants par appel d'affectation (limite du référentiel). */
	private static final int AFFECTATIONS_LOT = 100;

	/**
	 * Affectation actuelle (direction, département, service) de collaborateurs désignés par fiche ou compte, par lots
	 * de 100 (D-P17). Un identifiant inconnu est absent de la réponse. Référentiel injoignable : 503.
	 */
	public List<Affectation> affectations(List<UUID> identifiants) {
		List<Affectation> out = new java.util.ArrayList<>();
		try {
			for (int i = 0; i < identifiants.size(); i += AFFECTATIONS_LOT) {
				List<UUID> lot = identifiants.subList(i, Math.min(i + AFFECTATIONS_LOT, identifiants.size()));
				Affectation[] body = restClient.post()
						.uri("/internal/referentiel/collaborateurs/affectations")
						.header(EN_TETE_JETON_INTERNE, jetonInterne)
						.body(Map.of("collaborateur_ids", lot))
						.retrieve()
						.body(Affectation[].class);
				if (body != null) {
					out.addAll(List.of(body));
				}
			}
			return out;
		} catch (RestClientException ex) {
			throw referentielIndisponible(ex);
		}
	}

	/** Collaborateurs (fiche et compte) d'une unité et de ses sous-unités, tous statuts ; unité inconnue : liste vide. */
	public List<MembreUnite> membresDeUnite(UUID uniteId) {
		try {
			MembreUnite[] body = restClient.get()
					.uri("/internal/referentiel/unites/{uniteId}/collaborateurs", uniteId)
					.header(EN_TETE_JETON_INTERNE, jetonInterne)
					.retrieve()
					.body(MembreUnite[].class);
			return body == null ? List.of() : List.of(body);
		} catch (RestClientException ex) {
			throw referentielIndisponible(ex);
		}
	}

	/**
	 * Décision sensible (fiche d'équipe, D-N02) : {@code compteAppelant} (lu dans le JWT) est-il le valideur D-H03 de
	 * {@code collaborateurId} ? Jamais mis en cache ; référentiel injoignable ou trop lent : 503, aucune donnée.
	 * Collaborateur inconnu, compte sans fiche ou collaborateur inactif : {@code est_valideur = false}.
	 */
	public boolean estValideurDe(UUID collaborateurId, String compteAppelant) {
		try {
			ValideurDe body = restClient.get()
					.uri(uri -> uri.path("/internal/referentiel/collaborateurs/{collaborateurId}/valideur")
							.queryParam("compte_id", compteAppelant).build(collaborateurId))
					.header(EN_TETE_JETON_INTERNE, jetonInterne)
					.retrieve()
					.body(ValideurDe.class);
			return body != null && body.estValideur() && body.collaborateurActif() != Boolean.FALSE;
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

	/**
	 * Collaborateur avec son affectation actuelle (endpoint {@code /internal}). {@code posteLibelle} et
	 * {@code uniteLibelle} sont renvoyés par le référentiel depuis le lot 2 (absents d'un référentiel plus ancien).
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Affectation(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom") String nom,
			@JsonProperty("direction_libelle") String directionLibelle,
			@JsonProperty("departement_libelle") String departementLibelle,
			@JsonProperty("service_libelle") String serviceLibelle,
			@JsonProperty("poste_libelle") String posteLibelle,
			@JsonProperty("unite_libelle") String uniteLibelle
	) {

		/** Sans poste ni unité (référentiel d'avant le lot 2). */
		public Affectation(UUID collaborateurId, UUID compteId, String matricule, String nom, String directionLibelle,
						   String departementLibelle, String serviceLibelle) {
			this(collaborateurId, compteId, matricule, nom, directionLibelle, departementLibelle, serviceLibelle, null, null);
		}

		/** Unité la plus précise : celle fournie par le référentiel, sinon service, département, direction. */
		public String uniteAffichee() {
			if (uniteLibelle != null && !uniteLibelle.isBlank()) {
				return uniteLibelle;
			}
			if (serviceLibelle != null && !serviceLibelle.isBlank()) {
				return serviceLibelle;
			}
			if (departementLibelle != null && !departementLibelle.isBlank()) {
				return departementLibelle;
			}
			return directionLibelle;
		}
	}

	/** Réponse de {@code /internal/.../{id}/valideur?compte_id=} (ADR lot 2, § 2). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ValideurDe(
			@JsonProperty("est_valideur") boolean estValideur,
			@JsonProperty("collaborateur_actif") Boolean collaborateurActif,
			@JsonProperty("valideur_fiche_id") UUID valideurFicheId
	) {
	}

	/** Membre d'une unité : identifiants de fiche et de compte. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MembreUnite(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId
	) {
	}
}
