package com.hr.referentiel.config;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.JourFerie;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.entity.AcquisitionMois;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.repository.MouvementSoldeCongeRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.repository.SoldeCongeRepository;
import com.hr.referentiel.service.AcquisitionFicheService;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.ReglagesCongeService;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base H2 (mode PostgreSQL) : amorçage des jours fériés et des réglages, unicités, verrou du solde (concurrence) et
 * idempotence de l'acquisition. Les verrous de ligne de H2 reproduisent la sérialisation attendue de PostgreSQL ;
 * un essai sur le PostgreSQL de recette reste à faire (voir rapport).
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:soldes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(SoldesCongeH2Test.Config.class)
class SoldesCongeH2Test {

	@Configuration
	@EnableJpaRepositories(basePackageClasses = SoldeCongeRepository.class, excludeFilters =
	@ComponentScan.Filter(type = FilterType.REGEX,
			pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(Solde|Mouvement|JourFerie|Reglages|Acquisition)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(SoldeConge.class.getName(), MouvementSoldeConge.class.getName(),
					JourFerie.class.getName(), ParametresConges.class.getName(), AcquisitionMois.class.getName());
		}

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

		@Bean
		DemandeAdministrativeRhRepository demandeRepository() {
			return Mockito.mock(DemandeAdministrativeRhRepository.class);
		}
	}

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;
	@Autowired
	SoldeCongeRepository soldeRepository;
	@Autowired
	MouvementSoldeCongeRepository mouvementRepository;
	@Autowired
	JourFerieRepository jourFerieRepository;
	@Autowired
	ReglagesCongesRepository reglagesRepository;
	@Autowired
	AcquisitionMoisRepository acquisitionMoisRepository;
	@Autowired
	DemandeAdministrativeRhRepository demandeRepository;
	@Autowired
	Clock clock;
	@jakarta.persistence.PersistenceContext
	jakarta.persistence.EntityManager em;

	@BeforeEach
	void init() {
		mouvementRepository.deleteAll();
		soldeRepository.deleteAll();
		jourFerieRepository.deleteAll();
		reglagesRepository.deleteAll();
		acquisitionMoisRepository.deleteAll();
		jdbc.execute("drop table if exists rh_jour_ferie_amorcage");
	}

