package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/** {@code GET /api/referentiel/v1/collaborateurs/moi} avec le JWT de l'appelant, délais d'attente configurables. */
@Component
public class RestReferentielMoiClient implements ReferentielMoiClient {

	private final RestClient restClient;

	public RestReferentielMoiClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${evaluation.referentiel.timeout-connexion-ms}") int delaiConnexionMs,
			@Value("${evaluation.referentiel.timeout-lecture-ms}") int delaiLectureMs) {
		this.restClient = ReferentielRestClients.creer(baseUrl, delaiConnexionMs, delaiLectureMs);
	}

	@Override
	public Optional<MoiReferentiel> moi() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof JwtAuthenticationToken jwt)) {
			return Optional.empty();
		}
		Dto dto = restClient.get()
				.uri("/api/referentiel/v1/collaborateurs/moi")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue())
				.exchange((requete, reponse) -> {
					// 404 ou 403 : compte sans fiche collaborateur (le référentiel répond 403 dans ce cas).
					int statut = reponse.getStatusCode().value();
					if (statut == 404 || statut == 403) {
						return null;
					}
					if (reponse.getStatusCode().isError()) {
						throw new IllegalStateException("Référentiel : statut " + reponse.getStatusCode().value());
					}
					return reponse.bodyTo(Dto.class);
				});
		if (dto == null || dto.identifiant == null) {
			return Optional.empty();
		}
		return Optional.of(new MoiReferentiel(dto.identifiant, dto.prenom, dto.nom));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("identifiant")
		UUID identifiant;
		@JsonProperty("prenom")
		String prenom;
		@JsonProperty("nom")
		String nom;
	}
}
