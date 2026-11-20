package com.hr.workflow.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.config.CacheConfig;
import com.hr.workflow.config.WorkflowProperties;
import com.hr.workflow.web.WorkflowMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Client HTTP interne vers {@code svc-referentiel-rh} (D-W3-03, § 9.5.3, § 9.11).
 * Connexion 1 s, lecture 3 s, en-tête {@code X-Internal-Token}, sans {@code X-Societe-Id},
 * cloisonné par un sémaphore de 20 appels simultanés.
 */
@Component
public class WorkflowReferentielClient {

	private static final Logger log = LoggerFactory.getLogger(WorkflowReferentielClient.class);
	private static final String ENTETE_JETON = "X-Internal-Token";

	private final RestClient restClient;
	private final String jetonInterne;
	private final Semaphore semaphore;

	public WorkflowReferentielClient(
			WorkflowProperties properties,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		WorkflowProperties.Referentiel ref = properties.getReferentiel();
		HttpClient http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(ref.getTimeoutConnexionMs()))
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofMillis(ref.getTimeoutLectureMs()));
		this.restClient = RestClient.builder()
				.baseUrl(ref.getBaseUrl())
				.requestFactory(factory)
				.build();
		this.jetonInterne = jetonInterne;
		this.semaphore = new Semaphore(Math.max(1, ref.getMaxAppelsSimultanes()));
	}

	public ResolutionLotResponse resoudreLot(ResolutionLotRequest requete) {
		return executerAvecCloison(() -> {
			try {
				ResolutionLotResponse rep = restClient.post()
						.uri("/internal/referentiel/v1/workflow/resolutions")
						.header(ENTETE_JETON, jetonInterne)
						.body(requete)
						.retrieve()
						.body(ResolutionLotResponse.class);
				if (rep == null) {
					throw referentielIndisponible("Réponse vide du référentiel lors de la résolution.");
				}
				return rep;
			} catch (HttpClientErrorException.BadRequest ex) {
				throw WorkflowMetierException.badRequest("DEFINITION_INVALIDE",
						"Paramètres de résolution rejetés par le référentiel.");
			} catch (RestClientException ex) {
				throw referentielIndisponible(ex.getMessage());
			}
		});
	}

	@Cacheable(cacheNames = CacheConfig.CACHE_CALENDRIER_SOCIETE, key = "#du + ':' + #au")
	public CalendrierSocieteResponse calendrierSociete(LocalDate du, LocalDate au) {
		return executerAvecCloison(() -> {
			try {
				CalendrierSocieteResponse rep = restClient.get()
						.uri(uri -> uri.path("/internal/referentiel/v1/societe/calendrier")
								.queryParam("du", du)
								.queryParam("au", au)
								.build())
						.header(ENTETE_JETON, jetonInterne)
						.retrieve()
						.body(CalendrierSocieteResponse.class);
				if (rep == null) {
					throw referentielIndisponible("Réponse vide du référentiel pour le calendrier société.");
				}
				return rep;
			} catch (RestClientException ex) {
				throw referentielIndisponible(ex.getMessage());
			}
		});
	}

	@Cacheable(cacheNames = CacheConfig.CACHE_COLLABORATEUR_PAR_COMPTE, key = "#compteId")
	public Optional<CollaborateurResume> collaborateurParCompte(UUID compteId) {
		return executerAvecCloison(() -> {
			try {
				CollaborateurResume rep = restClient.get()
						.uri("/internal/referentiel/v1/collaborateurs/par-compte/{compteId}", compteId)
						.header(ENTETE_JETON, jetonInterne)
						.retrieve()
						.body(CollaborateurResume.class);
				return Optional.ofNullable(rep);
			} catch (HttpClientErrorException.NotFound ex) {
				return Optional.empty();
			} catch (RestClientException ex) {
				throw referentielIndisponible(ex.getMessage());
			}
		});
	}

	public Optional<CollaborateurResume> collaborateurParId(UUID collaborateurId) {
		return executerAvecCloison(() -> {
			try {
				CollaborateurResume rep = restClient.get()
						.uri("/internal/referentiel/v1/collaborateurs/{id}/resume", collaborateurId)
						.header(ENTETE_JETON, jetonInterne)
						.retrieve()
						.body(CollaborateurResume.class);
				return Optional.ofNullable(rep);
			} catch (HttpClientErrorException.NotFound ex) {
				return Optional.empty();
			} catch (RestClientException ex) {
				throw referentielIndisponible(ex.getMessage());
			}
		});
	}

	/** Marque une fonction comme utilisée lors de la publication d'une version (D-W3-05). */
	public void marquerFonctionUtilisee(String codeFonction) {
		executerAvecCloison(() -> {
			try {
				restClient.post()
						.uri("/internal/referentiel/v1/fonctions/{code}/utiliser", codeFonction)
						.header(ENTETE_JETON, jetonInterne)
						.retrieve()
						.toBodilessEntity();
				return null;
			} catch (HttpClientErrorException.NotFound ex) {
				// D-W3-05 : fonction inconnue non bloquante à la publication en W3 (gérée au runtime par D-W16)
				log.debug("Fonction {} absente lors du marquage d'utilisation (D-W3-05)", codeFonction);
				return null;
			} catch (RestClientException ex) {
				throw referentielIndisponible(ex.getMessage());
			}
		});
	}

	private <T> T executerAvecCloison(Supplier<T> action) {
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw referentielIndisponible("Appel référentiel interrompu");
		}
		try {
			return action.get();
		} finally {
			semaphore.release();
		}
	}

	private static WorkflowMetierException referentielIndisponible(String detail) {
		log.warn("Référentiel RH indisponible : {}", detail);
		return WorkflowMetierException.indisponible("SERVICE_INDISPONIBLE",
				"Le référentiel RH est momentanément indisponible. Réessayez plus tard.");
	}

	// ─── Contrats DTO alignés sur svc-referentiel-rh (§ 9.5.3) ───────────────

	public record ResolutionLotRequest(
			@JsonProperty("date") LocalDate date,
			@JsonProperty("demandeur_id") UUID demandeurId,
			@JsonProperty("beneficiaires") List<UUID> beneficiaires,
			@JsonProperty("exclure") List<UUID> exclure,
			@JsonProperty("regles") List<RegleResolutionRequest> regles
	) {
	}

	public record RegleResolutionRequest(
			@JsonProperty("cle") String cle,
			@JsonProperty("regle") String regle,
			@JsonProperty("params") Map<String, Object> params
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record IntervenantResolu(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("intitule") String intitule
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResultatResolution(
			@JsonProperty("candidats") List<IntervenantResolu> candidats,
			@JsonProperty("raison_vide") String raisonVide
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContexteDemandeur(
			@JsonProperty("profil_acces") String profilAcces,
			@JsonProperty("type_noeud") String typeNoeud,
			@JsonProperty("anciennete_mois") int ancienneteMois,
			@JsonProperty("est_manager") boolean estManager
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResolutionLotResponse(
			@JsonProperty("resultats") Map<String, ResultatResolution> resultats,
			@JsonProperty("contexte_demandeur") ContexteDemandeur contexteDemandeur
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CalendrierSocieteResponse(
			@JsonProperty("fuseau") String fuseau,
			@JsonProperty("jours_travailles") List<String> joursTravailles,
			@JsonProperty("heure_debut_journee") String heureDebutJournee,
			@JsonProperty("heure_fin_journee") String heureFinJournee,
			@JsonProperty("delegation_auto_conges") boolean delegationAutoConges,
			@JsonProperty("publication_quatre_yeux") boolean publicationQuatreYeux,
			@JsonProperty("jours_feries") List<LocalDate> joursFeries
	) {
		public static CalendrierSocieteResponse defaut() {
			return new CalendrierSocieteResponse(
					"Africa/Tunis",
					List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"),
					"08:00",
					"17:00",
					true,
					false,
					List.of()
			);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CollaborateurResume(
			@JsonProperty("id") UUID id,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("statut") String statut
	) {
	}
}
