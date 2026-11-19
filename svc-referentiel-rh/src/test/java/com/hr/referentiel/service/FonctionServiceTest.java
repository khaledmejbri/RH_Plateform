package com.hr.referentiel.service;

import com.hr.referentiel.dto.FonctionDtos.AffectationRequest;
import com.hr.referentiel.dto.FonctionDtos.AffectationResponse;
import com.hr.referentiel.dto.FonctionDtos.FonctionRequest;
import com.hr.referentiel.dto.FonctionDtos.FonctionResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import com.hr.referentiel.web.FonctionController;
import com.hr.referentiel.web.ReferentielMetierException;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link FonctionService} et {@link FonctionAffectationService} (US-05, CA-05.1 à CA-05.3).
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:fonctions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(FonctionServiceTest.Config.class)
@ExtendWith(AvecSocieteContexteExtension.class)
class FonctionServiceTest {

	@Configuration
	@EnableJpaRepositories(
			basePackageClasses = FonctionRepository.class,
			excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(Fonction|Collaborateur|UniteOrganisation)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					Fonction.class.getName(),
					FonctionAffectation.class.getName(),
					Collaborateur.class.getName(),
					UniteOrganisation.class.getName());
		}
	}

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);

	@Autowired
	FonctionRepository fonctionRepo;
	@Autowired
	FonctionAffectationRepository affectationRepo;
	@Autowired
	CollaborateurRepository collaborateurRepo;
	@Autowired
	UniteOrganisationRepository uniteRepo;

	private FonctionService fonctionService;
	private FonctionAffectationService affectationService;

	@BeforeEach
	void setUp() {
		affectationRepo.deleteAll();
		fonctionRepo.deleteAll();
		collaborateurRepo.deleteAll();
		uniteRepo.deleteAll();
		fonctionService = new FonctionService(fonctionRepo, affectationRepo, collaborateurRepo, uniteRepo, HORLOGE);
		affectationService = new FonctionAffectationService(fonctionRepo, affectationRepo, collaborateurRepo, uniteRepo, HORLOGE);
	}

	@Test
	@DisplayName("CA-05.1 : création de fonction avec code généré immuable et unicité insensible à la casse/accents (409 LIBELLE_EN_DOUBLE)")
	void creationEtUniciteLibelle() {
		FonctionResponse f = fonctionService.creer(new FonctionRequest("Référent Sécurité", "Responsable HSE", null));
		assertThat(f.code()).isEqualTo("REFERENT_SECURITE");
		assertThat(f.actif()).isTrue();

		assertThatThrownBy(() -> fonctionService.creer(new FonctionRequest("  referent   securite ", null, null)))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException ex = (ReferentielMetierException) e;
					assertThat(ex.getHttpStatus()).isEqualTo(409);
					assertThat(ex.getCode()).isEqualTo("LIBELLE_EN_DOUBLE");
				});

		// Modification du libellé conserve le code initial immuable
		FonctionResponse maj = fonctionService.modifier(f.id(), new FonctionRequest("Référent HSE", "Mise à jour", f.version()));
		assertThat(maj.code()).isEqualTo("REFERENT_SECURITE");
		assertThat(maj.libelle()).isEqualTo("Référent HSE");
	}

	@Test
	@DisplayName("CA-05.2 & CA-05.3 : affectation avec périmètre et dates, refus chevauchement (409), refus suppression si utilisée (409 PARAMETRE_UTILISE)")
	void affectationChevauchementEtSuppression() {
		FonctionResponse f = fonctionService.creer(new FonctionRequest("Approbateur Achat", null, null));
		UniteOrganisation u = new UniteOrganisation();
		u.setCode("DIR-IT");
		u.setLibelle("Direction IT");
		u.setTypeNoeud("DIRECTION");
		u.setCreeLe(Instant.now(HORLOGE));
		u = uniteRepo.saveAndFlush(u);

		Collaborateur c = new Collaborateur();
		c.setMatricule("COL-01");
		c.setPrenom("Sami");
		c.setNom("Ben Ali");
		c.setStatut("ACTIF");
		c.setUnite(u);
		c.setCreeLe(Instant.now(HORLOGE));
		c = collaborateurRepo.saveAndFlush(c);

		AffectationResponse aff = affectationService.affecter(f.id(), new AffectationRequest(
				c.getId(), u.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
		assertThat(aff.unitePerimetreId()).isEqualTo(u.getId());

		// Chevauchement sur même collaborateur + même unité -> 409 AFFECTATION_CHEVAUCHEMENT
		UUID collabId = c.getId();
		UUID uniteId = u.getId();
		assertThatThrownBy(() -> affectationService.affecter(f.id(), new AffectationRequest(
				collabId, uniteId, LocalDate.of(2026, 6, 1), null)))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("AFFECTATION_CHEVAUCHEMENT"));

		// Suppression d'une fonction affectée/utilisée -> 409 PARAMETRE_UTILISE
		assertThatThrownBy(() -> fonctionService.supprimer(f.id()))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException ex = (ReferentielMetierException) e;
					assertThat(ex.getHttpStatus()).isEqualTo(409);
					assertThat(ex.getCode()).isEqualTo("PARAMETRE_UTILISE");
				});

		// Fonction désactivée refuse toute nouvelle affectation (422 FONCTION_INACTIVE)
		fonctionService.changerActivation(f.id(), false);
		assertThatThrownBy(() -> affectationService.affecter(f.id(), new AffectationRequest(
				collabId, null, LocalDate.of(2027, 1, 1), null)))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("FONCTION_INACTIVE"));
	}

	@Test
	@DisplayName("Sécurité FonctionController : DIRECTION a accès en lecture mais pas en écriture")
	void securiteControleur() throws Exception {
		PreAuthorize creerAuth = FonctionController.class
				.getMethod("creerFonction", FonctionRequest.class)
				.getAnnotation(PreAuthorize.class);
		PreAuthorize listerAuth = FonctionController.class
				.getMethod("listerFonctions", boolean.class)
				.getAnnotation(PreAuthorize.class);
		assertThat(listerAuth.value()).isEqualTo(PreAuthorizeExpressions.BACKOFFICE_LECTURE);
		assertThat(creerAuth.value()).isEqualTo(PreAuthorizeExpressions.BACKOFFICE_ECRITURE);
	}
}
