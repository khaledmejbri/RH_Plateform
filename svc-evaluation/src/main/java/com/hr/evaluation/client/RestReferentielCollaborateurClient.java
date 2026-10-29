package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class RestReferentielCollaborateurClient implements ReferentielCollaborateurClient {

	private static final Logger log = LoggerFactory.getLogger(RestReferentielCollaborateurClient.class);

	private final RestClient restClient;
	private final String jetonInterne;

	public RestReferentielCollaborateurClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
		this.jetonInterne = jetonInterne;
	}

	@Override
	public List<CollaborateurEvaluationSnapshot> listerActifsPourEvaluation() {
		try {
			String bearer = bearerOrEmpty();
			// Activation par la RH : jeton de l'utilisateur. Lancement automatique (SCRUM-42) : aucun utilisateur,
			// endpoint interne du référentiel protégé par le jeton partagé.
			Dto[] body = bearer.isEmpty()
					? restClient.get()
							.uri("/internal/referentiel/collaborateurs/actifs-evaluation")
							.header("X-Internal-Token", jetonInterne)
							.retrieve()
							.body(Dto[].class)
					: restClient.get()
							.uri("/api/referentiel/v1/collaborateurs/actifs-evaluation")
							.header(HttpHeaders.AUTHORIZATION, bearer)
							.retrieve()
							.body(Dto[].class);
			if (body == null || body.length == 0) {
				return List.of();
			}
			return Arrays.stream(body)
					.map(d -> new CollaborateurEvaluationSnapshot(
							d.identifiant,
							d.statut,
							d.familleMetierCode,
							d.niveauSeniorite,
							d.superieurIdentifiant,
							d.profilAcces,
							d.compteUtilisateurId,
							d.nomComplet,
							d.posteLibelle,
							d.superieurCompteUtilisateurId,
							d.superieurNomComplet))
					.toList();
		} catch (Exception ex) {
			log.error("Échec lecture population ACTIF depuis référentiel: {}", ex.getMessage());
			throw new IllegalStateException(
					"Impossible de charger les collaborateurs ACTIFS depuis le référentiel: " + ex.getMessage(), ex);
		}
	}

	private static String bearerOrEmpty() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) {
			return "Bearer " + jwt.getToken().getTokenValue();
		}
		return "";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("identifiant")
		UUID identifiant;
		@JsonProperty("statut")
		String statut;
		@JsonProperty("famille_metier_code")
		String familleMetierCode;
		@JsonProperty("niveau_seniorite")
		String niveauSeniorite;
		@JsonProperty("superieur_identifiant")
		UUID superieurIdentifiant;
		@JsonProperty("profil_acces")
		String profilAcces;
		@JsonProperty("compte_utilisateur_id")
		UUID compteUtilisateurId;
		@JsonProperty("nom_complet")
		String nomComplet;
		@JsonProperty("poste_libelle")
		String posteLibelle;
		@JsonProperty("superieur_compte_utilisateur_id")
		UUID superieurCompteUtilisateurId;
		@JsonProperty("superieur_nom_complet")
		String superieurNomComplet;
	}
}
