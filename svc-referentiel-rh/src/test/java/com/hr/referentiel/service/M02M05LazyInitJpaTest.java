package com.hr.referentiel.service;

import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.dto.DemandeDocumentAdministratifCreationRequest;
import com.hr.referentiel.dto.DemandeDocumentAdministratifRhResponse;
import com.hr.referentiel.dto.DemandeFormationCreationRequest;
import com.hr.referentiel.dto.DemandeFormationRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.FormationWorkflowHistory;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie sur une vraie session Hibernate ({@code open-in-view=false}, {@link Propagation#NOT_SUPPORTED})
 * que {@link DemandeDocumentAdministratifRhService#soumettre} (M02) et {@link DemandeFormationRhService#creer} (M05)
 * matérialisent la réponse sans {@code LazyInitializationException} sur {@code demandeur.unite}, {@code uniteCible}
 * et {@code collaborateursCibles}, en mode {@code HERITE} comme en mode {@code MOTEUR}.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:m02m05lazy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(M02M05LazyInitJpaTest.Config.class)
@ExtendWith(AvecSocieteContexteExtension.class)
class M02M05LazyInitJpaTest {

	@Configuration
	@EnableJpaRepositories(
			basePackageClasses = DemandeDocumentAdministratifRhRepository.class,
			excludeFilters = @ComponentScan.Filter(
					type = FilterType.REGEX,
					pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(DemandeDocumentAdministratifRhRepository|DemandeFormationRhRepository|FormationWorkflowHistoryRepository|CollaborateurRepository|UniteOrganisationRepository)$).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					UniteOrganisation.class.getName(),
					Collaborateur.class.getName(),
					DemandeDocumentAdministratifRh.class.getName(),
					DemandeFormationRh.class.getName(),
					FormationWorkflowHistory.class.getName());
		}
	}

	@Autowired
	private PlatformTransactionManager txManager;
	@Autowired
	private UniteOrganisationRepository uniteRepository;
	@Autowired
	private CollaborateurRepository collaborateurRepository;
	@Autowired
	private DemandeDocumentAdministratifRhRepository documentRepository;
	@Autowired
	private DemandeFormationRhRepository formationRepository;
	@Autowired
	private FormationWorkflowHistoryRepository formationHistoryRepository;

	private ParametreService parametreService;
	private RhNotificationPublisher notificationPublisher;
	private NumeroLisibleService numeroLisibleService;
	private WorkflowActivationService activationService;
	private WorkflowDemarrageService demarrageService;
	private WorkflowMoteurClient moteurClient;
	private WorkflowVariablesBuilder variablesBuilder;

	private DemandeDocumentAdministratifRhService documentService;
	private DemandeFormationRhService formationService;

	private UniteOrganisation departement;
	private UniteOrganisation equipe;
	private Collaborateur demandeurCollab;
	private Collaborateur chefDept;
	private Collaborateur ro;
	private Collaborateur cible;
	private Jwt jwtCollab;
	private Jwt jwtChef;
	private Jwt jwtRo;

	@BeforeEach
	void setUp() {
		TransactionTemplate tx = new TransactionTemplate(txManager);
		tx.executeWithoutResult(status -> {
			formationHistoryRepository.deleteAll();
			formationRepository.deleteAll();
			documentRepository.deleteAll();
			collaborateurRepository.deleteAll();
			uniteRepository.deleteAll();
		});

		tx.executeWithoutResult(status -> {
			departement = new UniteOrganisation();
			departement.setCode("DEP-OPS-" + UUID.randomUUID().toString().substring(0, 6));
			departement.setLibelle("Direction Opérations");
			departement.setActif(true);
			departement = uniteRepository.save(departement);

			equipe = new UniteOrganisation();
			equipe.setCode("EQ-LOG-" + UUID.randomUUID().toString().substring(0, 6));
			equipe.setLibelle("Équipe Logistique");
			equipe.setParent(departement);
			equipe.setActif(true);
			equipe = uniteRepository.save(equipe);

			demandeurCollab = creerCollaborateur("MAT-DOC-1", "Sonia", "Mrad", "COLLABORATEUR", equipe);
			chefDept = creerCollaborateur("MAT-CHEF-1", "Hatem", "Khelifi", "RESPONSABLE", departement);
			ro = creerCollaborateur("MAT-RO-1", "Karim", "Zouari", "RO", equipe);
			cible = creerCollaborateur("MAT-CIBLE-1", "Sami", "Haddad", "COLLABORATEUR", equipe);
		});

		jwtCollab = jwtPour(demandeurCollab.getCompteUtilisateurId());
		jwtChef = jwtPour(chefDept.getCompteUtilisateurId());
		jwtRo = jwtPour(ro.getCompteUtilisateurId());

		parametreService = Mockito.mock(ParametreService.class);
		notificationPublisher = Mockito.mock(RhNotificationPublisher.class);
		numeroLisibleService = Mockito.mock(NumeroLisibleService.class);
		activationService = Mockito.mock(WorkflowActivationService.class);
		demarrageService = Mockito.mock(WorkflowDemarrageService.class);
		moteurClient = Mockito.mock(WorkflowMoteurClient.class);
		variablesBuilder = Mockito.mock(WorkflowVariablesBuilder.class);

		when(parametreService.lire("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL")).thenReturn(Optional.of(
				new ParametreVue("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL", null,
						"Attestation de travail", null, "NEUTRE", 1, true, true, 48, false, false)));
		when(parametreService.libelleOuDefaut("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL"))
				.thenReturn("Attestation de travail");
		when(parametreService.libelleOuInconnu(eq("STATUT_DOCUMENT"), any()))
				.thenAnswer(inv -> inv.getArgument(1));
		when(parametreService.libelleOuInconnu(eq("STATUT_FORMATION"), any()))
				.thenAnswer(inv -> inv.getArgument(1));
		when(numeroLisibleService.attribuer(any(), any()))
				.thenAnswer(inv -> "NUM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

		CollaborateurConnecteService connecteService = new CollaborateurConnecteService(collaborateurRepository);
		documentService = new DemandeDocumentAdministratifRhService(
				documentRepository, connecteService, parametreService, notificationPublisher, numeroLisibleService,
				activationService, demarrageService, moteurClient, variablesBuilder, txManager);
		formationService = new DemandeFormationRhService(
				formationRepository, collaborateurRepository, uniteRepository, connecteService,
				notificationPublisher, formationHistoryRepository, numeroLisibleService, parametreService,
				activationService, demarrageService, moteurClient, variablesBuilder, txManager);
	}

	@Test
	void soumettreDocumentEnModeHerite_sansOpenInView_neLevePasLazyInitializationException() {
		when(activationService.mode("DOCUMENT")).thenReturn(WorkflowActivationService.MODE_HERITE);
		DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
		req.setTypeDocument("ATTESTATION_TRAVAIL");
		req.setCommentaireDemandeur("Pour dossier bancaire");

		DemandeDocumentAdministratifRhResponse rep = documentService.soumettre(req, jwtCollab);

		assertThat(rep.getIdentifiant()).isNotNull();
		assertThat(rep.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		assertThat(rep.getWorkflowMode()).isEqualTo(WorkflowActivationService.MODE_HERITE);
		assertThat(rep.getDemandeurUnite()).isEqualTo("Équipe Logistique");
		assertThat(rep.getRangDansFile()).isEqualTo(1);
		assertThat(rep.isEstProchaineFifo()).isTrue();
	}

	@Test
	void soumettreDocumentEnModeHerite_dixSoumissionsSuccessives_neSeCompteJamaisElleMeme() {
		when(activationService.mode("DOCUMENT")).thenReturn(WorkflowActivationService.MODE_HERITE);
		TransactionTemplate tx = new TransactionTemplate(txManager);

		for (int i = 0; i < 10; i++) {
			tx.executeWithoutResult(status -> documentRepository.deleteAll());
			DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
			req.setTypeDocument("ATTESTATION_TRAVAIL");
			req.setCommentaireDemandeur("Soumission #" + i);

			DemandeDocumentAdministratifRhResponse rep = documentService.soumettre(req, jwtCollab);

			assertThat(rep.getCreeLe().getNano() % 1_000).isZero();
			assertThat(rep.getRangDansFile()).isEqualTo(1);
			assertThat(rep.isEstProchaineFifo()).isTrue();
		}
	}

	@Test
	void soumettreDocumentEnModeMoteur_rechargeAvecFetchUnite_neLevePasLazyInitializationException() {
		when(activationService.mode("DOCUMENT")).thenReturn(WorkflowActivationService.MODE_MOTEUR);
		DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
		req.setTypeDocument("ATTESTATION_TRAVAIL");

		DemandeDocumentAdministratifRhResponse rep = documentService.soumettre(req, jwtCollab);

		assertThat(rep.getIdentifiant()).isNotNull();
		assertThat(rep.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_COURS);
		assertThat(rep.getWorkflowMode()).isEqualTo(WorkflowActivationService.MODE_MOTEUR);
		assertThat(rep.getDemandeurUnite()).isEqualTo("Équipe Logistique");
		verify(demarrageService).demarrerDocument(rep.getIdentifiant());
	}

	@Test
	void creerFormationEnModeHerite_sansOpenInView_neLevePasLazyInitializationException() {
		when(activationService.mode("FORMATION")).thenReturn(WorkflowActivationService.MODE_HERITE);
		DemandeFormationCreationRequest req = nouvelleRequeteFormation();
		req.setUniteCibleIdentifiant(equipe.getId());

		DemandeFormationRhResponse rep = formationService.creer(req, jwtChef);

		assertThat(rep.getIdentifiant()).isNotNull();
		assertThat(rep.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_VALIDATION_RRH);
		assertThat(rep.getWorkflowMode()).isEqualTo(WorkflowActivationService.MODE_HERITE);
		assertThat(rep.getDemandeurUnite()).isEqualTo("Direction Opérations");
		assertThat(rep.getUniteCibleIdentifiant()).isEqualTo(equipe.getId());
		assertThat(rep.getUniteCibleLibelle()).isEqualTo("Équipe Logistique");
	}

	@Test
	void creerFormationEnModeMoteur_rechargeAvecEntityGraph_neLevePasLazyInitializationException() {
		when(activationService.mode("FORMATION")).thenReturn(WorkflowActivationService.MODE_MOTEUR);
		DemandeFormationCreationRequest req = nouvelleRequeteFormation();
		req.setCollaborateursCiblesIdentifiants(Set.of(cible.getId()));

		DemandeFormationRhResponse rep = formationService.creer(req, jwtRo);

		assertThat(rep.getIdentifiant()).isNotNull();
		assertThat(rep.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_COURS);
		assertThat(rep.getWorkflowMode()).isEqualTo(WorkflowActivationService.MODE_MOTEUR);
		assertThat(rep.getDemandeurUnite()).isEqualTo("Équipe Logistique");
		assertThat(rep.getCollaborateursCibles()).hasSize(1);
		assertThat(rep.getCollaborateursCibles().get(0).identifiant()).isEqualTo(cible.getId());
		verify(demarrageService).demarrerFormation(rep.getIdentifiant());
	}

	private Collaborateur creerCollaborateur(
			String matricule, String prenom, String nom, String profilAcces, UniteOrganisation unite) {
		Collaborateur c = new Collaborateur();
		c.setMatricule(matricule + "-" + UUID.randomUUID().toString().substring(0, 6));
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut("ACTIF");
		c.setProfilAcces(profilAcces);
		c.setUnite(unite);
		c.setCompteUtilisateurId(UUID.randomUUID());
		return collaborateurRepository.save(c);
	}

	private static Jwt jwtPour(UUID compteUtilisateurId) {
		return Jwt.withTokenValue("tok-" + compteUtilisateurId)
				.header("alg", "none")
				.claim("identifiant_utilisateur", compteUtilisateurId.toString())
				.build();
	}

	private static DemandeFormationCreationRequest nouvelleRequeteFormation() {
		DemandeFormationCreationRequest req = new DemandeFormationCreationRequest();
		req.setTypeFormation("Sécurité incendie");
		req.setOrganisme("CNFCPP");
		req.setDureeHeures(16);
		req.setCoutEstime(new BigDecimal("1200.00"));
		req.setObjectifsPedagogiques("Habilitation");
		req.setJustification("Obligation réglementaire");
		req.setDateSouhaiteeDebut(LocalDate.of(2026, 12, 1));
		req.setDateSouhaiteeFin(LocalDate.of(2026, 12, 2));
		return req;
	}
}
