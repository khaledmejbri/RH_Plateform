package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.evaluation.web.EvaluationMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Appel interne au référentiel (jeton {@code X-Internal-Token}, comme les autres clients internes, délais d'attente
 * bornés). Toute panne ou endpoint absent (référentiel plus ancien) devient un 503 : seule la création d'un template
 * technique est concernée, jamais le reste du service (ADR § 8).
 * <p>
 * Réponse attendue : {@code {"valides":[...],"inactifs":[...],"inconnus":[...]}}, chaque élément étant soit un code
 * texte, soit un objet {@code {"referentiel":"…","code":"…"}}.
 */
@Component
public class RestReferentielParametresClient implements ReferentielParametresClient {

	private static final Logger log = LoggerFactory.getLogger(RestReferentielParametresClient.class);

	public static final String REFERENTIEL_INDISPONIBLE = "REFERENTIEL_INDISPONIBLE";

	private final RestClient restClient;
	private final String jetonInterne;

	public RestReferentielParametresClient(
			@Value("${evaluation.referentiel.base-url:http://localhost:8083}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			@Value("${evaluation.referentiel.timeout-connexion-ms}") int delaiConnexionMs,
			@Value("${evaluation.referentiel.timeout-lecture-ms}") int delaiLectureMs) {
		this.restClient = ReferentielRestClients.creer(baseUrl, delaiConnexionMs, delaiLectureMs);
		this.jetonInterne = jetonInterne;
	}

	@Override
	public Resultat utiliser(List<Reference> references) {
		try {
			Dto dto = restClient.post().uri("/internal/referentiel/parametres/utiliser")
					.header("X-Internal-Token", jetonInterne)
					.body(Map.of("references", references.stream()
							.map(r -> Map.of("referentiel", r.referentiel(), "code", r.code())).toList()))
					.retrieve().body(Dto.class);
			if (dto == null) {
				throw new IllegalStateException("réponse vide");
			}
			return new Resultat(codes(dto.valides), codes(dto.inactifs), codes(dto.inconnus));
		} catch (RuntimeException ex) {
			log.warn("Validation des paramètres au référentiel impossible : {}", ex.getMessage());
			throw EvaluationMetierException.unavailable(REFERENTIEL_INDISPONIBLE,
					"Le référentiel RH ne répond pas : impossible de valider la famille ou le niveau pour le moment. "
							+ "Réessayez dans un instant.");
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> codes(List<Object> elements) {
		List<String> codes = new ArrayList<>();
		if (elements == null) {
			return codes;
		}
		for (Object e : elements) {
			if (e instanceof Map<?, ?> m && m.get("code") != null) {
				codes.add(String.valueOf(m.get("code")));
			} else if (e instanceof String s) {
				codes.add(s);
			}
		}
		return codes;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("valides")
		List<Object> valides;
		@JsonProperty("inactifs")
		List<Object> inactifs;
		@JsonProperty("inconnus")
		List<Object> inconnus;
	}
}
