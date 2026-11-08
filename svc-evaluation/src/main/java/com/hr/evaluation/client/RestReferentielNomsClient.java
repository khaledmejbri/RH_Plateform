package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /internal/referentiel/collaborateurs/affectations} (jeton interne, non routé par la gateway) : un seul
 * appel pour tout un lot. Les champs ajoutés par le lot 2 (actif, poste, unité) sont facultatifs : un référentiel plus
 * ancien répond quand même, avec ces champs vides.
 */
@Component
public class RestReferentielNomsClient implements ReferentielNomsClient {

	private final RestClient restClient;
	private final String jetonInterne;

	public RestReferentielNomsClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			@Value("${evaluation.referentiel.timeout-connexion-ms}") int delaiConnexionMs,
			@Value("${evaluation.referentiel.timeout-lecture-ms}") int delaiLectureMs) {
		this.restClient = ReferentielRestClients.creer(baseUrl, delaiConnexionMs, delaiLectureMs);
		this.jetonInterne = jetonInterne;
	}

	@Override
	public List<PersonneResumee> resoudre(Collection<UUID> fichesOuComptes) {
		if (fichesOuComptes.isEmpty()) {
			return List.of();
		}
		Dto[] corps = restClient.post()
				.uri("/internal/referentiel/collaborateurs/affectations")
				.header("X-Internal-Token", jetonInterne)
				.body(Map.of("collaborateur_ids", List.copyOf(fichesOuComptes)))
				.retrieve()
				.body(Dto[].class);
		if (corps == null) {
			return List.of();
		}
		return Arrays.stream(corps)
				.map(d -> new PersonneResumee(d.collaborateurId, d.compteId, d.nomComplet, d.matricule, d.actif,
						d.posteLibelle, d.uniteId, d.uniteLibelle))
				.toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("collaborateur_id")
		UUID collaborateurId;
		@JsonProperty("compte_id")
		UUID compteId;
		@JsonProperty("matricule")
		String matricule;
		@JsonProperty("nom_complet")
		@JsonAlias("nom")
		String nomComplet;
		@JsonProperty("actif")
		Boolean actif;
		@JsonProperty("poste_libelle")
		String posteLibelle;
		@JsonProperty("unite_id")
		UUID uniteId;
		@JsonProperty("unite_libelle")
		String uniteLibelle;
	}
}
