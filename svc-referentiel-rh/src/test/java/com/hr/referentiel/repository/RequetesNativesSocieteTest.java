package com.hr.referentiel.repository;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.entity.AcquisitionMois;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.NumeroSequence;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.entity.UniteOrganisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des requêtes SQL natives multi-société (§ 9.2.2, § 10.3) :
 * {@link AcquisitionMoisRepository}, {@link ReglagesCongesRepository}, {@link SoldeCongeRepository}.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:natives_soc;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RequetesNativesSocieteTest.Config.class)
class RequetesNativesSocieteTest {

	@Configuration
	@EnableJpaRepositories(
			basePackageClasses = SoldeCongeRepository.class,
			excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(AcquisitionMois|ReglagesConges|SoldeConge|Collaborateur|UniteOrganisation)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					AcquisitionMois.class.getName(),
					ParametresConges.class.getName(),
					SoldeConge.class.getName(),
					Collaborateur.class.getName(),
					UniteOrganisation.class.getName(),
					NumeroSequence.class.getName());
		}
	}

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	AcquisitionMoisRepository acquisitionMoisRepository;
	@Autowired
	ReglagesCongesRepository reglagesRepository;
	@Autowired
	SoldeCongeRepository soldeRepository;
	@Autowired
	CollaborateurRepository collaborateurRepository;
	@Autowired
	UniteOrganisationRepository uniteRepository;

	@BeforeEach
	void alignerClePrimaireComposeeH2() {
		jdbc.execute("ALTER TABLE rh_acquisition_mois DROP PRIMARY KEY");
		jdbc.execute("ALTER TABLE rh_acquisition_mois ADD PRIMARY KEY (societe_id, mois)");
	}

	@Test
	@DisplayName("Requêtes natives d'upsert (rh_acquisition_mois, rh_parametres, rh_solde_conge) isolées par societe_id")
	void upsertsNatifsIsolesParSociete() {
		// 1. AcquisitionMois : figer le même mois "2026-11" avec deux taux différents dans SOC_A et SOC_B
		SocieteContexte.executer(SOC_A, () ->
				acquisitionMoisRepository.figerSiAbsent(SOC_A, "2026-11", new BigDecimal("1.5")));
		SocieteContexte.executer(SOC_B, () ->
				acquisitionMoisRepository.figerSiAbsent(SOC_B, "2026-11", new BigDecimal("2.5")));

		BigDecimal tauxA = SocieteContexte.executer(SOC_A, () ->
				acquisitionMoisRepository.findById("2026-11").orElseThrow().getJoursParMois());
		BigDecimal tauxB = SocieteContexte.executer(SOC_B, () ->
				acquisitionMoisRepository.findById("2026-11").orElseThrow().getJoursParMois());
		assertThat(tauxA).isEqualByComparingTo("1.5");
		assertThat(tauxB).isEqualByComparingTo("2.5");

		// 2. ReglagesConges : creerSiAbsente uniquement sur la société ciblée
		SocieteContexte.executer(SOC_A, () ->
				reglagesRepository.creerSiAbsente(SOC_A, new BigDecimal("1.5"), LocalDate.of(2026, 12, 1)));
		SocieteContexte.executer(SOC_B, () ->
				reglagesRepository.creerSiAbsente(SOC_B, new BigDecimal("2.0"), LocalDate.of(2027, 1, 1)));

		LocalDate debutA = SocieteContexte.executer(SOC_A, () ->
				reglagesRepository.findFirstByOrderByIdAsc().orElseThrow().getDebutAcquisitionsLe());
		LocalDate debutB = SocieteContexte.executer(SOC_B, () ->
				reglagesRepository.findFirstByOrderByIdAsc().orElseThrow().getDebutAcquisitionsLe());
		assertThat(debutA).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(debutB).isEqualTo(LocalDate.of(2027, 1, 1));

		// 3. SoldeConge : creerSiAbsent insère societe_id et n'est visible que dans sa société
		UUID collabA = SocieteContexte.executer(SOC_A, () -> {
			UniteOrganisation u = new UniteOrganisation();
			u.setCode("U-A");
			u.setLibelle("Unité A");
			u.setTypeNoeud("SERVICE");
			u.setCreeLe(Instant.now());
			u = uniteRepository.saveAndFlush(u);

			Collaborateur c = new Collaborateur();
			c.setMatricule("COL-01");
			c.setPrenom("Ali");
			c.setNom("A");
			c.setStatut("ACTIF");
			c.setUnite(u);
			c.setCreeLe(Instant.now());
			return collaborateurRepository.saveAndFlush(c).getId();
		});
		SocieteContexte.executer(SOC_A, () -> soldeRepository.creerSiAbsent(SOC_A, collabA));

		assertThat(SocieteContexte.executer(SOC_A, () -> soldeRepository.findById(collabA))).isPresent();
		assertThat(SocieteContexte.executer(SOC_B, () -> soldeRepository.findById(collabA))).isEmpty();
	}
}
