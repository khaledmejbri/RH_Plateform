package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.dto.FonctionDtos.FonctionRequest;
import com.hr.referentiel.dto.FonctionDtos.FonctionResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.JourFerie;
import com.hr.referentiel.entity.ParametreJournal;
import com.hr.referentiel.entity.ParametreValeur;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.entity.PlainteRh;
import com.hr.referentiel.entity.SocieteParametres;
import com.hr.referentiel.entity.SocieteParametresJournal;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.repository.ParametreJournalRepository;
import com.hr.referentiel.repository.ParametreValeurRepository;
import com.hr.referentiel.repository.PlainteRhRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.repository.SocieteParametresJournalRepository;
import com.hr.referentiel.repository.SocieteParametresRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
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
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests d'isolation multi-société de {@code svc-referentiel-rh} (§ 9.14, § 10.3, CA-01.3) :
 * <ul>
 *   <li>même matricule, même code unité, même date de jour férié, même code fonction autorisés dans deux sociétés A et B ;</li>
 *   <li>toute liste depuis B ne retourne aucune donnée de A (liste vide ou uniquement B) ;</li>
 *   <li>tout accès par identifiant d'une ressource de A depuis B retourne vide / 404 (jamais 403).</li>
 * </ul>
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:isolation_w1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(IsolationMultiSocieteReferentielTest.Config.class)
class IsolationMultiSocieteReferentielTest {

	@Configuration
	@EnableJpaRepositories(
			basePackageClasses = CollaborateurRepository.class,
			excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(Collaborateur|UniteOrganisation|JourFerie|Fonction|SocieteParametres|ReglagesConges|AcquisitionMois)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					Collaborateur.class.getName(),
					UniteOrganisation.class.getName(),
					JourFerie.class.getName(),
					Fonction.class.getName(),
					FonctionAffectation.class.getName(),
					SocieteParametres.class.getName(),
					SocieteParametresJournal.class.getName(),
					ParametresConges.class.getName(),
					com.hr.referentiel.entity.AcquisitionMois.class.getName());
		}
	}

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);

	@Autowired
	CollaborateurRepository collaborateurRepo;
	@Autowired
	UniteOrganisationRepository uniteRepo;
	@Autowired
	JourFerieRepository jourFerieRepo;
	@Autowired
	FonctionRepository fonctionRepo;
	@Autowired
	FonctionAffectationRepository affectationRepo;
	@Autowired
	SocieteParametresRepository societeParametresRepo;
	@Autowired
	SocieteParametresJournalRepository societeParametresJournalRepo;
	@Autowired
	ReglagesCongesRepository reglagesRepo;
	@Autowired
	AcquisitionMoisRepository acquisitionMoisRepo;

	@Test
	@DisplayName("CA-01.3 & § 9.14 : cloisonnement complet entre Société A et Société B (listes vides, 404 inter-société, unicités par société)")
	void cloisonnementCompletEntreDeuxSocietes() {
		FonctionService fonctionService = new FonctionService(
				fonctionRepo, affectationRepo, collaborateurRepo, uniteRepo, HORLOGE);
		SocieteParametresService paramsService = new SocieteParametresService(
				societeParametresRepo, societeParametresJournalRepo,
				new ReglagesCongeService(reglagesRepo, acquisitionMoisRepo, mock(JournalParametres.class), HORLOGE),
				JsonMapper.builder().build(), HORLOGE);

		// 1. Créer des données dans la Société A
		UniteOrganisation uniteA = SocieteContexte.executer(SOC_A, () -> {
			UniteOrganisation u = new UniteOrganisation();
			u.setCode("DIR-GEN");
			u.setLibelle("Direction A");
			u.setTypeNoeud("DIRECTION_GENERALE");
			u.setCreeLe(Instant.now(HORLOGE));
			return uniteRepo.saveAndFlush(u);
		});
		UUID uniteAId = uniteA.getId();
		UUID collabAId = SocieteContexte.executer(SOC_A, () -> {
			Collaborateur c = new Collaborateur();
			c.setMatricule("COL-WEB");
			c.setPrenom("Sonia");
			c.setNom("Société A");
			c.setStatut("ACTIF");
			c.setUnite(uniteA);
			c.setCreeLe(Instant.now(HORLOGE));
			return collaborateurRepo.saveAndFlush(c).getId();
		});
		UUID ferieAId = SocieteContexte.executer(SOC_A, () -> {
			JourFerie jf = new JourFerie();
			jf.setDateJour(LocalDate.of(2026, 3, 20));
			jf.setLibelle("Fête Indépendance A");
			jf.setActif(true);
			jf.setCreeLe(Instant.now(HORLOGE));
			return jourFerieRepo.saveAndFlush(jf).getId();
		});
		FonctionResponse fonctionA = SocieteContexte.executer(SOC_A, () ->
				fonctionService.creer(new FonctionRequest("Référent Qualité", "Qualité A", null)));
		SocieteContexte.executer(SOC_A, paramsService::obtenir);

		// 2. Depuis la Société B (encore vide) : toutes les listes sont vides et tous les ID de A sont invisibles (404 / empty)
		SocieteContexte.executer(SOC_B, () -> {
			assertThat(uniteRepo.findAll()).isEmpty();
			assertThat(collaborateurRepo.findAll()).isEmpty();
			assertThat(jourFerieRepo.findAll()).isEmpty();
			assertThat(fonctionService.lister(false)).isEmpty();

			assertThat(uniteRepo.findById(uniteAId)).isEmpty();
			assertThat(collaborateurRepo.findById(collabAId)).isEmpty();
			assertThat(jourFerieRepo.findById(ferieAId)).isEmpty();

			assertThatThrownBy(() -> fonctionService.obtenir(fonctionA.id()))
					.isInstanceOf(ReferentielMetierException.class)
					.satisfies(e -> assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(404));
		});

		// 3. Créer les MÊMES codes / matricules / dates dans la Société B : aucune collision avec A
		SocieteContexte.executer(SOC_B, () -> {
			UniteOrganisation uB = new UniteOrganisation();
			uB.setCode("DIR-GEN");
			uB.setLibelle("Direction B");
			uB.setTypeNoeud("DIRECTION_GENERALE");
			uB.setCreeLe(Instant.now(HORLOGE));
			uB = uniteRepo.saveAndFlush(uB);

			Collaborateur cB = new Collaborateur();
			cB.setMatricule("COL-WEB");
			cB.setPrenom("Karim");
			cB.setNom("Société B");
			cB.setStatut("ACTIF");
			cB.setUnite(uB);
			cB.setCreeLe(Instant.now(HORLOGE));
			collaborateurRepo.saveAndFlush(cB);

			JourFerie jfB = new JourFerie();
			jfB.setDateJour(LocalDate.of(2026, 3, 20));
			jfB.setLibelle("Fête Indépendance B");
			jfB.setActif(true);
			jfB.setCreeLe(Instant.now(HORLOGE));
			jourFerieRepo.saveAndFlush(jfB);

			FonctionResponse fonctionB = fonctionService.creer(new FonctionRequest("Référent Qualité", "Qualité B", null));
			assertThat(fonctionB.code()).isEqualTo("REFERENT_QUALITE");
			assertThat(fonctionB.id()).isNotEqualTo(fonctionA.id());

			assertThat(collaborateurRepo.findAll()).hasSize(1)
					.first().extracting(Collaborateur::getNom).isEqualTo("Société B");
		});
	}
}
