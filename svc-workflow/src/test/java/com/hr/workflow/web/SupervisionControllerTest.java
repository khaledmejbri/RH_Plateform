package com.hr.workflow.web;

import com.hr.workflow.WorkflowApplication;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.client.WorkflowReferentielClient.ResolutionLotResponse;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.OrigineDelegation;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationAdminRequest;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationMoiRequest;
import com.hr.workflow.dto.DelegationDtos.DelegationResponse;
import com.hr.workflow.dto.DelegationDtos.ReaffecterTacheRequest;
import com.hr.workflow.dto.SupervisionDtos.IndicateurEtapeDto;
import com.hr.workflow.dto.SupervisionDtos.IndicateursWorkflowResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceDetailAdminResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceResumeResponse;
import com.hr.workflow.dto.SupervisionDtos.ReaffecterTacheResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.entity.WfDelegation;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.DecisionService;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.repository.WfDelegationRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.service.WorkflowUtilisateurService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration PostgreSQL ({@code workflow_db_test}) des contrôleurs de supervision,
 * réaffectation, indicateurs et délégations (CA-15.1 à CA-15.3, CA-11.6, Q5, D-W4-05 à D-W4-09).
 */
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SupervisionControllerTest {

	private static final String DB_URL = "jdbc:postgresql://localhost:5444/workflow_db_test";
	private static final String DB_USER = "postgres";

	private static HttpServer fakeServer;
	private static int fakePort;

	@Autowired private MockMvc mockMvc;
	@Autowired private JsonMapper jsonMapper;
	@Autowired private WfModeleRepository modeleRepository;
	@Autowired private WfModeleVersionRepository versionRepository;
	@Autowired private WfInstanceRepository instanceRepository;
	@Autowired private WfEtapeExecutionRepository etapeExecutionRepository;
	@Autowired private WfTacheRepository tacheRepository;
	@Autowired private WfTacheCandidatRepository candidatRepository;
	@Autowired private WfDelegationRepository delegationRepository;
	@Autowired private WorkflowUtilisateurService utilisateurService;
	@Autowired private DecisionService decisionService;

	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private WorkflowReferentielClient referentielClient;

	private static String dbPassword() {
		String env = System.getenv("TEST_DB_PASSWORD");
		return (env != null && !env.isBlank()) ? env : "password";
	}

	@BeforeAll
	static void verifierBaseEtDemarrerServeurFactice() throws Exception {
		boolean dbOk = false;
		try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, dbPassword())) {
			dbOk = c.isValid(2);
		} catch (Exception ignored) {
		}
		Assumptions.assumeTrue(dbOk, "Base PostgreSQL workflow_db_test absente ou inaccessible sur localhost:5444");

		fakeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakePort = fakeServer.getAddress().getPort();
		fakeServer.createContext("/internal/referentiel/v1/workflow/effets", exchange -> {
			byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
		fakeServer.start();
	}

	@AfterAll
	static void arreterServeurFactice() {
		if (fakeServer != null) {
			fakeServer.stop(0);
		}
	}

	@DynamicPropertySource
	static void configurerProprietes(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> DB_URL);
		registry.add("spring.datasource.username", () -> DB_USER);
		registry.add("spring.datasource.password", SupervisionControllerTest::dbPassword);
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", () -> "");
		registry.add("spring.task.scheduling.enabled", () -> "false");
		registry.add("workflow.referentiel.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-referentiel-rh.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-presence.base-url", () -> "http://127.0.0.1:" + fakePort);
	}

	@BeforeEach
	void configurerReferentielParDefaut() {
		lenient().when(referentielClient.calendrierSociete(any(), any()))
				.thenReturn(CalendrierSocieteResponse.defaut());
		lenient().when(referentielClient.resoudreLot(any()))
				.thenReturn(new ResolutionLotResponse(Map.of(),
						new ContexteDemandeur("COLLABORATEUR", "SERVICE", 12, false)));
	}

	@Test
	void ca15_1_listerInstances_filtresEtPaginationEnBasePostgreSql() throws Exception {
		WfModeleVersion versionConge = creerVersion("M01_CONGE", "CONGE", "VALID_RESP", "Validation Responsable");
		UUID demandeurA = UUID.randomUUID();
		UUID demandeurB = UUID.randomUUID();
		UUID intervenantX = UUID.randomUUID();
		Instant now = Instant.now();

		// Instance 1 : CONGE, EN_COURS, VALID_RESP, en retard, intervenantX actif
		WfInstance inst1 = creerInstance(versionConge, "CONGE", StatutInstance.EN_COURS, "VALID_RESP",
				demandeurA, "Demandeur A", now.minus(2, ChronoUnit.DAYS), null);
		WfEtapeExecution ee1 = creerEtapeActive(inst1, "VALID_RESP", now.minus(2, ChronoUnit.DAYS));
		WfTache t1 = creerTache(inst1, ee1, "VALID_RESP", "Validation Resp", now.minus(2, ChronoUnit.HOURS));
		creerCandidat(t1, intervenantX, UUID.randomUUID(), "Valideur X", OrigineCandidat.TITULAIRE, null, now);

		// Instance 2 : ORDRE_MISSION, BLOQUEE, VALID_RH, non en retard, demarreLe -10j, sans intervenantX
		WfInstance inst2 = creerInstance(versionConge, "ORDRE_MISSION", StatutInstance.BLOQUEE, "VALID_RH",
				demandeurA, "Demandeur A", now.minus(10, ChronoUnit.DAYS), null);
		WfEtapeExecution ee2 = creerEtapeActive(inst2, "VALID_RH", now.minus(10, ChronoUnit.DAYS));
		creerTache(inst2, ee2, "VALID_RH", "Validation RH", now.plus(10, ChronoUnit.HOURS));

		// Instance 3 : CONGE, APPROUVEE, demandeurB
		WfInstance inst3 = creerInstance(versionConge, "CONGE", StatutInstance.APPROUVEE, "VALID_RESP",
				demandeurB, "Demandeur B", now.minus(1, ChronoUnit.DAYS), now.minus(12, ChronoUnit.HOURS));

		JwtRequestPostProcessor rhJwt = jeton(UUID.randomUUID(), "RH Superviseur", "ROLE_RH");

		// Filtre objet_type + demandeur_id -> inst1 uniquement
		PageResponse<InstanceResumeResponse> parObjet = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurA.toString())
						.queryParam("objet_type", "CONGE")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(parObjet.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst1.getId());
		assertThat(parObjet.elements().get(0).enRetard()).isTrue();

		// Filtre statut=BLOQUEE & etape=VALID_RH -> inst2 uniquement
		PageResponse<InstanceResumeResponse> parStatutEtape = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurA.toString())
						.queryParam("statut", "BLOQUEE")
						.queryParam("etape", "VALID_RH")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(parStatutEtape.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst2.getId());
		assertThat(parStatutEtape.elements().get(0).enRetard()).isFalse();

		// Filtre en_retard=true / false
		PageResponse<InstanceResumeResponse> enRetardOui = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurA.toString())
						.queryParam("en_retard", "true")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(enRetardOui.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst1.getId());

		PageResponse<InstanceResumeResponse> enRetardNon = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurA.toString())
						.queryParam("en_retard", "false")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(enRetardNon.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst2.getId());

		// Filtre intervenant_id
		PageResponse<InstanceResumeResponse> parIntervenant = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("intervenant_id", intervenantX.toString())
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(parIntervenant.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst1.getId());

		// Filtre demandeur_id = demandeurB + du/au
		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		PageResponse<InstanceResumeResponse> parDates = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurA.toString())
						.queryParam("du", auj.minusDays(4).toString())
						.queryParam("au", auj.toString())
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(parDates.elements()).extracting(InstanceResumeResponse::id).containsExactly(inst1.getId());

		// Pagination stricte en base (22 instances -> page 0 taille 20 puis page 1 -> 2 éléments)
		UUID demandeurPag = UUID.randomUUID();
		for (int i = 0; i < 22; i++) {
			creerInstance(versionConge, "CONGE", StatutInstance.EN_COURS, "VALID_RESP",
					demandeurPag, "Pagination User", now.minus(i, ChronoUnit.MINUTES), null);
		}
		PageResponse<InstanceResumeResponse> page0 = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurPag.toString())
						.queryParam("page", "0")
						.queryParam("taille", "20")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(page0.elements()).hasSize(20);
		assertThat(page0.total()).isEqualTo(22);

		PageResponse<InstanceResumeResponse> page1 = lirePageInstances(mockMvc.perform(
				get("/api/rh/v1/admin/workflow/instances")
						.queryParam("demandeur_id", demandeurPag.toString())
						.queryParam("page", "1")
						.queryParam("taille", "20")
						.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(page1.elements()).hasSize(2);
		assertThat(inst3.getStatut()).isEqualTo(StatutInstance.APPROUVEE);
	}

	@Test
	void ca15_2_detailInstanceEtIndicateursAgregesEnBase() throws Exception {
		JwtRequestPostProcessor rhJwt = jeton(UUID.randomUUID(), "RH Indicateurs", "ROLE_RH");

		// 1. Snapshot baseline des indicateurs globaux (fenêtre 30j par défaut)
		MvcResult resAvant = mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs").with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		IndicateursWorkflowResponse avant = jsonMapper.readValue(
				resAvant.getResponse().getContentAsString(), IndicateursWorkflowResponse.class);

		String stepUnique = "VALID_IND_" + UUID.randomUUID().toString().substring(0, 8);
		WfModeleVersion versionInd = creerVersion("M01_CONGE", "CONGE", stepUnique, "Étape Indicateur");
		Instant now = Instant.now();

		// +1 EN_COURS à l'heure, +1 EN_COURS en retard, +1 BLOQUEE
		WfInstance instHeure = creerInstance(versionInd, "CONGE", StatutInstance.EN_COURS, stepUnique,
				UUID.randomUUID(), "Demandeur 1", now, null);
		WfEtapeExecution eeHeure = creerEtapeActive(instHeure, stepUnique, now);
		creerTache(instHeure, eeHeure, stepUnique, "Étape Indicateur", now.plus(5, ChronoUnit.HOURS));

		WfInstance instRetard = creerInstance(versionInd, "CONGE", StatutInstance.EN_COURS, stepUnique,
				UUID.randomUUID(), "Demandeur 2", now, null);
		WfEtapeExecution eeRetard = creerEtapeActive(instRetard, stepUnique, now.minus(10, ChronoUnit.HOURS));
		WfTache tRetard = creerTache(instRetard, eeRetard, stepUnique, "Étape Indicateur", now.minus(1, ChronoUnit.HOURS));
		UUID candCollab = UUID.randomUUID();
		creerCandidat(tRetard, candCollab, UUID.randomUUID(), "Amel Chef", OrigineCandidat.DELEGUE, "Titulaire Init", now);

		creerInstance(versionInd, "CONGE", StatutInstance.BLOQUEE, stepUnique,
				UUID.randomUUID(), "Demandeur 3", now, null);

		// Une tâche décidée en ~4h pour tester par_etape agrégé en SQL
		WfInstance instTerminee = creerInstance(versionInd, "CONGE", StatutInstance.EN_COURS, stepUnique,
				UUID.randomUUID(), "Demandeur 4", now.minus(5, ChronoUnit.HOURS), null);
		WfEtapeExecution eeDone = creerEtapeActive(instTerminee, stepUnique, now.minus(4, ChronoUnit.HOURS));
		WfTache tDone = creerTache(instTerminee, eeDone, stepUnique, "Étape Indicateur", now.plus(4, ChronoUnit.HOURS));
		UUID decideurCollab = UUID.randomUUID();
		UUID decideurCompte = UUID.randomUUID();
		creerCandidat(tDone, decideurCollab, decideurCompte, "Sami Décideur", OrigineCandidat.TITULAIRE, null, now.minus(4, ChronoUnit.HOURS));

		decisionService.decider(tDone.getId(),
				new DecisionRequest("APPROUVER", "OK", tDone.getVersion(), "ind-" + UUID.randomUUID()),
				decideurCompte, "Sami Décideur", false);

		// Détail complet d'instance (tâches, candidats nominatifs avec pour_nom, étapes, variables)
		MvcResult resDetail = mockMvc.perform(get("/api/rh/v1/admin/workflow/instances/{id}", instRetard.getId()).with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		InstanceDetailAdminResponse detail = jsonMapper.readValue(
				resDetail.getResponse().getContentAsString(), InstanceDetailAdminResponse.class);
		assertThat(detail.id()).isEqualTo(instRetard.getId());
		assertThat(detail.taches()).hasSize(1);
		assertThat(detail.taches().get(0).candidats()).hasSize(1);
		assertThat(detail.taches().get(0).candidats().get(0).pourNom()).isEqualTo("Titulaire Init");

		// 404 sur instance inconnue
		mockMvc.perform(get("/api/rh/v1/admin/workflow/instances/{id}", UUID.randomUUID()).with(rhJwt))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INSTANCE_INTROUVABLE"));

		// Indicateurs fenêtre 30j par défaut : deltas exacts
		MvcResult resApres = mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs").with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		IndicateursWorkflowResponse apres = jsonMapper.readValue(
				resApres.getResponse().getContentAsString(), IndicateursWorkflowResponse.class);
		assertThat(apres.enCours() - avant.enCours()).isEqualTo(2);
		assertThat(apres.enRetard() - avant.enRetard()).isEqualTo(1);
		assertThat(apres.bloquees() - avant.bloquees()).isEqualTo(1);

		// Indicateurs avec du/au explicite : délai moyen ~4h sur stepUnique
		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		MvcResult resFenetre = mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs")
				.queryParam("du", auj.minusDays(1).toString())
				.queryParam("au", auj.plusDays(1).toString())
				.with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		IndicateursWorkflowResponse fenetre = jsonMapper.readValue(
				resFenetre.getResponse().getContentAsString(), IndicateursWorkflowResponse.class);
		IndicateurEtapeDto indStep = fenetre.parEtape().stream()
				.filter(e -> stepUnique.equals(e.etapeCode()))
				.findFirst()
				.orElseThrow();
		assertThat(indStep.nb()).isEqualTo(1);
		assertThat(indStep.delaiMoyenHeures()).isBetween(new BigDecimal("3.90"), new BigDecimal("4.10"));
	}

	@Test
	void ca15_3_droitsDirectionLectureSeule_etRbacReaffectationEtSupervision() throws Exception {
		WfModeleVersion versionConge = creerVersion("M01_CONGE", "CONGE", "VALID_RH", "Validation RH");
		Instant now = Instant.now();
		WfInstance instBloquee = creerInstance(versionConge, "CONGE", StatutInstance.BLOQUEE, "VALID_RH",
				UUID.randomUUID(), "Demandeur Bloqué", now, null);
		WfEtapeExecution ee = creerEtapeActive(instBloquee, "VALID_RH", now);
		WfTache tBloquee = creerTache(instBloquee, ee, "VALID_RH", "Validation RH", null);

		UUID nouveauCollab = UUID.randomUUID();
		UUID nouveauCompte = UUID.randomUUID();
		UUID rhCompte = UUID.randomUUID();
		UUID rhCollab = UUID.randomUUID();
		lenient().when(referentielClient.collaborateurParCompte(rhCompte))
				.thenReturn(Optional.of(new CollaborateurResume(rhCollab, rhCompte, "Nadia RH", "M-RH", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(nouveauCollab))
				.thenReturn(Optional.of(new CollaborateurResume(nouveauCollab, nouveauCompte, "DRH Secours", "M-DRH", "ACTIF")));

		JwtRequestPostProcessor directionJwt = jeton(UUID.randomUUID(), "Directrice", "ROLE_DIRECTION");
		JwtRequestPostProcessor userJwt = jeton(UUID.randomUUID(), "Collaborateur", "ROLE_USER");
		JwtRequestPostProcessor rhJwt = jeton(rhCompte, "Nadia RH", "ROLE_RH");

		// DIRECTION : lecture seule OK (200), écriture refusée (403)
		mockMvc.perform(get("/api/rh/v1/admin/workflow/instances").with(directionJwt)).andExpect(status().isOk());
		mockMvc.perform(get("/api/rh/v1/admin/workflow/instances/{id}", instBloquee.getId()).with(directionJwt)).andExpect(status().isOk());
		mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs").with(directionJwt)).andExpect(status().isOk());
		mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations").with(directionJwt)).andExpect(status().isOk());

		String bodyReaff = jsonMapper.writeValueAsString(
				new ReaffecterTacheRequest(List.of(nouveauCollab), "Déblocage réaffecté", tBloquee.getVersion()));
		mockMvc.perform(post("/api/rh/v1/admin/workflow/taches/{id}/reaffecter", tBloquee.getId())
				.contentType(MediaType.APPLICATION_JSON).content(bodyReaff).with(directionJwt))
				.andExpect(status().isForbidden());

		String bodyDelAdmin = jsonMapper.writeValueAsString(
				new CreerDelegationAdminRequest(rhCollab, nouveauCollab, LocalDate.now(), LocalDate.now().plusDays(2), null, null));
		mockMvc.perform(post("/api/rh/v1/admin/workflow/delegations")
				.contentType(MediaType.APPLICATION_JSON).content(bodyDelAdmin).with(directionJwt))
				.andExpect(status().isForbidden());

		// USER : accès refusé sur toute la supervision et réaffectation (403)
		mockMvc.perform(get("/api/rh/v1/admin/workflow/instances").with(userJwt)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs").with(userJwt)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/rh/v1/admin/workflow/taches/{id}/reaffecter", tBloquee.getId())
				.contentType(MediaType.APPLICATION_JSON).content(bodyReaff).with(userJwt))
				.andExpect(status().isForbidden());

		// RH : réaffectation réussit (200) et débloque l'instance en PostgreSQL
		MvcResult resReaff = mockMvc.perform(post("/api/rh/v1/admin/workflow/taches/{id}/reaffecter", tBloquee.getId())
				.contentType(MediaType.APPLICATION_JSON).content(bodyReaff).with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		ReaffecterTacheResponse repReaff = jsonMapper.readValue(
				resReaff.getResponse().getContentAsString(), ReaffecterTacheResponse.class);
		assertThat(repReaff.statutInstance()).isEqualTo(StatutInstance.EN_COURS);
		assertThat(repReaff.candidats()).anyMatch(c -> c.collaborateurId().equals(nouveauCollab) && c.retireLe() == null);
	}

	@Test
	void ca11_6_et_q5_postgresql_delegationChevauchementDisjoint409_pourNomMesTachesEtHistorique() throws Exception {
		WfModeleVersion versionConge = creerVersion("M01_CONGE", "CONGE", "VALID_RESP", "Validation Responsable");
		UUID titulaireCollab = UUID.randomUUID();
		UUID titulaireCompte = UUID.randomUUID();
		UUID delegueCollab = UUID.randomUUID();
		UUID delegueCompte = UUID.randomUUID();
		UUID tiersDelegueCollab = UUID.randomUUID();
		UUID tiersDelegueCompte = UUID.randomUUID();

		lenient().when(referentielClient.collaborateurParCompte(titulaireCompte))
				.thenReturn(Optional.of(new CollaborateurResume(titulaireCollab, titulaireCompte, "Titulaire Chef", "M-T", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(delegueCollab))
				.thenReturn(Optional.of(new CollaborateurResume(delegueCollab, delegueCompte, "Sami Délégué", "M-D", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(tiersDelegueCollab))
				.thenReturn(Optional.of(new CollaborateurResume(tiersDelegueCollab, tiersDelegueCompte, "Autre Délégué", "M-D2", "ACTIF")));

		Instant now = Instant.now();
		WfInstance inst = creerInstance(versionConge, "CONGE", StatutInstance.EN_COURS, "VALID_RESP",
				UUID.randomUUID(), "Employé Demandeur", now, null);
		WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RESP", now);
		WfTache t = creerTache(inst, ee, "VALID_RESP", "Validation Responsable", now.plus(24, ChronoUnit.HOURS));
		creerCandidat(t, titulaireCollab, titulaireCompte, "Titulaire Chef", OrigineCandidat.TITULAIRE, null, now);

		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		JwtRequestPostProcessor titulaireJwt = jeton(titulaireCompte, "Titulaire Chef", "ROLE_USER");

		// 1. Création délégation manuelle sur CONGE -> 201 et ajout immédiat du candidat DELEGUE
		MvcResult resDel = mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(new CreerDelegationMoiRequest(
						delegueCollab, auj, auj.plusDays(4), List.of("CONGE"), "Absence")))
				.with(titulaireJwt))
				.andExpect(status().is2xxSuccessful())
				.andReturn();
		DelegationResponse delCreee = jsonMapper.readValue(resDel.getResponse().getContentAsString(), DelegationResponse.class);
		assertThat(delCreee.active()).isTrue();

		// 2. Q5 : même délégant, période chevauchante, objet_types disjoints ["ORDRE_MISSION"] -> 409 DELEGATION_CHEVAUCHEMENT en base
		mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(new CreerDelegationMoiRequest(
						tiersDelegueCollab, auj.plusDays(1), auj.plusDays(5), List.of("ORDRE_MISSION"), "Chevauchement")))
				.with(titulaireJwt))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DELEGATION_CHEVAUCHEMENT"));

		// 3. CA-11.6 : le délégué voit "pour_nom = Titulaire Chef" dans mesTaches
		var mesTaches = utilisateurService.mesTaches(delegueCompte, "A_TRAITER", 0, 20);
		assertThat(mesTaches.elements()).anySatisfy(item -> {
			assertThat(item.tacheId()).isEqualTo(t.getId());
			assertThat(item.origine()).isEqualTo(OrigineCandidat.DELEGUE);
			assertThat(item.pourNom()).isEqualTo("Titulaire Chef");
		});

		// 4. CA-11.6 : le délégué décide -> historique et détail admin montrent acteur_nom + pour_nom
		WfTache tRelue = tacheRepository.findById(t.getId()).orElseThrow();
		var resDec = decisionService.decider(t.getId(),
				new DecisionRequest("APPROUVER", "Validé par délégation", tRelue.getVersion(), "del-" + UUID.randomUUID()),
				delegueCompte, "Sami Délégué", false);
		assertThat(resDec.httpStatus()).isEqualTo(200);

		MvcResult resDetail = mockMvc.perform(get("/api/rh/v1/admin/workflow/instances/{id}", inst.getId())
				.with(jeton(UUID.randomUUID(), "RH Lecteur", "ROLE_RH")))
				.andExpect(status().isOk())
				.andReturn();
		InstanceDetailAdminResponse detail = jsonMapper.readValue(
				resDetail.getResponse().getContentAsString(), InstanceDetailAdminResponse.class);
		assertThat(detail.taches().get(0).decideurNom()).isEqualTo("Sami Délégué");
		assertThat(detail.taches().get(0).pourNom()).isEqualTo("Titulaire Chef");
		assertThat(detail.historique()).anySatisfy(h -> {
			assertThat(h.tacheId()).isEqualTo(t.getId());
			assertThat(h.acteurNom()).isEqualTo("Sami Délégué");
			assertThat(h.pourNom()).isEqualTo("Titulaire Chef");
		});

		// 5. Annulation de la délégation -> 204
		mockMvc.perform(delete("/api/rh/v1/workflow/delegations/moi/{id}", delCreee.id()).with(titulaireJwt))
				.andExpect(status().isNoContent());
	}

	@Test
	void c2_postgresql_reaffectationEnCours_incrementeVersion_etSecondeAvecAncienneVersionRecoit409() throws Exception {
		WfModeleVersion versionConge = creerVersion("M01_CONGE", "CONGE", "VALID_RH", "Validation RH");
		Instant now = Instant.now();
		WfInstance instEnCours = creerInstance(versionConge, "CONGE", StatutInstance.EN_COURS, "VALID_RH",
				UUID.randomUUID(), "Demandeur Actif", now, null);
		WfEtapeExecution ee = creerEtapeActive(instEnCours, "VALID_RH", now);
		WfTache t = creerTache(instEnCours, ee, "VALID_RH", "Validation RH", now.plus(24, ChronoUnit.HOURS));
		creerCandidat(t, UUID.randomUUID(), UUID.randomUUID(), "RH Init", OrigineCandidat.TITULAIRE, null, now);
		Long versionInit = t.getVersion();

		UUID rhCompte = UUID.randomUUID();
		UUID rhCollab = UUID.randomUUID();
		UUID drh1Collab = UUID.randomUUID();
		UUID drh2Collab = UUID.randomUUID();
		lenient().when(referentielClient.collaborateurParCompte(rhCompte))
				.thenReturn(Optional.of(new CollaborateurResume(rhCollab, rhCompte, "Nadia RH", "M-RH", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(drh1Collab))
				.thenReturn(Optional.of(new CollaborateurResume(drh1Collab, UUID.randomUUID(), "DRH 1", "M-1", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(drh2Collab))
				.thenReturn(Optional.of(new CollaborateurResume(drh2Collab, UUID.randomUUID(), "DRH 2", "M-2", "ACTIF")));

		JwtRequestPostProcessor rhJwt = jeton(rhCompte, "Nadia RH", "ROLE_RH");

		// 1. Première réaffectation sur tâche EN_COURS -> 200 et @Version incrémentée
		String body1 = jsonMapper.writeValueAsString(
				new ReaffecterTacheRequest(List.of(drh1Collab), "Réaffectation charge RH", versionInit));
		MvcResult res1 = mockMvc.perform(post("/api/rh/v1/admin/workflow/taches/{id}/reaffecter", t.getId())
				.contentType(MediaType.APPLICATION_JSON).content(body1).with(rhJwt))
				.andExpect(status().isOk())
				.andReturn();
		ReaffecterTacheResponse rep1 = jsonMapper.readValue(
				res1.getResponse().getContentAsString(), ReaffecterTacheResponse.class);
		assertThat(rep1.statutInstance()).isEqualTo(StatutInstance.EN_COURS);
		assertThat(rep1.version()).isEqualTo(versionInit + 1);

		WfTache tRelue = tacheRepository.findById(t.getId()).orElseThrow();
		assertThat(tRelue.getVersion()).isEqualTo(versionInit + 1);
		assertThat(tRelue.getReaffecteeLe()).isNotNull();

		// 2. Seconde réaffectation avec l'ancienne version -> 409 MODIFIE_ENTRE_TEMPS
		String body2 = jsonMapper.writeValueAsString(
				new ReaffecterTacheRequest(List.of(drh2Collab), "Écrasement concurrent", versionInit));
		mockMvc.perform(post("/api/rh/v1/admin/workflow/taches/{id}/reaffecter", t.getId())
				.contentType(MediaType.APPLICATION_JSON).content(body2).with(rhJwt))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MODIFIE_ENTRE_TEMPS"));
	}

	@Test
	void c6_postgresql_concurrenceCreationDelegationMemeDelegant_unSuccesUnConflit409() throws Exception {
		UUID delegantCollab = UUID.randomUUID();
		UUID delegantCompte = UUID.randomUUID();
		UUID delegue1Collab = UUID.randomUUID();
		UUID delegue2Collab = UUID.randomUUID();
		lenient().when(referentielClient.collaborateurParCompte(delegantCompte))
				.thenReturn(Optional.of(new CollaborateurResume(delegantCollab, delegantCompte, "Chef Concurrent", "M-C", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(delegue1Collab))
				.thenReturn(Optional.of(new CollaborateurResume(delegue1Collab, UUID.randomUUID(), "Délégué 1", "M-D1", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(delegue2Collab))
				.thenReturn(Optional.of(new CollaborateurResume(delegue2Collab, UUID.randomUUID(), "Délégué 2", "M-D2", "ACTIF")));

		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		JwtRequestPostProcessor delegantJwt = jeton(delegantCompte, "Chef Concurrent", "ROLE_USER");
		String body1 = jsonMapper.writeValueAsString(
				new CreerDelegationMoiRequest(delegue1Collab, auj, auj.plusDays(4), List.of("CONGE"), "Concurrent 1"));
		String body2 = jsonMapper.writeValueAsString(
				new CreerDelegationMoiRequest(delegue2Collab, auj.plusDays(1), auj.plusDays(5), List.of("ORDRE_MISSION"), "Concurrent 2"));

		CountDownLatch pret = new CountDownLatch(2);
		CountDownLatch depart = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Callable<Integer> tache1 = () -> {
			pret.countDown();
			depart.await();
			return mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
					.contentType(MediaType.APPLICATION_JSON).content(body1).with(delegantJwt))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> tache2 = () -> {
			pret.countDown();
			depart.await();
			return mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
					.contentType(MediaType.APPLICATION_JSON).content(body2).with(delegantJwt))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> f1 = pool.submit(tache1);
		Future<Integer> f2 = pool.submit(tache2);
		pret.await();
		depart.countDown();

		List<Integer> statuts = List.of(f1.get(), f2.get());
		pool.shutdown();
		assertThat(statuts).containsExactlyInAnyOrder(201, 409);
	}

	@Test
	void c8_postgresql_indicateursBorneHauteStricte() throws Exception {
		String stepUnique = "IND_BORNE_" + UUID.randomUUID().toString().substring(0, 8);
		WfModeleVersion version = creerVersion("M01_CONGE", "CONGE", stepUnique, "Étape Borne");
		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		Instant finExclue = auj.plusDays(1).atStartOfDay(PlanificationEcheanceService.FUSEAU_DEFAUT).toInstant();

		// 1. Décidée à 23:59:59.999999 (microsecondes avant minuit le jour `au`) -> incluse
		WfInstance instIn = creerInstance(version, "CONGE", StatutInstance.APPROUVEE, null,
				UUID.randomUUID(), "Dem In", finExclue.minus(3, ChronoUnit.HOURS), finExclue.minusNanos(1000));
		WfEtapeExecution eeIn = creerEtapeActive(instIn, stepUnique, finExclue.minus(2, ChronoUnit.HOURS));
		WfTache tIn = creerTache(instIn, eeIn, stepUnique, "Étape Borne", finExclue.plus(4, ChronoUnit.HOURS));
		tIn.setStatut(StatutTache.TRAITEE);
		tIn.setDecision(DecisionTache.APPROUVEE);
		tIn.setDecideLe(finExclue.minusNanos(1000));
		tacheRepository.saveAndFlush(tIn);

		// 2. Décidée exactement à 00:00:00 le jour `au + 1` (finExclue) -> exclue
		WfInstance instOut = creerInstance(version, "CONGE", StatutInstance.APPROUVEE, null,
				UUID.randomUUID(), "Dem Out", finExclue.minus(12, ChronoUnit.HOURS), finExclue);
		WfEtapeExecution eeOut = creerEtapeActive(instOut, stepUnique, finExclue.minus(10, ChronoUnit.HOURS));
		WfTache tOut = creerTache(instOut, eeOut, stepUnique, "Étape Borne", finExclue.plus(4, ChronoUnit.HOURS));
		tOut.setStatut(StatutTache.TRAITEE);
		tOut.setDecision(DecisionTache.APPROUVEE);
		tOut.setDecideLe(finExclue);
		tacheRepository.saveAndFlush(tOut);

		MvcResult res = mockMvc.perform(get("/api/rh/v1/admin/workflow/indicateurs")
				.queryParam("du", auj.toString())
				.queryParam("au", auj.toString())
				.with(jeton(UUID.randomUUID(), "RH Ind", "ROLE_RH")))
				.andExpect(status().isOk())
				.andReturn();
		IndicateursWorkflowResponse ind = jsonMapper.readValue(
				res.getResponse().getContentAsString(), IndicateursWorkflowResponse.class);
		IndicateurEtapeDto dto = ind.parEtape().stream()
				.filter(e -> stepUnique.equals(e.etapeCode()))
				.findFirst().orElseThrow();
		assertThat(dto.nb()).isEqualTo(1);
		assertThat(dto.delaiMoyenHeures()).isBetween(new BigDecimal("1.95"), new BigDecimal("2.05"));
	}

	@Test
	void c9_postgresql_filtresDelegationsMoiEtAdmin() throws Exception {
		UUID collabA = UUID.randomUUID();
		UUID compteA = UUID.randomUUID();
		UUID collabB = UUID.randomUUID();
		UUID compteB = UUID.randomUUID();
		UUID collabC = UUID.randomUUID();
		UUID compteC = UUID.randomUUID();

		lenient().when(referentielClient.collaborateurParCompte(compteA))
				.thenReturn(Optional.of(new CollaborateurResume(collabA, compteA, "Collab A", "M-A", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(collabA))
				.thenReturn(Optional.of(new CollaborateurResume(collabA, compteA, "Collab A", "M-A", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(collabB))
				.thenReturn(Optional.of(new CollaborateurResume(collabB, compteB, "Collab B", "M-B", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(collabC))
				.thenReturn(Optional.of(new CollaborateurResume(collabC, compteC, "Collab C", "M-C", "ACTIF")));

		LocalDate auj = LocalDate.now(PlanificationEcheanceService.FUSEAU_DEFAUT);
		JwtRequestPostProcessor jwtA = jeton(compteA, "Collab A", "ROLE_USER");
		JwtRequestPostProcessor jwtRh = jeton(UUID.randomUUID(), "RH Admin", "ROLE_RH");

		// del1 : collabA -> collabB (active aujourd'hui)
		DelegationResponse del1 = jsonMapper.readValue(mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						new CreerDelegationMoiRequest(collabB, auj, auj.plusDays(3), null, "Active sortante")))
				.with(jwtA))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), DelegationResponse.class);

		// del2 : collabC -> collabA (active aujourd'hui, collabA est DELEGUE)
		DelegationResponse del2 = jsonMapper.readValue(mockMvc.perform(post("/api/rh/v1/admin/workflow/delegations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						new CreerDelegationAdminRequest(collabC, collabA, auj, auj.plusDays(4), null, "Active entrante")))
				.with(jwtRh))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), DelegationResponse.class);

		// del3 : collabA -> collabC puis annulée (actives = false)
		DelegationResponse del3 = jsonMapper.readValue(mockMvc.perform(post("/api/rh/v1/workflow/delegations/moi")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						new CreerDelegationMoiRequest(collabC, auj.plusDays(10), auj.plusDays(12), null, "Annulée")))
				.with(jwtA))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), DelegationResponse.class);
		mockMvc.perform(delete("/api/rh/v1/workflow/delegations/moi/{id}", del3.id()).with(jwtA))
				.andExpect(status().isNoContent());

		// del4 : collabB -> collabA expirée dans le passé (actives = false)
		WfDelegation entiteExpiree = new WfDelegation();
		entiteExpiree.setDelegantCollaborateurId(collabB);
		entiteExpiree.setDelegantNom("Collab B");
		entiteExpiree.setDelegueCollaborateurId(collabA);
		entiteExpiree.setDelegueCompteId(compteA);
		entiteExpiree.setDelegueNom("Collab A");
		entiteExpiree.setDu(auj.minusDays(10));
		entiteExpiree.setAu(auj.minusDays(2));
		entiteExpiree.setOrigine(OrigineDelegation.MANUELLE);
		entiteExpiree.setCreeeLe(Instant.now().minus(10, ChronoUnit.DAYS));
		UUID del4Id = delegationRepository.saveAndFlush(entiteExpiree).getId();

		// 1. /delegations/moi : role=DELEGANT|DELEGUE|TOUS & actives=true|false
		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "DELEGANT").queryParam("actives", "true").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactly(del1.id());
		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "DELEGANT").queryParam("actives", "false").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactly(del3.id());

		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "DELEGUE").queryParam("actives", "true").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactly(del2.id());
		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "DELEGUE").queryParam("actives", "false").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactly(del4Id);

		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "TOUS").queryParam("actives", "true").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactlyInAnyOrder(del1.id(), del2.id());
		assertThat(lireListeDelegations(mockMvc.perform(get("/api/rh/v1/workflow/delegations/moi")
				.queryParam("role", "TOUS").queryParam("actives", "false").with(jwtA))
				.andExpect(status().isOk()).andReturn()))
				.extracting(DelegationResponse::id).containsExactlyInAnyOrder(del3.id(), del4Id);

		// 2. /admin/workflow/delegations : delegant_id, delegue_id, actives=true|false
		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegant_id", collabA.toString()).queryParam("actives", "true").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.extracting(DelegationResponse::id).containsExactly(del1.id());
		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegant_id", collabA.toString()).queryParam("actives", "false").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.extracting(DelegationResponse::id).containsExactly(del3.id());

		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegue_id", collabA.toString()).queryParam("actives", "true").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.extracting(DelegationResponse::id).containsExactly(del2.id());
		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegue_id", collabA.toString()).queryParam("actives", "false").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.extracting(DelegationResponse::id).containsExactly(del4Id);

		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegant_id", collabA.toString())
				.queryParam("delegue_id", collabB.toString())
				.queryParam("actives", "true").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.extracting(DelegationResponse::id).containsExactly(del1.id());
		assertThat(lirePageDelegations(mockMvc.perform(get("/api/rh/v1/admin/workflow/delegations")
				.queryParam("delegant_id", collabA.toString())
				.queryParam("delegue_id", collabB.toString())
				.queryParam("actives", "false").with(jwtRh))
				.andExpect(status().isOk()).andReturn()).elements())
				.isEmpty();
	}

	private JwtRequestPostProcessor jeton(UUID compteId, String nom, String role) {
		return jwt().jwt(j -> j
				.subject(compteId.toString())
				.claim("identifiant_utilisateur", compteId.toString())
				.claim("nom", nom))
				.authorities(new SimpleGrantedAuthority(role));
	}

	private PageResponse<InstanceResumeResponse> lirePageInstances(MvcResult res) throws Exception {
		return jsonMapper.readValue(res.getResponse().getContentAsString(),
				new TypeReference<PageResponse<InstanceResumeResponse>>() {});
	}

	private List<DelegationResponse> lireListeDelegations(MvcResult res) throws Exception {
		return jsonMapper.readValue(res.getResponse().getContentAsString(),
				new TypeReference<List<DelegationResponse>>() {});
	}

	private PageResponse<DelegationResponse> lirePageDelegations(MvcResult res) throws Exception {
		return jsonMapper.readValue(res.getResponse().getContentAsString(),
				new TypeReference<PageResponse<DelegationResponse>>() {});
	}

	private WfModeleVersion creerVersion(String modeleCode, String objetType, String etapeCode, String etapeLibelle) {
		WfModele modele = modeleRepository.findByCode(modeleCode).orElseThrow();
		EtapeDefinition etape = new EtapeDefinition(etapeCode, etapeLibelle, "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		DefinitionCodec codec = new DefinitionCodec();
		DefinitionWorkflow def = new DefinitionWorkflow(1, objetType, null, List.of(etape));
		int nextNumero = versionRepository.findTopByModeleIdOrderByNumeroDesc(modele.getId())
				.map(top -> top.getNumero() + 1).orElse(3000);
		WfModeleVersion v = new WfModeleVersion();
		v.setModele(modele);
		v.setNumero(nextNumero);
		v.setStatut(StatutVersion.RETIREE);
		v.setDefinition(codec.serialiser(def));
		v.setFormatDefinition(1);
		v.setEmpreinte(codec.calculerEmpreinte(def));
		v.setModifieLe(Instant.now());
		return versionRepository.saveAndFlush(v);
	}

	private WfInstance creerInstance(
			WfModeleVersion version, String objetType, StatutInstance statut, String etapeCourante,
			UUID demandeurCollabId, String demandeurNom, Instant demarreLe, Instant termineLe) {
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(version);
		inst.setModeleCode("M01_CONGE");
		inst.setModeleVersionNumero(version.getNumero());
		inst.setObjetType(objetType);
		inst.setObjetId(UUID.randomUUID());
		inst.setObjetNumero("SUP-" + UUID.randomUUID().toString().substring(0, 6));
		inst.setServiceProprietaire("svc-referentiel-rh");
		inst.setDemandeurCollaborateurId(demandeurCollabId);
		inst.setDemandeurCompteId(UUID.randomUUID());
		inst.setDemandeurNom(demandeurNom);
		inst.setStatut(statut);
		inst.setEtapeCouranteCode(etapeCourante);
		inst.setVariables(Map.of("jours", 2));
		inst.setCleDemarrage("SUP-IT:" + UUID.randomUUID());
		inst.setDemarreLe(demarreLe);
		inst.setTermineLe(termineLe);
		return instanceRepository.saveAndFlush(inst);
	}

	private WfEtapeExecution creerEtapeActive(WfInstance inst, String etapeCode, Instant activeLe) {
		WfEtapeExecution ee = new WfEtapeExecution();
		ee.setInstance(inst);
		ee.setEtapeCode(etapeCode);
		ee.setOrdre(1);
		ee.setStatut(StatutEtapeExecution.ACTIVE);
		ee.setActiveLe(activeLe);
		return etapeExecutionRepository.saveAndFlush(ee);
	}

	private WfTache creerTache(WfInstance inst, WfEtapeExecution ee, String codeTache, String libelle, Instant echeanceLe) {
		WfTache t = new WfTache();
		t.setInstance(inst);
		t.setEtapeExecution(ee);
		t.setCodeTache(codeTache);
		t.setType(TypeTache.VALIDATION);
		t.setLibelle(libelle);
		t.setStatut(StatutTache.A_TRAITER);
		t.setObligatoire(true);
		t.setEcheanceLe(echeanceLe);
		return tacheRepository.saveAndFlush(t);
	}

	private void creerCandidat(WfTache t, UUID collabId, UUID compteId, String nom,
			OrigineCandidat origine, String pourNom, Instant now) {
		WfTacheCandidat cand = new WfTacheCandidat();
		cand.setId(new WfTacheCandidatId(t.getId(), collabId));
		cand.setCompteId(compteId);
		cand.setNom(nom);
		cand.setOrigine(origine);
		cand.setPourNom(pourNom);
		cand.setAjouteLe(now);
		candidatRepository.saveAndFlush(cand);
	}
}
