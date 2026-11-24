package com.hr.presence.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.commun.societe.SocieteContexte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client HTTP synchrone de {@code svc-presence} vers {@code svc-workflow} (D-W6-02).
 * Aucun bean {@code @LoadBalanced} : {@link RestClient} dédié construit localement sur {@link JdkClientHttpRequestFactory}.
 * Aucun appel ne doit avoir lieu à l'intérieur d'une transaction {@code @Transactional}.
 */
@Component
public class PresenceWorkflowMoteurClient {

	private static final Logger log = LoggerFactory.getLogger(PresenceWorkflowMoteurClient.class);
	private static final String EN_TETE_JETON = "X-Internal-Token";
	private static final String EN_TETE_SOCIETE = "X-Societe-Id";
	private static final String EN_TETE_IDEMPOTENCE = "Idempotency-Key";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient restClient;
	private final RestClient reglePointageRestClient;
	private final String jetonInterne;

	public PresenceWorkflowMoteurClient(
			String baseUrl,
			String jetonInterne,
			int timeoutConnexionMs,
			int timeoutLectureMs) {
		this(baseUrl, jetonInterne, timeoutConnexionMs, timeoutLectureMs, 2000);
	}

	@Autowired
	public PresenceWorkflowMoteurClient(
			@Value("${presence.workflow.base-url:http://localhost:8087}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne,
			@Value("${presence.workflow.timeout-connexion-ms:1000}") int timeoutConnexionMs,
			@Value("${presence.workflow.timeout-lecture-ms:3000}") int timeoutLectureMs,
			@Value("${presence.workflow.notif-timeout-lecture-ms:2000}") int notifTimeoutLectureMs) {
		this.restClient = construireClient(baseUrl, timeoutConnexionMs, timeoutLectureMs);
		this.reglePointageRestClient = construireClient(baseUrl, timeoutConnexionMs, notifTimeoutLectureMs);
		this.jetonInterne = jetonInterne;
	}

	/** Constructeur de test permettant d'injecter un {@link RestClient} personnalisé. */
	public PresenceWorkflowMoteurClient(RestClient restClient, String jetonInterne) {
		this.restClient = restClient;
		this.reglePointageRestClient = restClient;
		this.jetonInterne = jetonInterne;
	}

	private static RestClient construireClient(String baseUrl, int timeoutConnexionMs, int timeoutLectureMs) {
		HttpClient http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(timeoutConnexionMs))
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofMillis(timeoutLectureMs));
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	/** Lecture directe (sans cache TTL) de la règle active {@code POINTAGE} (connect 1s, read 2s). */
	public Optional<RegleActivePointage> regleActivePointage() {
		try {
			var req = reglePointageRestClient.get()
					.uri("/internal/workflow/v1/pointage/regle-active")
					.header(EN_TETE_JETON, jetonInterne);
			Optional<UUID> societeId = SocieteContexte.couranteOuVide();
			if (societeId.isPresent() && !SocieteContexte.AUCUNE.equals(societeId.get())) {
				req = req.header(EN_TETE_SOCIETE, societeId.get().toString());
			}
			RegleActivePointage rep = req.retrieve().body(RegleActivePointage.class);
			return Optional.ofNullable(rep);
		} catch (RestClientException ex) {
			log.warn("Lecture règle active POINTAGE impossible : {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** Lecture du mode d'activation d'un type d'objet ({@code HERITE} ou {@code MOTEUR}). */
	public Optional<String> modeActivation(String objetType) {
		try {
			ActivationInterneResponse rep = restClient.get()
					.uri("/internal/workflow/v1/activations/{objetType}", objetType)
					.header(EN_TETE_JETON, jetonInterne)
					.retrieve()
					.body(ActivationInterneResponse.class);
			if (rep == null || rep.mode() == null || rep.mode().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(rep.mode().trim().toUpperCase());
		} catch (RestClientException ex) {
			log.warn("Lecture mode workflow impossible pour objetType={} : {}", objetType, ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** Démarre une instance de workflow (mono-société : sans en-tête X-Societe-Id). */
	public IssueDemarrage demarrer(String idempotencyKey, DemarrerInstanceRequest req) {
		try {
			ResponseEntity<DemarrerInstanceResponse> entity = restClient.post()
					.uri("/internal/workflow/v1/instances")
					.contentType(MediaType.APPLICATION_JSON)
					.header(EN_TETE_JETON, jetonInterne)
					.header(EN_TETE_IDEMPOTENCE, idempotencyKey)
					.body(req)
					.retrieve()
					.toEntity(DemarrerInstanceResponse.class);
			DemarrerInstanceResponse body = entity.getBody();
			if (body == null || body.instanceId() == null) {
				return new IssueDemarrage.Indisponible("Réponse de démarrage vide");
			}
			return new IssueDemarrage.Demarre(entity.getStatusCode().value(), body);
		} catch (HttpStatusCodeException ex) {
			int codeHttp = ex.getStatusCode().value();
			String codeErreur = extraireCodeErreur(ex.getResponseBodyAsString());
			if (codeHttp == 409 && "INSTANCE_EXISTANTE".equals(codeErreur)) {
				return new IssueDemarrage.InstanceExistante();
			}
			if (codeHttp == 409 && "MODE_HERITE".equals(codeErreur)) {
				return new IssueDemarrage.ModeHerite();
			}
			if (codeHttp == 422 && "WORKFLOW_ABSENT".equals(codeErreur)) {
				return new IssueDemarrage.WorkflowAbsent();
			}
			log.warn("Échec démarrage workflow objetType={} objetId={} http={} code={}",
					req.objetType(), req.objetId(), codeHttp, codeErreur);
			return new IssueDemarrage.Indisponible("HTTP " + codeHttp + " " + codeErreur);
		} catch (RestClientException ex) {
			log.warn("Moteur workflow injoignable au démarrage objetType={} objetId={} : {}",
					req.objetType(), req.objetId(), ex.getClass().getSimpleName());
			return new IssueDemarrage.Indisponible(ex.getClass().getSimpleName());
		}
	}

	/** Relit une instance existante par objet (cas 409 {@code INSTANCE_EXISTANTE}). */
	public Optional<SuiviObjetInterne> obtenirParObjet(String objetType, UUID objetId) {
		try {
			SuiviObjetInterne rep = restClient.get()
					.uri("/internal/workflow/v1/objets/{objetType}/{objetId}", objetType, objetId)
					.header(EN_TETE_JETON, jetonInterne)
					.retrieve()
					.body(SuiviObjetInterne.class);
			return Optional.ofNullable(rep);
		} catch (RestClientException ex) {
			log.warn("Relecture instance workflow impossible objetType={} objetId={} : {}",
					objetType, objetId, ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** Annule une instance workflow (ex. annulation demandeur M09_HEURES_SUP ou instance orpheline). */
	public IssueAnnulation annuler(UUID instanceId, String motif, ActeurInterne acteur) {
		try {
			restClient.post()
					.uri("/internal/workflow/v1/instances/{id}/annuler", instanceId)
					.contentType(MediaType.APPLICATION_JSON)
					.header(EN_TETE_JETON, jetonInterne)
					.body(new AnnulerInstanceRequest(motif, acteur))
					.retrieve()
					.toBodilessEntity();
			return IssueAnnulation.ANNULEE;
		} catch (HttpStatusCodeException ex) {
			int code = ex.getStatusCode().value();
			String err = extraireCodeErreur(ex.getResponseBodyAsString());
			if (code == 409) {
				return IssueAnnulation.INSTANCE_TERMINEE;
			}
			log.warn("Échec annulation workflow instanceId={} http={} code={}", instanceId, code, err);
			return IssueAnnulation.INDISPONIBLE;
		} catch (RestClientException ex) {
			log.warn("Moteur workflow injoignable pour annulation instanceId={} : {}",
					instanceId, ex.getClass().getSimpleName());
			return IssueAnnulation.INDISPONIBLE;
		}
	}

	private static String extraireCodeErreur(String corps) {
		if (corps == null || corps.isBlank()) {
			return "";
		}
		try {
			JsonNode n = JSON.readTree(corps);
			if (n.hasNonNull("code")) {
				return n.get("code").asString("");
			}
			if (n.hasNonNull("erreur")) {
				return n.get("erreur").asString("");
			}
			return "";
		} catch (Exception e) {
			return "";
		}
	}

	public sealed interface IssueDemarrage {
		record Demarre(int statutHttp, DemarrerInstanceResponse reponse) implements IssueDemarrage {
		}
		record InstanceExistante() implements IssueDemarrage {
		}
		record ModeHerite() implements IssueDemarrage {
		}
		record WorkflowAbsent() implements IssueDemarrage {
		}
		record Indisponible(String detail) implements IssueDemarrage {
		}
	}

	public enum IssueAnnulation {
		ANNULEE,
		INSTANCE_TERMINEE,
		INDISPONIBLE
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ActivationInterneResponse(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("mode") String mode
	) {
	}

	public record DemarrerInstanceRequest(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("sous_type") String sousType,
			@JsonProperty("demandeur_compte_id") UUID demandeurCompteId,
			@JsonProperty("demandeur_collaborateur_id") UUID demandeurCollaborateurId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("beneficiaires") List<UUID> beneficiaires,
			@JsonProperty("variables") Map<String, Object> variables
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DemarrerInstanceResponse(
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("statut") String statut,
			@JsonProperty("effet_immediat") String effetImmediat,
			@JsonProperty("etape_courante") EtapeCouranteDto etapeCourante
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SuiviObjetInterne(
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("statut") String statut,
			@JsonProperty("etape_courante") EtapeCouranteDto etapeCourante
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EtapeCouranteDto(
			@JsonProperty("etape_code") String etapeCode,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("echeance_le") Instant echeanceLe
	) {
	}

	public record ActeurInterne(
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("nom") String nom
	) {
	}

	public record AnnulerInstanceRequest(
			@JsonProperty("motif") String motif,
			@JsonProperty("acteur") ActeurInterne acteur
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RegleActivePointage(
			@JsonProperty("actif_effectif") boolean actifEffectif,
			@JsonProperty("mode") String mode,
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("version_numero") Integer versionNumero,
			@JsonProperty("notifier_ro") boolean notifierRo,
			@JsonProperty("portee_sites") String porteeSites,
			@JsonProperty("site_ids") List<UUID> siteIds,
			@JsonProperty("types_pointage") List<String> typesPointage,
			@JsonProperty("canal") String canal
	) {
	}
}
