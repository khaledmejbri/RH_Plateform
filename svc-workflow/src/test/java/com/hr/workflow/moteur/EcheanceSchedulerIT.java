package com.hr.workflow.moteur;

import com.hr.workflow.WorkflowApplication;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EcheanceDefinition.ApresEcheance;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration PostgreSQL ({@code workflow_db_test}) d'{@link EcheanceScheduler} :
 * concurrence multi-threads (CA-10.4), escalade (CA-10.5), expiration + 409 (CA-10.6),
 * {@code RIEN} sans boucle infinie, expiration {@code COMPLEMENT} (CA-13.4) et Q4 ({@code BLOQUEE} sur 422).
 */
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EcheanceSchedulerIT {

	private static final String DB_URL = "jdbc:postgresql://localhost:5444/workflow_db_test";
	private static final String DB_USER = "postgres";

	private static HttpServer fakeServer;
	private static int fakePort;
	private static final UUID RH_ESCALADE_COLLAB = UUID.randomUUID();
	private static final UUID RH_ESCALADE_COMPTE = UUID.randomUUID();
	private static final UUID OBJET_Q4_REJET = UUID.randomUUID();

	@Autowired private EcheanceScheduler echeanceScheduler;
	@Autowired private DecisionService decisionService;
	@Autowired private WfModeleRepository modeleRepository;
	@Autowired private WfModeleVersionRepository versionRepository;
	@Autowired private WfInstanceRepository instanceRepository;
	@Autowired private WfEtapeExecutionRepository etapeExecutionRepository;
	@Autowired private WfTacheRepository tacheRepository;
	@Autowired private WfTacheCandidatRepository candidatRepository;
	@Autowired private WfHistoriqueRepository historiqueRepository;
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
		fakeServer.createContext("/internal/referentiel/v1/workflow/effets", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (body.contains(OBJET_Q4_REJET.toString())) {
				byte[] resp = "{\"code\":\"SOLDE_INSUFFISANT\",\"message\":\"Solde insuffisant\"}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(422, resp.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(resp);
				}
				return;
			}
			byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
		fakeServer.createContext("/internal/presence/", exchange -> {
			byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
		fakeServer.createContext("/internal/referentiel/v1/workflow/resolutions", exchange -> {
			byte[] resp = ("{\"resultats\":{\"escalade\":{\"candidats\":[{\"collaborateur_id\":\"" + RH_ESCALADE_COLLAB
					+ "\",\"compte_id\":\"" + RH_ESCALADE_COMPTE
					+ "\",\"nom\":\"Nadia RH\",\"intitule\":\"RH\"}],\"raison_vide\":null},"
					+ "\"roles\":{\"candidats\":[{\"collaborateur_id\":\"" + RH_ESCALADE_COLLAB
					+ "\",\"compte_id\":\"" + RH_ESCALADE_COMPTE
					+ "\",\"nom\":\"Nadia RH\",\"intitule\":\"RH\"}],\"raison_vide\":null}},"
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
		registry.add("spring.datasource.password", EcheanceSchedulerIT::dbPassword);
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", () -> "");
		registry.add("spring.task.scheduling.enabled", () -> "false");
		registry.add("workflow.referentiel.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-referentiel-rh.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-presence.base-url", () -> "http://127.0.0.1:" + fakePort);
	}

	@BeforeEach
	void nettoyerTachesEchuesAnterieures() {
		// Neutraliser d'éventuelles tâches A_TRAITER échues laissées par d'autres exécutions de tests
		jdbcTemplate.update("UPDATE wf_tache SET statut = 'ANNULEE' WHERE statut = 'A_TRAITER' AND (echeance_le <= now() OR prochaine_relance_le <= now() OR escalade_a_faire_le <= now())");
	}

	@Test
	void ca10_4_concurrence2Threads_relanceEnvoyeeUneSeuleFois() throws Exception {
		EtapeDefinition e1 = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null),
				new RelancesDefinition(1, 4, null),
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion version = creerVersion("M01_CONGE", "CONGE", e1);

		Instant now = Instant.now();
		WfInstance inst = creerInstance("CONGE", "M01_CONGE", UUID.randomUUID(), version, now);
		WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RH", now.minus(10, ChronoUnit.HOURS));
		WfTache tache = creerTache(inst, ee, TypeTache.VALIDATION, "Validation RH",
				now.plus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.MINUTES), null);
		creerTitulaire(tache, UUID.randomUUID(), UUID.randomUUID(), "Amel RH", now);

		lancerDeuxThreadsEnParallele();

		WfTache relue = tacheRepository.findById(tache.getId()).orElseThrow();
		assertThat(relue.getNbRelances()).isEqualTo(1);
		assertThat(relue.getProchaineRelanceLe()).isNull();

		List<WfHistorique> hist = historiqueRepository.findByInstanceIdOrderByHorodatageAsc(inst.getId());
		assertThat(hist.stream().filter(h -> "RELANCE".equals(h.getTypeEvenement())).count()).isEqualTo(1);

		Integer nbOutbox = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wf_outbox WHERE sujet = 'rh.notifications' AND charge->>'objet_id' = ? AND charge->>'evenement' = 'WF_RELANCE'",
				Integer.class, inst.getObjetId().toString());
		assertThat(nbOutbox).isEqualTo(1);
	}

	@Test
	void ca10_5_escaladeConcurrence_uneSeuleEscalade_titulaireConserveEtPeutDecider() throws Exception {
		EtapeDefinition e1 = new EtapeDefinition("VALID_RESP", "Validation Responsable", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("ESCALADER", new ApresEcheance(2, "HEURES"),
						new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL")),
				null, null, null);
		WfModeleVersion version = creerVersion("M01_CONGE", "CONGE", e1);

		Instant now = Instant.now();
		WfInstance inst = creerInstance("CONGE", "M01_CONGE", UUID.randomUUID(), version, now);
		WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RESP", now.minus(30, ChronoUnit.HOURS));
		WfTache tache = creerTache(inst, ee, TypeTache.VALIDATION, "Validation Responsable",
				now.minus(5, ChronoUnit.HOURS), null, now.minus(1, ChronoUnit.MINUTES));
		tache.setEcheanceTraiteeLe(now.minus(4, ChronoUnit.HOURS));
		tacheRepository.saveAndFlush(tache);

		UUID titulaireCollab = UUID.randomUUID();
		UUID titulaireCompte = UUID.randomUUID();
		creerTitulaire(tache, titulaireCollab, titulaireCompte, "Omar Chef", now);

		lancerDeuxThreadsEnParallele();

		WfTache relue = tacheRepository.findById(tache.getId()).orElseThrow();
		assertThat(relue.getEscaladeLe()).isNotNull();

		List<WfTacheCandidat> actifs = candidatRepository.findByIdTacheIdAndRetireLeIsNull(tache.getId());
		assertThat(actifs).hasSize(2);
		assertThat(actifs).anyMatch(c -> c.getOrigine() == OrigineCandidat.TITULAIRE && c.getId().getCollaborateurId().equals(titulaireCollab));
		assertThat(actifs).anyMatch(c -> c.getOrigine() == OrigineCandidat.ESCALADE && c.getId().getCollaborateurId().equals(RH_ESCALADE_COLLAB));

		List<WfHistorique> hist = historiqueRepository.findByInstanceIdOrderByHorodatageAsc(inst.getId());
		assertThat(hist.stream().filter(h -> "ESCALADEE".equals(h.getTypeEvenement())).count()).isEqualTo(1);

		// Le titulaire peut toujours décider après l'escalade
		var dec = decisionService.decider(tache.getId(),
				new DecisionRequest("APPROUVER", null, relue.getVersion(), "cle-esc-" + UUID.randomUUID()),
				titulaireCompte, "Omar Chef", false);
		assertThat(dec.httpStatus()).isEqualTo(200);
		assertThat(dec.reponse().instance().statut()).isEqualTo(StatutInstance.APPROUVEE);
	}

	@Test
	void ca10_6_expirerTermineInstance_etDecisionUlterieureRetourne409() {
		EtapeDefinition e1 = new EtapeDefinition("VALID_RESP", "Validation Pointage", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("EXPIRER", null, null), null, null, null);
		WfModeleVersion version = creerVersion("M09_POINTAGE_MANUEL", "POINTAGE_MANUEL", e1);

		Instant now = Instant.now();
		WfInstance inst = creerInstance("POINTAGE_MANUEL", "M09_POINTAGE_MANUEL", UUID.randomUUID(), version, now);
		WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RESP", now.minus(25, ChronoUnit.HOURS));
		WfTache tache = creerTache(inst, ee, TypeTache.VALIDATION, "Validation Pointage",
				now.minus(1, ChronoUnit.MINUTES), null, null);
		UUID candCompte = UUID.randomUUID();
		creerTitulaire(tache, UUID.randomUUID(), candCompte, "Valideur", now);

		int traites = echeanceScheduler.traiterEcheances();
		assertThat(traites).isEqualTo(1);

		WfInstance instRelue = instanceRepository.findById(inst.getId()).orElseThrow();
		WfTache tacheRelue = tacheRepository.findById(tache.getId()).orElseThrow();
		assertThat(instRelue.getStatut()).isEqualTo(StatutInstance.EXPIREE);
		assertThat(tacheRelue.getStatut()).isEqualTo(StatutTache.EXPIREE);

		// Décision tardive -> 409 TACHE_DEJA_TRAITEE
		assertThatThrownBy(() -> decisionService.decider(tache.getId(),
				new DecisionRequest("APPROUVER", null, tacheRelue.getVersion(), "cle-tard-" + UUID.randomUUID()),
				candCompte, "Valideur", false))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "TACHE_DEJA_TRAITEE");
	}

	@Test
	void ca13_4_expirationComplement_annuleInstanceEtAppelleEffetAnnuler() {
		EtapeDefinition e1 = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion version = creerVersion("M01_CONGE", "CONGE", e1);

		Instant now = Instant.now();
		WfInstance inst = creerInstance("CONGE", "M01_CONGE", UUID.randomUUID(), version, now);
		WfEtapeExecution ee = creerEtapeActive(inst, "VALID_RH", now.minus(120, ChronoUnit.HOURS));
		WfTache comp = creerTache(inst, ee, TypeTache.COMPLEMENT, "Complément demandé : Validation RH",
				now.minus(5, ChronoUnit.MINUTES), null, null);
		creerTitulaire(comp, inst.getDemandeurCollaborateurId(), inst.getDemandeurCompteId(), "Sarra", now);

		int traites = echeanceScheduler.traiterEcheances();
		assertThat(traites).isEqualTo(1);

		WfInstance instRelue = instanceRepository.findById(inst.getId()).orElseThrow();
		WfTache compRelue = tacheRepository.findById(comp.getId()).orElseThrow();
		assertThat(instRelue.getStatut()).isEqualTo(StatutInstance.ANNULEE);
		assertThat(instRelue.getResultatMotif()).isEqualTo("Complément non fourni dans le délai");
		assertThat(compRelue.getStatut()).isEqualTo(StatutTache.EXPIREE);
	}

	@Test
	void q4_rejet422SurActionAuto_passeBloqueeEtNeBouclePas_etRienNeBouclePas() {
		EtapeDefinition eRefusAuto = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("REFUSER_AUTO", null, null), null, null, null);
		WfModeleVersion vQ4 = creerVersion("M01_CONGE", "CONGE", eRefusAuto);

		Instant now = Instant.now();
		WfInstance instQ4 = creerInstance("CONGE", "M01_CONGE", OBJET_Q4_REJET, vQ4, now);
		WfEtapeExecution eeQ4 = creerEtapeActive(instQ4, "VALID_RH", now.minus(30, ChronoUnit.HOURS));
		WfTache tQ4 = creerTache(instQ4, eeQ4, TypeTache.VALIDATION, "Validation RH",
				now.minus(1, ChronoUnit.MINUTES), null, null);
		creerTitulaire(tQ4, UUID.randomUUID(), UUID.randomUUID(), "RH", now);

		EtapeDefinition eRien = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion vRien = creerVersion("M01_CONGE", "CONGE", eRien);
		WfInstance instRien = creerInstance("CONGE", "M01_CONGE", UUID.randomUUID(), vRien, now);
		WfEtapeExecution eeRien = creerEtapeActive(instRien, "VALID_RH", now.minus(30, ChronoUnit.HOURS));
		WfTache tRien = creerTache(instRien, eeRien, TypeTache.VALIDATION, "Validation RH",
				now.minus(1, ChronoUnit.MINUTES), null, null);
		creerTitulaire(tRien, UUID.randomUUID(), UUID.randomUUID(), "RH", now);

		int premierPassage = echeanceScheduler.traiterEcheances();
		assertThat(premierPassage).isEqualTo(2);

		WfInstance instQ4Relue = instanceRepository.findById(instQ4.getId()).orElseThrow();
		WfTache tQ4Relue = tacheRepository.findById(tQ4.getId()).orElseThrow();
		assertThat(instQ4Relue.getStatut()).isEqualTo(StatutInstance.BLOQUEE);
		assertThat(tQ4Relue.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tQ4Relue.getEcheanceTraiteeLe()).isNotNull();

		WfTache tRienRelue = tacheRepository.findById(tRien.getId()).orElseThrow();
		assertThat(tRienRelue.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tRienRelue.getEcheanceTraiteeLe()).isNotNull();

		// Deuxième passage : ni Q4 ni RIEN ne doivent être retraités (0 boucle infinie)
		int secondPassage = echeanceScheduler.traiterEcheances();
		assertThat(secondPassage).isZero();
	}

	private void lancerDeuxThreadsEnParallele() throws Exception {
		CountDownLatch pret = new CountDownLatch(2);
		CountDownLatch depart = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Callable<Integer> job = () -> {
			pret.countDown();
			depart.await();
			return echeanceScheduler.traiterEcheances();
		};
		Future<Integer> f1 = pool.submit(job);
		Future<Integer> f2 = pool.submit(job);
		pret.await();
		depart.countDown();
		int total = f1.get() + f2.get();
		pool.shutdown();
		assertThat(total).isEqualTo(1);
	}

	private WfModeleVersion creerVersion(String modeleCode, String objetType, EtapeDefinition etape) {
		WfModele modele = modeleRepository.findByCode(modeleCode).orElseThrow();
		DefinitionCodec codec = new DefinitionCodec();
		DefinitionWorkflow def = new DefinitionWorkflow(1, objetType, null, List.of(etape));
		WfModeleVersion v = new WfModeleVersion();
		v.setModele(modele);
		v.setNumero(2000 + Math.abs(UUID.randomUUID().hashCode() % 100000));
		v.setStatut(StatutVersion.RETIREE);
		v.setDefinition(codec.serialiser(def));
		v.setFormatDefinition(1);
		v.setEmpreinte(codec.calculerEmpreinte(def));
		v.setModifieLe(Instant.now());
		return versionRepository.saveAndFlush(v);
	}

	private WfInstance creerInstance(String objetType, String modeleCode, UUID objetId, WfModeleVersion version, Instant now) {
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(version);
		inst.setModeleCode(modeleCode);
		inst.setModeleVersionNumero(version.getNumero());
		inst.setObjetType(objetType);
		inst.setObjetId(objetId);
		inst.setObjetNumero("ECH-" + UUID.randomUUID().toString().substring(0, 6));
		inst.setServiceProprietaire("POINTAGE_MANUEL".equals(objetType) ? "svc-presence" : "svc-referentiel-rh");
		inst.setDemandeurCollaborateurId(UUID.randomUUID());
		inst.setDemandeurCompteId(UUID.randomUUID());
		inst.setDemandeurNom("Sarra Demandeur");
		inst.setStatut(StatutInstance.EN_COURS);
		inst.setEtapeCouranteCode("VALID_RH");
		inst.setVariables(Map.of());
		inst.setCleDemarrage("ECH-IT:" + UUID.randomUUID());
		inst.setDemarreLe(now);
		return instanceRepository.saveAndFlush(inst);
	}

	private WfEtapeExecution creerEtapeActive(WfInstance inst, String code, Instant activeLe) {
		WfEtapeExecution ee = new WfEtapeExecution();
		ee.setInstance(inst);
		ee.setEtapeCode(code);
		ee.setOrdre(1);
		ee.setStatut(StatutEtapeExecution.ACTIVE);
		ee.setActiveLe(activeLe);
		return etapeExecutionRepository.saveAndFlush(ee);
	}

	private WfTache creerTache(WfInstance inst, WfEtapeExecution ee, TypeTache type, String libelle,
			Instant echeanceLe, Instant prochaineRelanceLe, Instant escaladeAFaireLe) {
		WfTache t = new WfTache();
		t.setInstance(inst);
		t.setEtapeExecution(ee);
		t.setType(type);
		t.setLibelle(libelle);
		t.setStatut(StatutTache.A_TRAITER);
		t.setObligatoire(true);
		t.setEcheanceLe(echeanceLe);
		t.setProchaineRelanceLe(prochaineRelanceLe);
		t.setEscaladeAFaireLe(escaladeAFaireLe);
		return tacheRepository.saveAndFlush(t);
	}

	private void creerTitulaire(WfTache t, UUID collabId, UUID compteId, String nom, Instant now) {
		WfTacheCandidat cand = new WfTacheCandidat();
		cand.setId(new WfTacheCandidatId(t.getId(), collabId));
		cand.setCompteId(compteId);
		cand.setNom(nom);
		cand.setOrigine(OrigineCandidat.TITULAIRE);
		cand.setAjouteLe(now);
		candidatRepository.saveAndFlush(cand);
	}
}
