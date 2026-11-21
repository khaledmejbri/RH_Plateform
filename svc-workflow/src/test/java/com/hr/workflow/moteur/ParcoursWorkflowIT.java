package com.hr.workflow.moteur;

import com.hr.workflow.WorkflowApplication;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.EtapeDefinition.TacheParalleleDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.CompteurTachesResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheListeItemResponse;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.DecisionService.ResultatDecision;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couverture des critères manquants (revue W3 n°13) sur PostgreSQL {@code workflow_db_test} :
 * CA-07.1, CA-07.2, CA-07.3, CA-08.1, CA-08.2, décisions TOUS / TACHES_PARALLELES,
 * et {@code POST /internal/workflow/v1/instances/{id}/complement-fourni}.
 */
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ParcoursWorkflowIT {

	private static final String DB_URL = "jdbc:postgresql://localhost:5444/workflow_db_test";
	private static final String DB_USER = "postgres";

	private static HttpServer fakeServer;
	private static int fakePort;
	private static final UUID CANDIDAT_SUIVANT_COLLAB = UUID.randomUUID();
	private static final UUID CANDIDAT_SUIVANT_COMPTE = UUID.randomUUID();

	@Autowired private MockMvc mockMvc;
	@Autowired private WorkflowUtilisateurService utilisateurService;
	@Autowired private DecisionService decisionService;
	@Autowired private WfModeleRepository modeleRepository;
	@Autowired private WfModeleVersionRepository versionRepository;
	@Autowired private WfInstanceRepository instanceRepository;
	@Autowired private WfEtapeExecutionRepository etapeExecutionRepository;
	@Autowired private WfTacheRepository tacheRepository;
	@Autowired private WfTacheCandidatRepository candidatRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
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
		fakeServer.createContext("/internal/referentiel/v1/workflow/resolutions", exchange -> {
			byte[] resp = ("{\"resultats\":{\"ETAPE_SUIV\":{\"candidats\":[{\"collaborateur_id\":\"" + CANDIDAT_SUIVANT_COLLAB
					+ "\",\"compte_id\":\"" + CANDIDAT_SUIVANT_COMPTE
					+ "\",\"nom\":\"Nadia RH\",\"intitule\":\"RH\"}],\"raison_vide\":null},"
					+ "\"ETAPE_1\":{\"candidats\":[{\"collaborateur_id\":\"" + CANDIDAT_SUIVANT_COLLAB
					+ "\",\"compte_id\":\"" + CANDIDAT_SUIVANT_COMPTE
					+ "\",\"nom\":\"Omar Chef\",\"intitule\":\"Chef\"}],\"raison_vide\":null}},"
					+ "\"contexte_demandeur\":{\"profil_acces\":\"COLLABORATEUR\",\"type_noeud\":\"SERVICE\",\"anciennete_mois\":12,\"est_manager\":false}}")
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
		fakeServer.createContext("/internal/referentiel/v1/societe/calendrier", exchange -> {
			byte[] resp = "{\"fuseau\":\"Africa/Tunis\",\"jours_travailles\":[\"LUN\",\"MAR\",\"MER\",\"JEU\",\"VEN\"],\"heure_debut_journee\":\"08:00\",\"heure_fin_journee\":\"17:00\",\"delegation_auto_conges\":true,\"publication_quatre_yeux\":false,\"jours_feries\":[]}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
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
		registry.add("spring.datasource.password", ParcoursWorkflowIT::dbPassword);
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", () -> "");
		registry.add("spring.task.scheduling.enabled", () -> "false");
		registry.add("app.internal-api.token", () -> "test-internal-secret");
		registry.add("workflow.referentiel.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-referentiel-rh.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-presence.base-url", () -> "http://127.0.0.1:" + fakePort);
	}

	@Test
	void ca07_1_et_ca07_2_mesTachesTrieesParEcheance_taille20_etCompteurBaisseApresDecision() throws Exception {
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		WfModeleVersion version = versionRepository.findByModeleIdAndStatut(modele.getId(), StatutVersion.PUBLIEE).orElseThrow();

		UUID compteIntervenant = UUID.randomUUID();
		Instant maintenant = Instant.now();

		// Créer 21 tâches pour vérifier taille par défaut 20 et tri par échéance croissante
		WfTache tachePlusUrgente = null;
		for (int i = 0; i < 21; i++) {
			WfInstance inst = creerInstance(modele, version, "CONGE", "DEM-MESTACHES-" + i + "-" + UUID.randomUUID().toString().substring(0, 4),
					UUID.randomUUID(), UUID.randomUUID(), "Demandeur " + i, "VALID_RH", maintenant);
			WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RH", 1, maintenant);
			// La dernière itération a l'échéance la plus proche
			Instant echeance = maintenant.plus(21 - i, ChronoUnit.HOURS);
			WfTache t = creerTache(inst, ee, TypeTache.VALIDATION, "Validation RH " + i, compteIntervenant, "RH Test", echeance, maintenant);
			if (i == 20) {
				tachePlusUrgente = t;
			}
		}

		// CA-07.1 : taille par défaut 20 via MockMvc, triée par échéance ASC
		mockMvc.perform(get("/api/rh/v1/workflow/taches/moi")
				.with(jwt().jwt(j -> j.subject("rh").claim("identifiant_utilisateur", compteIntervenant.toString()))
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taille").value(20))
				.andExpect(jsonPath("$.total").value(21))
				.andExpect(jsonPath("$.elements[0].tache_id").value(tachePlusUrgente.getId().toString()))
				.andExpect(jsonPath("$.elements[0].libelle_etape").value("Validation RH 20"))
				.andExpect(jsonPath("$.elements[0].objet_type").value("CONGE"));

		PageResponse<TacheListeItemResponse> page0 = utilisateurService.mesTaches(compteIntervenant, "A_TRAITER", 0, 20);
		assertThat(page0.elements()).hasSize(20);
		assertThat(page0.elements().get(0).tacheId()).isEqualTo(tachePlusUrgente.getId());

		// CA-07.2 : compteur à 21 avant décision, puis 20 après décision
		CompteurTachesResponse avant = utilisateurService.compteurMesTaches(compteIntervenant);
		assertThat(avant.aTraiter()).isEqualTo(21);

		decisionService.decider(tachePlusUrgente.getId(),
				new DecisionRequest("APPROUVER", null, tachePlusUrgente.getVersion(), "cle-ca072-" + UUID.randomUUID()),
				compteIntervenant, "RH Test", true);

		CompteurTachesResponse apres = utilisateurService.compteurMesTaches(compteIntervenant);
		assertThat(apres.aTraiter()).isEqualTo(20);
	}

	@Test
	void ca07_3_ca08_1_ca08_2_etapeSuivanteCreee_notifsCandidatEtDemandeur_nomsPoolMasquesAuDemandeur() {
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		DefinitionCodec codec = new DefinitionCodec();
		EtapeDefinition e1 = new EtapeDefinition("ETAPE_1", "Validation Chef", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);
		EtapeDefinition e2 = new EtapeDefinition("ETAPE_SUIV", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);

		WfModeleVersion v = creerVersion(modele, codec, new DefinitionWorkflow(1, "CONGE", null, List.of(e1, e2)));
		UUID demandeurCollab = UUID.randomUUID();
		UUID demandeurCompte = UUID.randomUUID();
		UUID chefCompte = UUID.randomUUID();
		Instant maintenant = Instant.now();

		WfInstance inst = creerInstance(modele, v, "CONGE", "DEM-CA073-" + UUID.randomUUID().toString().substring(0, 6),
				demandeurCollab, demandeurCompte, "Sarra", "ETAPE_1", maintenant);
		WfEtapeExecution ee1 = creerEtapeActive(inst, "ETAPE_1", 1, maintenant);
		WfTache t1 = creerTache(inst, ee1, TypeTache.VALIDATION, "Validation Chef", chefCompte, "Omar Chef",
				maintenant.plusSeconds(3600), maintenant);

		// CA-07.3 : décision sur étape 1 -> étape suivante créée + notifications candidat suivant et demandeur
		ResultatDecision res = decisionService.decider(t1.getId(),
				new DecisionRequest("APPROUVER", null, t1.getVersion(), "cle-ca073-" + UUID.randomUUID()),
				chefCompte, "Omar Chef", false);
		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().instance().etapeCourante().code()).isEqualTo("ETAPE_SUIV");

		Integer notifsDemandeur = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wf_outbox WHERE sujet = 'rh.notifications' AND cle = ? AND charge->>'objet_id' = ?",
				Integer.class, demandeurCompte.toString(), inst.getObjetId().toString());
		Integer notifsCandidatSuivant = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wf_outbox WHERE sujet = 'rh.notifications' AND cle = ? AND charge->>'objet_id' = ?",
				Integer.class, CANDIDAT_SUIVANT_COMPTE.toString(), inst.getObjetId().toString());
		assertThat(notifsDemandeur).isEqualTo(1);
		assertThat(notifsCandidatSuivant).isEqualTo(1);

		// CA-08.1 & CA-08.2 : suivi demandeur (noms pool masqués) vs suivi RH/ADMIN (noms pool visibles)
		SuiviInstanceResponse suiviDemandeur = utilisateurService.suiviParInstance(inst.getId(), demandeurCompte, false, false);
		assertThat(suiviDemandeur.etapes()).hasSize(2);
		assertThat(suiviDemandeur.etapes().get(1).candidats()).isNull();

		SuiviInstanceResponse suiviRh = utilisateurService.suiviParInstance(inst.getId(), UUID.randomUUID(), true, true);
		assertThat(suiviRh.etapes().get(1).candidats()).containsExactly("Nadia RH");
	}

	@Test
	void decisionSequentielleModeTousEtTachesParalleles() {
		verifierSequenceDeuxTaches("VALIDATION", "APPROUVER", TypeTache.VALIDATION);
		verifierSequenceDeuxTaches("TACHES_PARALLELES", "FAIT", TypeTache.TACHE);
	}

	@Test
	void endpointInterneComplementFourni_200_reactiveEtape() throws Exception {
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		DefinitionCodec codec = new DefinitionCodec();
		EtapeDefinition e1 = new EtapeDefinition("ETAPE_1", "Validation Chef", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);
		WfModeleVersion v = creerVersion(modele, codec, new DefinitionWorkflow(1, "CONGE", null, List.of(e1)));

		UUID demandeurCollab = UUID.randomUUID();
		UUID demandeurCompte = UUID.randomUUID();
		Instant maintenant = Instant.now();

		WfInstance inst = creerInstance(modele, v, "CONGE", "DEM-COMP-" + UUID.randomUUID().toString().substring(0, 6),
				demandeurCollab, demandeurCompte, "Sarra", "ETAPE_1", maintenant);
		WfEtapeExecution ee = creerEtapeActive(inst, "ETAPE_1", 1, maintenant);
		creerTache(inst, ee, TypeTache.COMPLEMENT, "Complément demandé : Validation Chef",
				demandeurCompte, "Sarra", maintenant.plusSeconds(3600), maintenant);

		mockMvc.perform(post("/internal/workflow/v1/instances/" + inst.getId() + "/complement-fourni")
				.header("X-Internal-Token", "test-internal-secret")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"commentaire\":\"Justificatif ajouté\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decision").value("FAITE"))
				.andExpect(jsonPath("$.instance.statut").value("EN_COURS"))
				.andExpect(jsonPath("$.instance.etape_courante.libelle").value("Validation Chef"));
	}

	private void verifierSequenceDeuxTaches(String typeEtape, String action, TypeTache typeTache) {
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		DefinitionCodec codec = new DefinitionCodec();
		EtapeDefinition e1 = "TACHES_PARALLELES".equals(typeEtape)
				? new EtapeDefinition("ETAPE_1", "Parallèle", "TACHES_PARALLELES", null, null, null,
						List.of(
								new TacheParalleleDefinition("T1", "Tâche 1", new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), null), null, true),
								new TacheParalleleDefinition("T2", "Tâche 2", new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), null), null, true)),
						null, null, false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null)
				: new EtapeDefinition("ETAPE_1", "Validation TOUS", "VALIDATION", null,
						new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "TOUS"),
						new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
						false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);
		EtapeDefinition e2 = new EtapeDefinition("ETAPE_SUIV", "Suivante", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);

		WfModeleVersion v = creerVersion(modele, codec, new DefinitionWorkflow(1, "CONGE", null, List.of(e1, e2)));
		Instant maintenant = Instant.now();
		WfInstance inst = creerInstance(modele, v, "CONGE", "DEM-SEQ-" + UUID.randomUUID().toString().substring(0, 6),
				UUID.randomUUID(), UUID.randomUUID(), "Demandeur", "ETAPE_1", maintenant);
		WfEtapeExecution ee = creerEtapeActive(inst, "ETAPE_1", 1, maintenant);
		UUID c1 = UUID.randomUUID();
		UUID c2 = UUID.randomUUID();
		WfTache t1 = creerTache(inst, ee, typeTache, "T1", c1, "U1", maintenant.plusSeconds(3600), maintenant);
		WfTache t2 = creerTache(inst, ee, typeTache, "T2", c2, "U2", maintenant.plusSeconds(3600), maintenant);

		ResultatDecision r1 = decisionService.decider(t1.getId(),
				new DecisionRequest(action, null, t1.getVersion(), "cle-1-" + UUID.randomUUID()), c1, "U1", false);
		assertThat(r1.reponse().instance().etapeCourante().code()).isEqualTo("ETAPE_1");

		ResultatDecision r2 = decisionService.decider(t2.getId(),
				new DecisionRequest(action, null, t2.getVersion(), "cle-2-" + UUID.randomUUID()), c2, "U2", false);
		assertThat(r2.reponse().instance().etapeCourante().code()).isEqualTo("ETAPE_SUIV");
	}

	private WfModeleVersion creerVersion(WfModele modele, DefinitionCodec codec, DefinitionWorkflow def) {
		WfModeleVersion v = new WfModeleVersion();
		v.setModele(modele);
		v.setNumero(1000 + Math.abs(UUID.randomUUID().hashCode() % 100000));
		v.setStatut(StatutVersion.RETIREE);
		v.setDefinition(codec.serialiser(def));
		v.setFormatDefinition(1);
		v.setEmpreinte(codec.calculerEmpreinte(def));
		v.setModifieLe(Instant.now());
		return versionRepository.saveAndFlush(v);
	}

	private WfInstance creerInstance(WfModele modele, WfModeleVersion version, String objetType, String objetNumero,
			UUID demandeurCollab, UUID demandeurCompte, String demandeurNom, String etapeCode, Instant maintenant) {
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(version);
		inst.setModeleCode(modele.getCode());
		inst.setModeleVersionNumero(version.getNumero());
		inst.setObjetType(objetType);
		inst.setObjetId(UUID.randomUUID());
		inst.setObjetNumero(objetNumero);
		inst.setServiceProprietaire("svc-referentiel-rh");
		inst.setDemandeurCollaborateurId(demandeurCollab);
		inst.setDemandeurCompteId(demandeurCompte);
		inst.setDemandeurNom(demandeurNom);
		inst.setStatut(StatutInstance.EN_COURS);
		inst.setEtapeCouranteCode(etapeCode);
		inst.setVariables(Map.of());
		inst.setCleDemarrage("PARC:" + UUID.randomUUID());
		inst.setDemarreLe(maintenant);
		return instanceRepository.saveAndFlush(inst);
	}

	private WfEtapeExecution creerEtapeActive(WfInstance inst, String code, int ordre, Instant maintenant) {
		WfEtapeExecution ee = new WfEtapeExecution();
		ee.setInstance(inst);
		ee.setEtapeCode(code);
		ee.setOrdre(ordre);
		ee.setStatut(StatutEtapeExecution.ACTIVE);
		ee.setActiveLe(maintenant);
		return etapeExecutionRepository.saveAndFlush(ee);
	}

	private WfTache creerTache(WfInstance inst, WfEtapeExecution ee, TypeTache type, String libelle,
			UUID compteId, String nom, Instant echeance, Instant maintenant) {
		WfTache t = new WfTache();
		t.setInstance(inst);
		t.setEtapeExecution(ee);
		t.setType(type);
		t.setLibelle(libelle);
		t.setStatut(StatutTache.A_TRAITER);
		t.setObligatoire(true);
		t.setEcheanceLe(echeance);
		t = tacheRepository.saveAndFlush(t);

		WfTacheCandidat cand = new WfTacheCandidat();
		cand.setId(new WfTacheCandidatId(t.getId(), UUID.randomUUID()));
		cand.setCompteId(compteId);
		cand.setNom(nom);
		cand.setOrigine(OrigineCandidat.TITULAIRE);
		cand.setAjouteLe(maintenant);
		candidatRepository.saveAndFlush(cand);
		return t;
	}
}
