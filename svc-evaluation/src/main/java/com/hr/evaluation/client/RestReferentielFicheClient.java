package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Appel {@code GET /internal/referentiel/collaborateurs/{id}/fiche-evaluation} (jeton interne, non routé par la
 * gateway). Délais courts : l'appel est fait pendant la validation du RO.
 */
@Component
public class RestReferentielFicheClient implements ReferentielFicheClient {

	private final RestClient restClient;
	private final String jetonInterne;

	public RestReferentielFicheClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
		fabrique.setReadTimeout(Duration.ofSeconds(2));
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrique).build();
		this.jetonInterne = jetonInterne;
	}

	@Override
	public Optional<FicheEvaluation> ficheEvaluation(UUID ficheId) {
		Dto dto = restClient.get()
				.uri("/internal/referentiel/collaborateurs/{id}/fiche-evaluation", ficheId)
				.header("X-Internal-Token", jetonInterne)
				.exchange((requete, reponse) -> {
					int statut = reponse.getStatusCode().value();
					if (statut == 404) {
						return null;
					}
					if (reponse.getStatusCode().isError()) {
						throw new IllegalStateException("Référentiel : statut " + statut);
					}
					return reponse.bodyTo(Dto.class);
				});
		if (dto == null) {
			return Optional.empty();
		}
		return Optional.of(new FicheEvaluation(dto.identifiant, dto.compteUtilisateurId, dto.matricule,
				dto.nomComplet, dto.uniteIdentifiant, dto.uniteLibelle, dto.statut));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("identifiant")
		UUID identifiant;
		@JsonProperty("compte_utilisateur_id")
		UUID compteUtilisateurId;
		@JsonProperty("matricule")
		String matricule;
		@JsonProperty("nom_complet")
		String nomComplet;
		@JsonProperty("unite_identifiant")
		UUID uniteIdentifiant;
		@JsonProperty("unite_libelle")
		String uniteLibelle;
		@JsonProperty("statut")
		String statut;
	}
}
