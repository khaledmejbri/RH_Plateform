package com.hr.workflow.moteur;

import com.hr.workflow.WorkflowApplication;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfOutboxRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration concurrence sur PostgreSQL {@code workflow_db_test} (§ 9.6.7, CA-07.4, D-W3-08, D-W3-10) :
 * 10 décisions simultanées sur la même tâche → 1 succès (200), 9 × 409,
 * une seule décision dans {@code wf_historique}, une seule notification dans {@code wf_outbox}.
 * Utilise {@link HttpServer} du JDK comme propriétaire factice (sans nouvelle dépendance Maven).
 */
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DecisionConcurrenteIT {

	private static final String DB_URL = "jdbc:postgresql://localhost:5444/workflow_db_test";
	private static final String DB_USER = "postgres";

	private static HttpServer fakeServer;
	private static int fakePort;

	@Autowired private DecisionService decisionService;
	@Autowired private WfModeleRepository modeleRepository;
	@Autowired private WfModeleVersionRepository versionRepository;
	@Autowired private WfActivationRepository activationRepository;
	@Autowired private WfInstanceRepository instanceRepository;
	@Autowired private WfEtapeExecutionRepository etapeExecutionRepository;
	@Autowired private WfTacheRepository tacheRepository;
	@Autowired private WfTacheCandidatRepository candidatRepository;
	@Autowired private WfHistoriqueRepository historiqueRepository;
	@Autowired private WfOutboxRepository outboxRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

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
		fakeServer.createContext("/internal/referentiel/v1/workflow/resolutions", exchange -> {
			byte[] resp = "{\"resultats\":{},\"contexte_demandeur\":{\"profil_acces\":\"COLLABORATEUR\",\"type_noeud\":\"SERVICE\",\"anciennete_mois\":12,\"est_manager\":false}}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, resp.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(resp);
			}
		});
		fakeServer.createContext("/internal/referentiel/v1/societe/calendrier", exchange -> {
			byte[] resp = "{\"fuseau\":\"Africa/Tunis\",\"jours_travailles\":[\"LUNDI\",\"MARDI\",\"MERCREDI\",\"JEUDI\",\"VENDREDI\"],\"heure_debut_journee\":\"08:00\",\"heure_fin_journee\":\"17:00\",\"delegation_auto_conges\":true,\"publication_quatre_yeux\":false,\"jours_feries\":[]}"
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
		registry.add("spring.datasource.password", DecisionConcurrenteIT::dbPassword);
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", () -> "");
		registry.add("spring.task.scheduling.enabled", () -> "false");
		registry.add("workflow.referentiel.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-referentiel-rh.base-url", () -> "http://127.0.0.1:" + fakePort);
		registry.add("workflow.proprietaires.svc-presence.base-url", () -> "http://127.0.0.1:" + fakePort);
	}

	@Test
	void ca07_4_dixDecisionsSimultanees_1Succes_9Conflits409_1Historique_1Notification() throws Exception {
		// Préparation d'une instance + tâche partagée avec 10 candidats actifs
		WfModele modele = modeleRepository.findByCode("M01_CONGE").orElseThrow();
		WfModeleVersion version = versionRepository.findByModeleIdAndStatut(modele.getId(), StatutVersion.PUBLIEE).orElseThrow();

		Instant maintenant = Instant.now();
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(version);
		inst.setModeleCode(modele.getCode());
		inst.setModeleVersionNumero(version.getNumero());
		inst.setObjetType("CONGE");
		inst.setObjetId(UUID.randomUUID());
		inst.setObjetNumero("DEM-CONC-0001");
		inst.setServiceProprietaire("svc-referentiel-rh");
		inst.setDemandeurCollaborateurId(UUID.randomUUID());
		inst.setDemandeurCompteId(UUID.randomUUID());
		inst.setDemandeurNom("Demandeur Concurrence");
		inst.setStatut(StatutInstance.EN_COURS);
		inst.setEtapeCouranteCode("VALID_RH");
		inst.setVariables(Map.of("jours_decomptes", 2));
		inst.setCleDemarrage("CONC:" + UUID.randomUUID());
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

		List<UUID> comptesCandidats = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			UUID compteId = UUID.randomUUID();
			comptesCandidats.add(compteId);
			WfTacheCandidat cand = new WfTacheCandidat();
			cand.setId(new WfTacheCandidatId(tache.getId(), UUID.randomUUID()));
			cand.setCompteId(compteId);
			cand.setNom("RH-" + i);
			cand.setOrigine(OrigineCandidat.TITULAIRE);
			cand.setAjouteLe(maintenant);
			candidatRepository.saveAndFlush(cand);
		}

		// Lancer 10 threads simultanément
		CountDownLatch pret = new CountDownLatch(10);
		CountDownLatch depart = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(10);
		AtomicInteger succes = new AtomicInteger();
		AtomicInteger conflits409 = new AtomicInteger();
		AtomicInteger autres = new AtomicInteger();

		final UUID tacheId = tache.getId();
		final Long versionInitiale = tache.getVersion();
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			final int idx = i;
			final UUID compteId = comptesCandidats.get(i);
			futures.add(pool.submit((Callable<Void>) () -> {
				pret.countDown();
				depart.await();
				try {
					var res = decisionService.decider(
							tacheId,
							new DecisionRequest("APPROUVER", null, versionInitiale, "cle-" + idx + "-" + UUID.randomUUID()),
							compteId, "RH-" + idx, true);
					if (res.httpStatus() == 200) {
						succes.incrementAndGet();
					} else {
						autres.incrementAndGet();
					}
				} catch (WorkflowMetierException ex) {
					if (ex.getHttpStatus() == 409) {
						conflits409.incrementAndGet();
					} else {
						autres.incrementAndGet();
					}
				} catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
					conflits409.incrementAndGet();
				}
				return null;
			}));
		}

		pret.await();
		depart.countDown();
		for (Future<?> f : futures) {
			f.get();
		}
		pool.shutdown();

		// Vérifications § 9.6.7 & CA-07.4
		assertThat(succes.get()).isEqualTo(1);
		assertThat(conflits409.get()).isEqualTo(9);
		assertThat(autres.get()).isEqualTo(0);

		List<WfHistorique> hist = historiqueRepository.findByInstanceIdOrderByHorodatageAsc(inst.getId());
		long nbDecisions = hist.stream().filter(h -> "DECISION".equals(h.getTypeEvenement())).count();
		assertThat(nbDecisions).isEqualTo(1);

		Integer nbNotifs = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wf_outbox WHERE sujet = 'rh.notifications' AND charge->>'objet_id' = ?",
				Integer.class, inst.getObjetId().toString());
		assertThat(nbNotifs).isEqualTo(1);
	}
}
