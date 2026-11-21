package com.hr.workflow.moteur;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.config.WorkflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client HTTP d'appel des endpoints d'effet chez les services propriétaires (§ 9.6.5, D-W3-03).
 * Connexion 1 s, lecture 3 s, aucun réessai automatique, en-tête {@code X-Internal-Token}, sans {@code X-Societe-Id}.
 * <strong>Jamais appelé dans une transaction.</strong>
 */
@Component
public class EffetClient {

	private static final Logger log = LoggerFactory.getLogger(EffetClient.class);
	private static final String ENTETE_JETON = "X-Internal-Token";

	private final WorkflowProperties properties;
	private final String jetonInterne;
	private final JsonMapper jsonMapper;
	private final Map<String, RestClient> clientsParService = new ConcurrentHashMap<>();

	public EffetClient(
			WorkflowProperties properties,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			JsonMapper jsonMapper) {
		this.properties = properties;
		this.jetonInterne = jetonInterne;
		this.jsonMapper = jsonMapper;
	}

	public record ActeurEffet(
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("nom") String nom,
			@JsonProperty("pour_nom") String pourNom
	) {
	}

	public record RequeteEffet(
			@JsonProperty("cle_idempotence") String cleIdempotence,
			@JsonProperty("mode") String mode,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("effet") String effet,
			@JsonProperty("acteur") ActeurEffet acteur,
			@JsonProperty("commentaire") String commentaire,
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("etape_libelle") String etapeLibelle
	) {
	}

	public sealed interface ResultatAppelEffet permits ResultatAppelEffet.Succes, ResultatAppelEffet.RejetMetier, ResultatAppelEffet.Incertain {
		record Succes(Map<String, Object> corps) implements ResultatAppelEffet {
		}

		record RejetMetier(int httpStatus, String code, String message, Map<String, Object> corps) implements ResultatAppelEffet {
		}

		record Incertain(String erreur) implements ResultatAppelEffet {
		}
	}

	public ResultatAppelEffet appeler(String serviceProprietaire, String cheminEffet, RequeteEffet requete) {
		RestClient client = clientPour(serviceProprietaire);
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = client.post()
					.uri(cheminEffet)
					.header(ENTETE_JETON, jetonInterne)
					.body(requete)
					.retrieve()
					.body(Map.class);
			return new ResultatAppelEffet.Succes(body != null ? body : Map.of("ok", true));
		} catch (HttpClientErrorException ex) {
			int status = ex.getStatusCode().value();
			if (status == 422 || status == 409) {
				return extraireRejetMetier(status, ex.getResponseBodyAsString());
			}
			log.warn("Effet {} sur {} refusé avec HTTP {}", requete.effet(), serviceProprietaire, status);
			return new ResultatAppelEffet.Incertain("HTTP " + status);
		} catch (RestClientException ex) {
			log.warn("Effet {} sur {} incertain ({})", requete.effet(), serviceProprietaire, ex.getMessage());
			return new ResultatAppelEffet.Incertain(ex.getMessage() != null ? ex.getMessage() : "Erreur réseau");
		}
	}

	private ResultatAppelEffet.RejetMetier extraireRejetMetier(int status, String bodyRaw) {
		String code = status == 409 ? "OBJET_DEJA_DECIDE" : "EFFET_REJETE";
		String message = "Le service propriétaire a refusé l'opération.";
		Map<String, Object> map = new LinkedHashMap<>();
		if (bodyRaw != null && !bodyRaw.isBlank()) {
			try {
				JsonNode node = jsonMapper.readTree(bodyRaw);
				if (node.has("code") && !node.get("code").isNull()) {
					code = node.get("code").asString();
				}
				if (node.has("message") && !node.get("message").isNull()) {
					message = node.get("message").asString();
				}
				map.put("code", code);
				map.put("message", message);
			} catch (Exception ignored) {
				map.put("raw", bodyRaw);
			}
		}
		return new ResultatAppelEffet.RejetMetier(status, code, message, map);
	}

	private RestClient clientPour(String serviceProprietaire) {
		return clientsParService.computeIfAbsent(serviceProprietaire, svc -> {
			WorkflowProperties.Proprietaires props = properties.getProprietaires();
			String baseUrl = props.baseUrlPour(svc);
			HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(props.getTimeoutConnexionMs()))
					.build();
			JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
			factory.setReadTimeout(Duration.ofMillis(props.getTimeoutLectureMs()));
			return RestClient.builder()
					.baseUrl(baseUrl)
					.requestFactory(factory)
					.build();
		});
	}
}
