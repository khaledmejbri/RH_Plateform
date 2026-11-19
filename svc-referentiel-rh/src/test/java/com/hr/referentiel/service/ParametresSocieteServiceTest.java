package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.domain.BaseDecompteConge;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresUpdateRequest;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.entity.SocieteParametres;
import com.hr.referentiel.entity.SocieteParametresJournal;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.repository.SocieteParametresJournalRepository;
import com.hr.referentiel.repository.SocieteParametresRepository;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import com.hr.referentiel.web.ReferentielMetierException;
import com.hr.referentiel.web.SocieteParametresController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests de {@link SocieteParametresService} (US-04, CA-04.1 à CA-04.5, D-SOC04 à D-SOC13).
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:socparams;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ParametresSocieteServiceTest.Config.class)
@ExtendWith(AvecSocieteContexteExtension.class)
class ParametresSocieteServiceTest {

	@Configuration
	@EnableJpaRepositories(
			basePackageClasses = SocieteParametresRepository.class,
			excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(SocieteParametres|ReglagesConges|AcquisitionMois)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					SocieteParametres.class.getName(),
					SocieteParametresJournal.class.getName(),
					ParametresConges.class.getName(),
					com.hr.referentiel.entity.AcquisitionMois.class.getName());
		}
	}

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);

	@Autowired
	SocieteParametresRepository repository;
	@Autowired
	SocieteParametresJournalRepository journalRepository;
	@Autowired
	ReglagesCongesRepository reglagesRepository;
	@Autowired
	AcquisitionMoisRepository acquisitionMoisRepository;

	private SocieteParametresService service;

	@BeforeEach
	void setUp() {
		journalRepository.deleteAll();
		repository.deleteAll();
		reglagesRepository.deleteAll();
		ReglagesCongeService reglagesService = new ReglagesCongeService(
				reglagesRepository, acquisitionMoisRepository, mock(JournalParametres.class), HORLOGE);
		service = new SocieteParametresService(
				repository, journalRepository, reglagesService, JsonMapper.builder().build(), HORLOGE);
	}

	@Test
	@DisplayName("CA-04.1 : valeurs par défaut créées automatiquement par société")
	void valeursParDefaut() {
		SocieteParametresResponse r = service.obtenir();
		assertThat(r.societeId()).isEqualTo(AvecSocieteContexteExtension.SOCIETE_TEST);
		assertThat(r.joursTravailles()).containsExactly("LUN", "MAR", "MER", "JEU", "VEN");
		assertThat(r.dureeHebdoMinutes()).isEqualTo(2400);
		assertThat(r.dureeJourMinutes()).containsEntry("LUN", 480).containsEntry("VEN", 480);
		assertThat(r.heureDebutJournee()).isEqualTo("08:00");
		assertThat(r.heureFinJournee()).isEqualTo("17:00");
		assertThat(r.baseDecompteConge()).isEqualTo(BaseDecompteConge.JOURS_TRAVAILLES);
		assertThat(r.dureeMaxSortieMinutes()).isEqualTo(120);
		assertThat(r.delegationAutoConges()).isTrue();
		assertThat(r.publicationQuatreYeux()).isFalse();
	}

	@Test
	@DisplayName("CA-04.2 : somme des durées journalières != durée hebdomadaire -> 400 REPARTITION_INCOHERENTE")
	void repartitionIncoherente400() {
		service.obtenir();
		SocieteParametresUpdateRequest req = new SocieteParametresUpdateRequest(
				List.of("LUN", "MAR", "MER", "JEU", "VEN"),
				2400,
				Map.of("LUN", 480, "MAR", 480, "MER", 480, "JEU", 480, "VEN", 400),
				"08:00", "17:00",
				BaseDecompteConge.JOURS_TRAVAILLES,
				null, 120, true, false, 0L);

		assertThatThrownBy(() -> service.modifier(req, new AuteurParametre(UUID.randomUUID(), "DRH01")))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException ex = (ReferentielMetierException) e;
					assertThat(ex.getHttpStatus()).isEqualTo(400);
					assertThat(ex.getCode()).isEqualTo("REPARTITION_INCOHERENTE");
				});
	}

	@Test
	@DisplayName("CA-04.4 & CA-04.5 : modification valide journalisée et version périmée -> 409 PARAMETRE_MODIFIE_ENTRE_TEMPS")
	void modificationJournaliseeEtConflitVersion409() {
		SocieteParametresResponse initial = service.obtenir();
		AuteurParametre auteur = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");

		SocieteParametresUpdateRequest req = new SocieteParametresUpdateRequest(
				List.of("LUN", "MAR", "MER", "JEU", "VEN", "SAM"),
				2640,
				Map.of("LUN", 480, "MAR", 480, "MER", 480, "JEU", 480, "VEN", 480, "SAM", 240),
				"08:30", "17:30",
				BaseDecompteConge.JOURS_OUVRABLES,
				new BigDecimal("2.0"),
				90, false, true, initial.version());

		SocieteParametresResponse maj = service.modifier(req, auteur);
		assertThat(maj.dureeHebdoMinutes()).isEqualTo(2640);
		assertThat(maj.baseDecompteConge()).isEqualTo(BaseDecompteConge.JOURS_OUVRABLES);
		assertThat(maj.dureeMaxSortieMinutes()).isEqualTo(90);
		assertThat(maj.version()).isGreaterThan(initial.version());

		var pageJournal = service.journal(PageRequest.of(0, 10));
		assertThat(pageJournal.getTotalElements()).isEqualTo(1);
		assertThat(pageJournal.getContent().get(0).auteurNom()).isEqualTo("Amel Trabelsi");
		assertThat(pageJournal.getContent().get(0).apresJson()).contains("JOURS_OUVRABLES");

		// Rejouer avec l'ancienne version -> 409 PARAMETRE_MODIFIE_ENTRE_TEMPS
		assertThatThrownBy(() -> service.modifier(req, auteur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException ex = (ReferentielMetierException) e;
					assertThat(ex.getHttpStatus()).isEqualTo(409);
					assertThat(ex.getCode()).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS");
				});
	}

	@Test
	@DisplayName("D-SOC08 : duree_max_sortie_minutes dynamique appliquée aux demandes d'autorisation de sortie")
	void dureeMaxSortieDynamique() {
		SocieteParametresResponse initial = service.obtenir();
		service.modifier(new SocieteParametresUpdateRequest(
				List.of("LUN", "MAR", "MER", "JEU", "VEN"),
				2400,
				Map.of("LUN", 480, "MAR", 480, "MER", 480, "JEU", 480, "VEN", 480),
				"08:00", "17:00",
				BaseDecompteConge.JOURS_TRAVAILLES,
				null, 60, true, false, initial.version()),
				AuteurParametre.systeme());

		DemandeAdministrativeValidationService validation =
				new DemandeAdministrativeValidationService(mock(ParametreService.class), service);

		// 60 min accepté
		validation.validerContenu(TypeDemandeAdministrativeRh.AUTORISATION_SORTIE, Map.of(
				"date_jour", "2026-11-20",
				"heure_debut", "10:00",
				"heure_fin", "11:00",
				"motif", "Rendez-vous médical"));

		// 90 min refusé car duree_max_sortie_minutes = 60
		assertThatThrownBy(() -> validation.validerContenu(TypeDemandeAdministrativeRh.AUTORISATION_SORTIE, Map.of(
				"date_jour", "2026-11-20",
				"heure_debut", "10:00",
				"heure_fin", "11:30",
				"motif", "Rendez-vous médical")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("60 minutes");
	}

	@Test
	@DisplayName("Sécurité contrôleur : lecture BACKOFFICE_LECTURE (inclut DIRECTION), écriture BACKOFFICE_ECRITURE (exclut DIRECTION)")
	void securiteControleurAnnotations() throws Exception {
		PreAuthorize getAuth = SocieteParametresController.class.getMethod("obtenir").getAnnotation(PreAuthorize.class);
		PreAuthorize putAuth = SocieteParametresController.class
				.getMethod("modifier", SocieteParametresUpdateRequest.class, org.springframework.security.oauth2.jwt.Jwt.class)
				.getAnnotation(PreAuthorize.class);
		assertThat(getAuth.value()).isEqualTo(PreAuthorizeExpressions.BACKOFFICE_LECTURE);
		assertThat(putAuth.value()).isEqualTo(PreAuthorizeExpressions.BACKOFFICE_ECRITURE);
		assertThat(putAuth.value()).doesNotContain("DIRECTION");
	}
}