	private SoldesCongeAmorceRunner runner(String instant) {
		return new SoldesCongeAmorceRunner(jdbc, tm, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
	}

	// ─── T8 : amorçage ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("C8-01 : premier démarrage en 2026 -> 16 jours fériés 2026-2027 ; redémarrage -> toujours 16")
	void seizeJoursFeries() {
		runner("2026-11-12T10:00:00Z").amorcer();
		assertThat(jourFerieRepository.count()).isEqualTo(16);
		runner("2026-11-12T10:00:00Z").amorcer();
		assertThat(jourFerieRepository.count()).isEqualTo(16);
		assertThat(jourFerieRepository.findByDateJour(java.time.LocalDate.of(2027, 3, 20))).isPresent();
		assertThat(jourFerieRepository.findAll()).allMatch(JourFerie::isActif);
	}

	@Test
	@DisplayName("C8-01 : un jour supprimé n'est pas recréé au redémarrage (15), l'année suivante est amorcée le 1er janvier")
	void dateSupprimeeNonRecreee() {
		runner("2026-11-12T10:00:00Z").amorcer();
		jourFerieRepository.delete(jourFerieRepository.findByDateJour(java.time.LocalDate.of(2026, 5, 1)).orElseThrow());
		runner("2026-11-13T10:00:00Z").amorcer();
		assertThat(jourFerieRepository.count()).isEqualTo(15);
		// 1er janvier 2027 (Tunis, 31/12 23:30 UTC) : 2027 existe déjà, 2028 est ajoutée (8 dates)
		runner("2026-12-31T23:30:00Z").amorcer();
		assertThat(jourFerieRepository.count()).isEqualTo(15 + 8);
		assertThat(jourFerieRepository.findByDateJour(java.time.LocalDate.of(2028, 1, 1))).isPresent();
	}

	@Test
	@DisplayName("C8-01 / T8 3 ter : ligne de réglages créée à 1,5 jour, début des acquisitions = 1er du mois suivant, posé une fois")
	void reglagesAmorces() {
		runner("2026-11-12T10:00:00Z").amorcer();
		ParametresConges p = reglagesRepository.findById(ParametresConges.ID_UNIQUE).orElseThrow();
		assertThat(p.getJoursAcquisParMois()).isEqualByComparingTo("1.5");
		assertThat(p.getDebutAcquisitionsLe()).isEqualTo(java.time.LocalDate.of(2026, 12, 1));
		runner("2027-02-10T10:00:00Z").amorcer();
		assertThat(reglagesRepository.findById(ParametresConges.ID_UNIQUE).orElseThrow().getDebutAcquisitionsLe())
				.isEqualTo(java.time.LocalDate.of(2026, 12, 1));
	}

	@Test
	@DisplayName("CHECK acquis >= 0 posée : un acquis négatif est refusé par la base")
	void contrainteAcquisPositif() {
		runner("2026-11-12T10:00:00Z").amorcer();
		assertThatThrownBy(() -> jdbc.update("insert into rh_solde_conge (collaborateur_id, acquis, version) values (?, -1, 0)",
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
	}

	// ─── T1 : unicités, acquisition, verrou ────────────────────────────────────

	private SoldeCongeService soldeService() {
		return new SoldeCongeService(soldeRepository, mouvementRepository, demandeRepository, clock, em);
	}

	@Test
	@DisplayName("unicité (collaborateur, mois) en base : une seconde acquisition du même mois est refusée")
	void uniciteAcquisition() {
		UUID id = UUID.randomUUID();
		mouvementRepository.saveAndFlush(new MouvementSoldeConge(id, "ACQUISITION", new BigDecimal("1.5"),
				new BigDecimal("1.5"), "SYSTEME", null, null, null, "2026-12", null, null, "Système (automatique)", Instant.now()));
		assertThatThrownBy(() -> mouvementRepository.saveAndFlush(new MouvementSoldeConge(id, "ACQUISITION",
				new BigDecimal("1.5"), new BigDecimal("3.0"), "SYSTEME", null, null, null, "2026-12", null, null,
				"Système (automatique)", Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("critère 21 : acquisition deux fois pour la même fiche et le même mois -> un seul mouvement")
	void acquisitionIdempotente() {
		runner("2026-11-12T10:00:00Z").amorcer();
		ReglagesCongeService reglages = Mockito.mock(ReglagesCongeService.class);
		AcquisitionFicheService fiche = new AcquisitionFicheService(soldeService(), mouvementRepository,
				acquisitionMoisRepository, reglages);
		UUID id = UUID.randomUUID();
		TransactionTemplate tx = new TransactionTemplate(tm);
		Boolean premier = tx.execute(s -> fiche.acquerir(id, "2026-12", new BigDecimal("1.5")));
		Boolean second = tx.execute(s -> fiche.acquerir(id, "2026-12", new BigDecimal("1.5")));
		assertThat(premier).isTrue();
		assertThat(second).isFalse();
		assertThat(mouvementRepository.countByCollaborateurIdAndMoisAcquisition(id, "2026-12")).isEqualTo(1);
		assertThat(soldeRepository.findById(id).orElseThrow().getAcquis()).isEqualByComparingTo("1.5");
	}

	@Test
	@DisplayName("N figé au premier passage du mois : un changement de réglage ensuite ne change rien (C1-16)")
	void jourParMoisFige() {
		runner("2026-11-12T10:00:00Z").amorcer();
		ReglagesCongeService reglages = Mockito.mock(ReglagesCongeService.class);
		Mockito.when(reglages.joursParMois()).thenReturn(new BigDecimal("1.5"), new BigDecimal("2.0"));
		AcquisitionFicheService fiche = new AcquisitionFicheService(soldeService(), mouvementRepository,
				acquisitionMoisRepository, reglages);
		TransactionTemplate tx = new TransactionTemplate(tm);
		BigDecimal un = tx.execute(s -> fiche.figerJoursDuMois("2026-12"));
		BigDecimal deux = tx.execute(s -> fiche.figerJoursDuMois("2026-12"));
		assertThat(un).isEqualByComparingTo("1.5");
		assertThat(deux).isEqualByComparingTo("1.5");
	}

	@Test
	@DisplayName("C1-08 / CA 10 : deux approbations simultanées de 3 jours avec 6 acquis -> exactement une passe, 20 essais, acquis jamais négatif")
	void concurrenceApprobations() throws Exception {
		runner("2026-11-12T10:00:00Z").amorcer();
		SoldeCongeService service = soldeService();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int essai = 0; essai < 20; essai++) {
				UUID id = UUID.randomUUID();
				// acquis 5 après ajustement à -1 (scénario du critère 10)
				TransactionTemplate init = new TransactionTemplate(tm);
				init.executeWithoutResult(s -> service.enregistrer(service.verrouiller(id), TypeMouvementSolde.AJUSTEMENT,
						new BigDecimal("5.0"), OrigineMouvementSolde.SAISIE_MANUELLE, "init", null, null, null, null,
						AuteurParametre.systeme()));
				AtomicInteger ok = new AtomicInteger();
				AtomicInteger insuffisant = new AtomicInteger();
				List<Callable<Void>> taches = new ArrayList<>();
				for (int k = 0; k < 2; k++) {
					taches.add(() -> {
						try {
							new TransactionTemplate(tm).executeWithoutResult(s -> {
								SoldeConge solde = service.verrouiller(id);
								if (solde.getAcquis().compareTo(new BigDecimal("3.0")) < 0) {
									throw ReferentielMetierException.unprocessable("SOLDE_INSUFFISANT", "insuffisant");
								}
								service.enregistrer(solde, TypeMouvementSolde.CONGE_PRIS, new BigDecimal("-3.0"), null, null,
										null, null, null, null, AuteurParametre.systeme());
							});
							ok.incrementAndGet();
						} catch (ReferentielMetierException e) {
							insuffisant.incrementAndGet();
						}
						return null;
					});
				}
				for (Future<Void> f : pool.invokeAll(taches)) {
					f.get();
				}
				assertThat(ok.get()).as("essai %d", essai).isEqualTo(1);
				assertThat(insuffisant.get()).isEqualTo(1);
				assertThat(soldeRepository.findById(id).orElseThrow().getAcquis()).isEqualByComparingTo("2");
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("C3-06 : deux ajusteurs verrouillent la même ligne -> l'un lit la version à jour de l'autre (aucun écart perdu)")
	void versionIncrementeeParMouvement() {
		SoldeCongeService service = soldeService();
		UUID id = UUID.randomUUID();
		TransactionTemplate tx = new TransactionTemplate(tm);
		Long v0 = tx.execute(s -> service.verrouiller(id).getVersion());
		tx.executeWithoutResult(s -> service.enregistrer(service.verrouiller(id), TypeMouvementSolde.AJUSTEMENT,
				new BigDecimal("2.0"), OrigineMouvementSolde.SAISIE_MANUELLE, "Report", null, null, null, null,
				AuteurParametre.systeme()));
		Long v1 = tx.execute(s -> service.verrouiller(id).getVersion());
		assertThat(v0).isZero();
		assertThat(v1).isEqualTo(1L);
		// acquis = somme des mouvements (invariant)
		BigDecimal somme = mouvementRepository.findByCollaborateurIdOrderByCreeLeDescIdDesc(id).stream()
				.map(MouvementSoldeConge::getNombre).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(soldeRepository.findById(id).orElseThrow().getAcquis()).isEqualByComparingTo(somme);
	}

	@Test
	@DisplayName("acquis négatif impossible : le mouvement qui rendrait le solde négatif est refusé (422 SOLDE_NEGATIF)")
	void soldeNegatifRefuse() {
		SoldeCongeService service = soldeService();
		UUID id = UUID.randomUUID();
		TransactionTemplate tx = new TransactionTemplate(tm);
		assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.enregistrer(service.verrouiller(id),
				TypeMouvementSolde.CONGE_PRIS, new BigDecimal("-1.0"), null, null, null, null, null, null,
				AuteurParametre.systeme()))).isInstanceOf(ReferentielMetierException.class);
		assertThat(mouvementRepository.findByCollaborateurIdOrderByCreeLeDescIdDesc(id)).isEmpty();
	}

	@Test
	@DisplayName("B2 : un solde déjà chargé dans le contexte (import) puis modifié par une autre transaction est relu sous verrou (valeur fraîche, écart exact)")
	void verrouillerRelitLEtatSousVerrou() {
		SoldeCongeService service = soldeService();
		UUID id = UUID.randomUUID();
		TransactionTemplate tx = new TransactionTemplate(tm);
		tx.executeWithoutResult(s -> service.enregistrer(service.verrouiller(id), TypeMouvementSolde.AJUSTEMENT,
				new BigDecimal("10.0"), OrigineMouvementSolde.SAISIE_MANUELLE, "init", null, null, null, null,
				AuteurParametre.systeme()));
		TransactionTemplate autre = new TransactionTemplate(tm);
		autre.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		BigDecimal relu = tx.execute(s -> {
			// comme ImportSoldesService.charger() : entité chargée, donc gérée
			SoldeConge charge = service.soldes(List.of(id)).get(0);
			assertThat(charge.getAcquis()).isEqualByComparingTo("10");
			// une approbation (autre transaction) passe pendant l'import : acquis 10 -> 7, version 1 -> 2
			autre.executeWithoutResult(s2 -> service.enregistrer(service.verrouiller(id), TypeMouvementSolde.CONGE_PRIS,
					new BigDecimal("-3.0"), null, null, null, null, null, null, AuteurParametre.systeme()));
			SoldeConge verrouille = service.verrouiller(id);
			return verrouille.getAcquis();
		});
		assertThat(relu).isEqualByComparingTo("7");
		assertThat(soldeRepository.findById(id).orElseThrow().getVersion()).isEqualTo(2L);
		BigDecimal somme = mouvementRepository.findByCollaborateurIdOrderByCreeLeDescIdDesc(id).stream()
				.map(MouvementSoldeConge::getNombre).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(somme).isEqualByComparingTo("7");
	}

	@Test
	@DisplayName("lot : l'écart est calculé sur l'acquis frais lu sous verrou (jamais une instance périmée), soldes manquants créés, ligne devenue égale sans mouvement")
	void lotSurAcquisFrais() {
		SoldeCongeService service = soldeService();
		com.hr.referentiel.service.SoldeLotService lot = new com.hr.referentiel.service.SoldeLotService(jdbc, clock);
		UUID existant = UUID.randomUUID();
		UUID neuf = UUID.randomUUID();
		UUID egal = UUID.randomUUID();
		TransactionTemplate tx = new TransactionTemplate(tm);
		tx.executeWithoutResult(s -> {
			service.enregistrer(service.verrouiller(existant), TypeMouvementSolde.AJUSTEMENT, new BigDecimal("10.0"),
					OrigineMouvementSolde.SAISIE_MANUELLE, "init", null, null, null, null, AuteurParametre.systeme());
			service.enregistrer(service.verrouiller(egal), TypeMouvementSolde.AJUSTEMENT, new BigDecimal("5.0"),
					OrigineMouvementSolde.SAISIE_MANUELLE, "init", null, null, null, null, AuteurParametre.systeme());
		});
		TransactionTemplate autre = new TransactionTemplate(tm);
		autre.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		UUID importId = UUID.randomUUID();
		List<com.hr.referentiel.service.SoldeLotService.Resultat> r = tx.execute(s -> {
			service.soldes(List.of(existant)); // entité gérée, comme l'ancien chargement de l'import
			autre.executeWithoutResult(s2 -> service.enregistrer(service.verrouiller(existant), TypeMouvementSolde.CONGE_PRIS,
					new BigDecimal("-3.0"), null, null, null, null, null, null, AuteurParametre.systeme()));
			return lot.ajuster(List.of(
					new com.hr.referentiel.service.SoldeLotService.Ajustement(existant, new BigDecimal("12.5"), "Reprise"),
					new com.hr.referentiel.service.SoldeLotService.Ajustement(neuf, new BigDecimal("3.0"), "Reprise"),
					new com.hr.referentiel.service.SoldeLotService.Ajustement(egal, new BigDecimal("5.0"), "Reprise")),
					importId, new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
		});
		java.util.Map<UUID, com.hr.referentiel.service.SoldeLotService.Resultat> parId = new java.util.HashMap<>();
		r.forEach(x -> parId.put(x.collaborateurId(), x));
		assertThat(parId.get(existant).ecart()).isEqualByComparingTo("5.5"); // 12,5 - 7 (et non 12,5 - 10)
		assertThat(parId.get(neuf).ecart()).isEqualByComparingTo("3.0");
		assertThat(parId.get(egal).ecart()).isNull();
		assertThat(jdbc.queryForObject("select acquis from rh_solde_conge where collaborateur_id = ?", BigDecimal.class, existant))
				.isEqualByComparingTo("12.5");
		assertThat(jdbc.queryForObject("select version from rh_solde_conge where collaborateur_id = ?", Long.class, existant)).isEqualTo(3L);
		assertThat(jdbc.queryForObject("select count(*) from rh_mouvement_solde_conge where collaborateur_id = ?", Long.class, egal)).isEqualTo(1L);
		for (UUID id : List.of(existant, neuf, egal)) {
			BigDecimal somme = jdbc.queryForObject("select coalesce(sum(nombre), 0) from rh_mouvement_solde_conge where collaborateur_id = ?",
					BigDecimal.class, id);
			assertThat(jdbc.queryForObject("select acquis from rh_solde_conge where collaborateur_id = ?", BigDecimal.class, id))
					.isEqualByComparingTo(somme);
		}
	}
}
