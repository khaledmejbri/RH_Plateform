package com.hr.workflow.web;

import com.hr.workflow.WorkflowApplication;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test anti-IDOR réel (§ 9.10, CA-08.3, revue W3 n°12) sur les vrais services et PostgreSQL {@code workflow_db_test} :
 * un tiers reçoit 404 avec le même code et le même message qu'un UUID inexistant.
 */
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecuriteAntiIdorIT {

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
	@MockitoBean private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

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
		fakeServer.createContext("/internal/referentiel/v1/collaborateurs/par-compte/", exchange -> {
			byte[] resp = "{\"detail\":\"absent\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(404, resp.length);
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
		registry.add("spring.datasource.password", SecuriteAntiIdorIT::dbPassword);
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", () -> "");
		registry.add("spring.task.scheduling.enabled", () -> "false");
		registry.add("workflow.referentiel.base-url", () -> "http://127.0.0.1:" + fakePort);
	}

	@Test
	void ca08_3_tiersRecoit404IdentiqueAUuidInexistant_surLes4Routes() throws Exception {
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		WfModeleVersion version = versionRepository.findByModeleIdAndStatut(modele.getId(), StatutVersion.PUBLIEE).orElseThrow();

		Instant maintenant = Instant.now();
		UUID objetIdExistant = UUID.randomUUID();
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(version);
		inst.setModeleCode(modele.getCode());
		inst.setModeleVersionNumero(version.getNumero());
		inst.setObjetType("CONGE");
		inst.setObjetId(objetIdExistant);
		inst.setObjetNumero("DEM-IDOR-" + UUID.randomUUID().toString().substring(0, 6));
		inst.setServiceProprietaire("svc-referentiel-rh");
		inst.setDemandeurCollaborateurId(UUID.randomUUID());
		inst.setDemandeurCompteId(UUID.randomUUID());
		inst.setDemandeurNom("Demandeur");
		inst.setStatut(StatutInstance.EN_COURS);
		inst.setEtapeCouranteCode("VALID_RH");
		inst.setVariables(Map.of());
		inst.setCleDemarrage("IDOR:" + UUID.randomUUID());
		inst.setDemarreLe(maintenant);
		inst = instanceRepository.saveAndFlush(inst);

		WfEtapeExecution ee = new WfEtapeExecution();
		ee.setInstance(inst);
		ee.setEtapeCode("VALID_RH");
		ee.setOrdre(1);
		ee.setStatut(StatutEtapeExecution.ACTIVE);
		ee.setActiveLe(maintenant);
		ee = etapeExecutionRepository.saveAndFlush(ee);

		WfTache tache = new WfTache();
		tache.setInstance(inst);
		tache.setEtapeExecution(ee);
		tache.setType(TypeTache.VALIDATION);
		tache.setLibelle("Validation RH");
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setObligatoire(true);
		tache.setEcheanceLe(maintenant.plusSeconds(3600));
		tache = tacheRepository.saveAndFlush(tache);

		WfTacheCandidat cand = new WfTacheCandidat();
		cand.setId(new WfTacheCandidatId(tache.getId(), UUID.randomUUID()));
		cand.setCompteId(UUID.randomUUID());
		cand.setNom("RH");
		cand.setOrigine(OrigineCandidat.TITULAIRE);
		cand.setAjouteLe(maintenant);
		candidatRepository.saveAndFlush(cand);

		UUID tiersCompteId = UUID.randomUUID();
		var jwtTiers = jwt().jwt(j -> j.subject("tiers").claim("identifiant_utilisateur", tiersCompteId.toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));

		UUID inexistant = UUID.randomUUID();

		// 1. GET /taches/{id}
		verifier404Identique(
				mockMvc.perform(get("/api/rh/v1/workflow/taches/" + tache.getId()).with(jwtTiers)).andExpect(status().isNotFound()).andReturn(),
				mockMvc.perform(get("/api/rh/v1/workflow/taches/" + inexistant).with(jwtTiers)).andExpect(status().isNotFound()).andReturn());

		// 2. POST /taches/{id}/decision
		String body = "{\"action\":\"APPROUVER\",\"version\":0,\"cle_idempotence\":\"idor-1\"}";
		verifier404Identique(
				mockMvc.perform(post("/api/rh/v1/workflow/taches/" + tache.getId() + "/decision")
						.with(jwtTiers).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound()).andReturn(),
				mockMvc.perform(post("/api/rh/v1/workflow/taches/" + inexistant + "/decision")
						.with(jwtTiers).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound()).andReturn());

		// 3. GET /instances/{id}/suivi
		verifier404Identique(
				mockMvc.perform(get("/api/rh/v1/workflow/instances/" + inst.getId() + "/suivi").with(jwtTiers)).andExpect(status().isNotFound()).andReturn(),
				mockMvc.perform(get("/api/rh/v1/workflow/instances/" + inexistant + "/suivi").with(jwtTiers)).andExpect(status().isNotFound()).andReturn());

		// 4. GET /objets/{t}/{id}/suivi
		verifier404Identique(
				mockMvc.perform(get("/api/rh/v1/workflow/objets/CONGE/" + objetIdExistant + "/suivi").with(jwtTiers)).andExpect(status().isNotFound()).andReturn(),
				mockMvc.perform(get("/api/rh/v1/workflow/objets/CONGE/" + inexistant + "/suivi").with(jwtTiers)).andExpect(status().isNotFound()).andReturn());
	}

	private void verifier404Identique(MvcResult resTiers, MvcResult resInexistant) throws Exception {
		JsonNode jTiers = jsonMapper.readTree(resTiers.getResponse().getContentAsString());
		JsonNode jInex = jsonMapper.readTree(resInexistant.getResponse().getContentAsString());
		assertThat(jTiers.get("code").asString()).isEqualTo(jInex.get("code").asString());
		assertThat(jTiers.get("message").asString()).isEqualTo(jInex.get("message").asString());
	}
}
