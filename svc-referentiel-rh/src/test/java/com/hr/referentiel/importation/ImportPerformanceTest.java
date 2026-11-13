package com.hr.referentiel.importation;

import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportDtos.Analyse;
import com.hr.referentiel.importation.ImportDtos.CompteRendu;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.service.SoldeLotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-I13.k : 5 000 lignes importées en moins de 60 s (analyse et confirmation), fiches puis soldes, sur base H2 avec les
 * dépôts JPA réels. Les durées sont affichées ; la borne de 60 s est celle du ticket (H2 est plus rapide que le
 * PostgreSQL de recette : la mesure sur le kit reste à faire).
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:perf;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.jdbc.batch_size=50",
		"spring.jpa.properties.hibernate.order_inserts=true",
		"spring.jpa.properties.hibernate.order_updates=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ImportPerformanceTest.Config.class)
class ImportPerformanceTest {

	static final int LIGNES = 5_000;
	static final Duration BORNE = Duration.ofSeconds(60);

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ImportAnalyseRepository.class, basePackages = {"com.hr.referentiel.importation",
			"com.hr.referentiel.repository"}, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
			pattern = "com\\.hr\\.referentiel\\.(?!(repository\\.(Collaborateur|UniteOrganisation)Repository|importation\\.Import)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ImportAnalyse.class.getName(), ImportContenu.class.getName(), ImportLigne.class.getName(),
					Collaborateur.class.getName(), UniteOrganisation.class.getName(), SoldeConge.class.getName(),
					MouvementSoldeConge.class.getName());
		}
	}

	@Autowired
	ImportAnalyseRepository analyseRepository;
	@Autowired
	ImportContenuRepository contenuRepository;
	@Autowired
	ImportLigneRepository ligneRepository;
	@Autowired
	CollaborateurRepository collaborateurRepository;
	@Autowired
	UniteOrganisationRepository uniteRepository;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("D-I13.k : 5 000 fiches créées puis 5 000 ajustements de soldes, analyse et confirmation chacune en moins de 60 s (durées affichées)")
	void cinqMilleLignes() {
		Clock horloge = Clock.fixed(Instant.parse("2026-12-10T10:00:00Z"), ZoneOffset.UTC);
		UniteOrganisation unite = new UniteOrganisation();
		unite.setCode("DTECH-SW");
		unite.setLibelle("Département Software");
		unite.setActif(true);
		uniteRepository.save(unite);

		ParametresCache cache = mock(ParametresCache.class);
		ParametresCache.Instantane ref = mock(ParametresCache.Instantane.class);
		when(ref.liste(any())).thenReturn(List.of());
		when(cache.instantane()).thenReturn(ref);
		ObjectProvider<CollaborateurCompteDemandePublisher> provider = mock(ObjectProvider.class);
		SoldeCongeService lectureSoldes = mock(SoldeCongeService.class);
		when(lectureSoldes.acquisGroupes(any())).thenReturn(Map.of());
		when(lectureSoldes.reservesGroupes(any())).thenReturn(Map.of());
		AuteurParametreResolver auteurResolver = mock(AuteurParametreResolver.class);
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("DRH01").build();
		when(auteurResolver.depuis(jwt)).thenReturn(new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
		CollaborateurConnecteService connecte = mock(CollaborateurConnecteService.class);
		when(connecte.resoudre(any())).thenReturn(Optional.empty());
		ImportService service = new ImportService(analyseRepository, contenuRepository, ligneRepository,
				new ImportCollaborateursService(collaborateurRepository, uniteRepository, cache, mock(ParametreService.class), provider, horloge),
				new ImportSoldesService(collaborateurRepository, lectureSoldes, mock(RhNotificationPublisher.class),
						new SoldeLotService(jdbc, horloge), horloge),
				mock(IdentiteInterneClient.class), new ImportVerrou(jdbc), auteurResolver, connecte, JsonMapper.builder().build(),
				horloge, tm, 30);

		// ── 5 000 créations de fiches ──
		StringBuilder csv = new StringBuilder("Mat.;Prénom;NOM;E-mail;Code unité\n");
		for (int i = 0; i < LIGNES; i++) {
			csv.append("PERF-").append(i).append(";Prenom").append(i).append(";Nom").append(i).append(";p").append(i)
					.append("@rh-evenement.test;DTECH-SW\n");
		}
		long t0 = System.nanoTime();
		Analyse a = service.analyserCollaborateurs(new MockMultipartFile("fichier", "agents.csv", "text/csv",
				csv.toString().getBytes(StandardCharsets.UTF_8)), false, jwt);
		Duration analyse = Duration.ofNanos(System.nanoTime() - t0);
		assertThat(a.totaux().creations()).isEqualTo(LIGNES);
		long t1 = System.nanoTime();
		CompteRendu r = new TransactionTemplate(tm).execute(s -> service.confirmer(a.analyseId(), null, jwt));
		Duration confirmation = Duration.ofNanos(System.nanoTime() - t1);
		System.out.println("PERF fiches : analyse " + analyse.toMillis() + " ms, confirmation " + confirmation.toMillis() + " ms");
		assertThat(r.creees()).isEqualTo(LIGNES);
		assertThat(collaborateurRepository.count()).isEqualTo(LIGNES);
		assertThat(ligneRepository.count()).isEqualTo(LIGNES);
		assertThat(analyse).isLessThan(BORNE);
		assertThat(confirmation).isLessThan(BORNE);

		// ── 5 000 ajustements de soldes (la moitié des soldes existe déjà) ──
		List<UUID> ids = collaborateurRepository.findAll().stream().map(Collaborateur::getId).toList();
		for (int i = 0; i < ids.size(); i += 2) {
			jdbc.update("insert into rh_solde_conge (collaborateur_id, acquis, version, modifie_le) values (?, 4, 3, current_timestamp)",
					ids.get(i));
		}
		StringBuilder soldes = new StringBuilder("N° matricule;Solde au 30/11;Observation\n");
		for (int i = 0; i < LIGNES; i++) {
			soldes.append("PERF-").append(i).append(";").append(i % 2 == 0 ? "10,5" : "7").append(";\n");
		}
		long t2 = System.nanoTime();
		Analyse sa = service.analyserSoldes(new MockMultipartFile("fichier", "soldes.csv", "text/csv",
				soldes.toString().getBytes(StandardCharsets.UTF_8)), "Reprise des soldes au 30/11/2026", jwt);
		Duration analyseSoldes = Duration.ofNanos(System.nanoTime() - t2);
		long t3 = System.nanoTime();
		CompteRendu sr = new TransactionTemplate(tm).execute(s -> service.confirmer(sa.analyseId(), null, jwt));
		Duration confirmationSoldes = Duration.ofNanos(System.nanoTime() - t3);
		System.out.println("PERF soldes : analyse " + analyseSoldes.toMillis() + " ms, confirmation " + confirmationSoldes.toMillis() + " ms");
		assertThat(sr.ajustements()).isEqualTo(LIGNES);
		assertThat(jdbc.queryForObject("select count(*) from rh_mouvement_solde_conge", Long.class)).isEqualTo(LIGNES);
		// invariant : acquis = somme des mouvements (+ 4 déjà présents sans mouvement pour la moitié) ; version incrémentée
		assertThat(jdbc.queryForObject("select count(*) from rh_solde_conge where acquis in (10.5, 7)", Long.class)).isEqualTo(LIGNES);
		assertThat(jdbc.queryForObject("select count(*) from rh_solde_conge where version = 4", Long.class)).isEqualTo(LIGNES / 2);
		assertThat(jdbc.queryForObject("select count(*) from rh_solde_conge where version = 1", Long.class)).isEqualTo(LIGNES / 2);
		assertThat(analyseSoldes).isLessThan(BORNE);
		assertThat(confirmationSoldes).isLessThan(BORNE);
		List<BigDecimal> echantillon = jdbc.queryForList("select nombre from rh_mouvement_solde_conge order by nombre limit 3", BigDecimal.class);
		assertThat(echantillon).isNotEmpty();
	}
}
