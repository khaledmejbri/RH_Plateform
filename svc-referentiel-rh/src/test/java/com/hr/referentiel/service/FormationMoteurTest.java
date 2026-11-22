package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.referentiel.client.WorkflowMoteurClient.EtapeCouranteDto;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueComplement;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueDemarrage;
import com.hr.referentiel.domain.ActionWorkflowFormation;
import com.hr.referentiel.domain.CibleDemandeFormationRh;
import com.hr.referentiel.domain.OrigineDemandeFormationRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.dto.DemandeComplementRequest;
import com.hr.referentiel.dto.DemandeFormationCreationRequest;
import com.hr.referentiel.dto.DemandeFormationIntegrationRequest;
import com.hr.referentiel.dto.DemandeFormationRhResponse;
import com.hr.referentiel.dto.DemandeRefusRequest;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.FormationWorkflowHistory;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.kafka.WorkflowEvenementsKafkaListener;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import com.hr.referentiel.repository.RhWorkflowEffetRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormationMoteurTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-22T10:00:00Z");
	private static final UUID SOCIETE_ID = UUID.randomUUID();

	@Mock
	private DemandeFormationRhRepository formationRepo;
	@Mock
	private CollaborateurRepository collaborateurRepo;
	@Mock
	private UniteOrganisationRepository uniteRepo;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private FormationWorkflowHistoryRepository historyRepo;
	@Mock
	private NumeroLisibleService numeroLisibleService;
	@Mock
	private ParametreService parametreService;
	@Mock
	private WorkflowActivationService activationService;
	@Mock
	private WorkflowMoteurClient moteurClient;
	@Mock
	private DemandeAdministrativeRhRepository demandeAdminRepo;
	@Mock
	private DemandeAdminWorkflowHistoryRepository adminHistoryRepo;
	@Mock
	private RhWorkflowEffetRepository effetRepo;
	@Mock
	private CongeDecompteService congeDecompteService;
	@Mock
	private DemandeDocumentAdministratifRhRepository docRepo;
	@Mock
	private SocieteDefautResolver societeDefautResolver;

	private WorkflowVariablesBuilder variablesBuilder;
	private WorkflowEffetService effetService;
	private WorkflowDemarrageService demarrageService;
	private DemandeFormationRhService service;
	private WorkflowEvenementsKafkaListener kafkaListener;

	private Collaborateur demandeurRo;
	private Collaborateur cible;
	private UniteOrganisation unite;
	private Jwt jwtDemandeur;

	private static PlatformTransactionManager txManagerAvecFlag() {
		return new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definition) {
				TransactionSynchronizationManager.setActualTransactionActive(true);
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}

			@Override
			public void rollback(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}
		};
	}

	@BeforeEach
	void setUp() {
		variablesBuilder = new WorkflowVariablesBuilder(
				mock(SoldeCongeService.class), parametreService, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
		effetService = new WorkflowEffetService(
				demandeAdminRepo, adminHistoryRepo, effetRepo, congeDecompteService,
				docRepo, formationRepo, historyRepo);
		demarrageService = new WorkflowDemarrageService(
				demandeAdminRepo, collaborateurRepo, adminHistoryRepo, moteurClient,
				variablesBuilder, effetService, notificationPublisher, txManagerAvecFlag(),
				docRepo, formationRepo, historyRepo, parametreService);
		service = new DemandeFormationRhService(
				formationRepo, collaborateurRepo, uniteRepo, collaborateurConnecteService,
				notificationPublisher, historyRepo, numeroLisibleService, parametreService,
				activationService, demarrageService, moteurClient, variablesBuilder, txManagerAvecFlag());

		TransactionTemplate tx = new TransactionTemplate() {
			@Override
			public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
				return action.doInTransaction(null);
			}
		};
		kafkaListener = new WorkflowEvenementsKafkaListener(
				demandeAdminRepo, societeDefautResolver, tx, docRepo, formationRepo);

		unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setLibelle("Logistique");

		demandeurRo = new Collaborateur();
		demandeurRo.setId(UUID.randomUUID());
		demandeurRo.setCompteUtilisateurId(UUID.randomUUID());
		demandeurRo.setPrenom("Karim");
		demandeurRo.setNom("Zouari");
		demandeurRo.setProfilAcces("RO");
		demandeurRo.setStatut("ACTIF");
		demandeurRo.setUnite(unite);

		cible = new Collaborateur();
		cible.setId(UUID.randomUUID());
		cible.setPrenom("Sami");
		cible.setNom("Haddad");
		cible.setStatut("ACTIF");
		cible.setUnite(unite);

		jwtDemandeur = Jwt.withTokenValue("tok")
				.header("alg", "none")
				.claim("identifiant_utilisateur", demandeurRo.getCompteUtilisateurId().toString())
				.build();

		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeurRo);
		lenient().when(collaborateurRepo.findDetailById(demandeurRo.getId())).thenReturn(Optional.of(demandeurRo));
		lenient().when(collaborateurRepo.findAllById(any())).thenReturn(List.of(cible));
		lenient().when(numeroLisibleService.attribuer(any(), any())).thenReturn("FOR-202611-0001");
		lenient().when(parametreService.libelleOuInconnu(eq("STATUT_FORMATION"), any()))
				.thenAnswer(inv -> inv.getArgument(1));
		lenient().when(formationRepo.save(any())).thenAnswer(inv -> {
			DemandeFormationRh d = inv.getArgument(0);
			if (d.getId() == null) d.setId(UUID.randomUUID());
			return d;
		});
		lenient().when(effetRepo.findById(any())).thenReturn(Optional.empty());
	}

	@AfterEach
	void clear() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void creerEnModeMoteur_enCoursSansNotifEtDemarreAvecVariablesQ5EtBeneficiaires() {
		when(activationService.mode("FORMATION")).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return WorkflowActivationService.MODE_MOTEUR;
		});
		UUID instanceId = UUID.randomUUID();
		when(formationRepo.findByIdAvecDemandeur(any())).thenAnswer(inv -> {
			DemandeFormationRh f = creerFormation(inv.getArgument(0), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
			f.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
			return Optional.of(f);
		});
		when(formationRepo.findByIdForUpdate(any())).thenAnswer(inv -> {
			DemandeFormationRh f = creerFormation(inv.getArgument(0), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
			f.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
			return Optional.of(f);
		});
		when(moteurClient.demarrer(any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			WorkflowMoteurClient.DemarrerInstanceRequest req = inv.getArgument(1);
			assertThat(req.beneficiaires()).containsExactly(cible.getId());
			assertThat(req.variables()).containsEntry("origine", "RESPONSABLE_OPERATIONNEL");
			assertThat(req.variables()).containsEntry("duree_jours", 3); // 01/12..03/12 inclusifs
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "EN_COURS", null, new EtapeCouranteDto("VALID_RESP", "Validation responsable", null)));
		});

		DemandeFormationCreationRequest req = new DemandeFormationCreationRequest();
		req.setTypeFormation("Sécurité incendie");
		req.setOrganisme("CNFCPP");
		req.setDureeHeures(21);
		req.setCoutEstime(new BigDecimal("1800.00"));
		req.setObjectifsPedagogiques("Habilitation");
		req.setJustification("Renouvellement réglementaire");
		req.setDateSouhaiteeDebut(LocalDate.of(2026, 12, 1));
		req.setDateSouhaiteeFin(LocalDate.of(2026, 12, 3));
		req.setCollaborateursCiblesIdentifiants(Set.of(cible.getId()));

		DemandeFormationRhResponse rep = service.creer(req, jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_COURS);
		assertThat(rep.getWorkflowMode()).isEqualTo("MOTEUR");
		verifyNoInteractions(notificationPublisher);

		// Sans dates : duree_jours = ceil(duree_heures / 8.0) (Q5)
		DemandeFormationRh sansDates = creerFormation(UUID.randomUUID(), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
		sansDates.setDateSouhaiteeDebut(null);
		sansDates.setDateSouhaiteeFin(null);
		sansDates.setDureeHeures(10);
		assertThat(variablesBuilder.construireFormation(sansDates)).containsEntry("duree_jours", 2);
	}

	@Test
	void anciennesRoutesSurMoteur_renvoient409DemandeSousWorkflow() {
		DemandeFormationRh f = creerFormation(UUID.randomUUID(), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
		when(formationRepo.findDetailById(f.getId())).thenReturn(Optional.of(f));

		assertThatThrownBy(() -> service.integrerPlan(f.getId(), new DemandeFormationIntegrationRequest(), jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException r = (ReferentielMetierException) ex;
					assertThat(r.getHttpStatus()).isEqualTo(409);
					assertThat(r.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
				});

		DemandeRefusRequest ref = new DemandeRefusRequest();
		ref.setMotifRefus("Hors budget");
		assertThatThrownBy(() -> service.refuser(f.getId(), ref, jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> assertThat(((ReferentielMetierException) ex).getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW"));
	}

	@Test
	void annuler_conserve422HeritePourNonDemandeur_etAppelleMoteurHorsTx() {
		DemandeFormationRh f = creerFormation(UUID.randomUUID(), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
		UUID instanceId = UUID.randomUUID();
		f.setWorkflowInstanceId(instanceId);
		when(formationRepo.findDetailById(f.getId())).thenReturn(Optional.of(f));
		when(formationRepo.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));

		// Non demandeur → IllegalArgumentException (422)
		Collaborateur autre = new Collaborateur();
		autre.setId(UUID.randomUUID());
		Jwt jwtAutre = Jwt.withTokenValue("tok-a").header("alg", "none").claim("sub", "autre-a").build();
		when(collaborateurConnecteService.exigerCollaborateur(jwtAutre)).thenReturn(autre);
		assertThatThrownBy(() -> service.annuler(f.getId(), jwtAutre))
				.isInstanceOf(IllegalArgumentException.class);

		// Demandeur → appelle moteur hors tx et passe ANNULEE + historique
		when(moteurClient.annuler(eq(instanceId), any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return IssueAnnulation.ANNULEE;
		});
		DemandeFormationRhResponse rep = service.annuler(f.getId(), jwtDemandeur);
		assertThat(rep.getStatut()).isEqualTo(StatutDemandeFormationRh.ANNULEE);
		verify(historyRepo).save(argThat((FormationWorkflowHistory h) ->
				h.getAction() == ActionWorkflowFormation.ANNULATION_DEMANDEUR));
	}

	@Test
	void effetsMoteur_complement_kafka_etRepliHerite() {
		DemandeFormationRh f = creerFormation(UUID.randomUUID(), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
		UUID instanceId = UUID.randomUUID();
		f.setWorkflowInstanceId(instanceId);
		when(formationRepo.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));
		when(formationRepo.findByIdAvecDemandeur(f.getId())).thenReturn(Optional.of(f));
		when(formationRepo.findDetailById(f.getId())).thenReturn(Optional.of(f));

		// METTRE_A_COMPLETER → A_COMPLETER + historique DEMANDE_COMPLEMENT avec commentaire valideur
		effetService.appliquer(new RequeteEffet("for-1", "APPLIQUER", "FORMATION", f.getId(),
				"METTRE_A_COMPLETER", new ActeurEffet(UUID.randomUUID(), null, "Amel RH", null),
				"Joindre le devis de l'organisme", instanceId, "Validation RH"));
		assertThat(f.getStatut()).isEqualTo(StatutDemandeFormationRh.A_COMPLETER);
		assertThat(f.getCommentaireRh()).isEqualTo("Joindre le devis de l'organisme");
		verify(historyRepo).save(argThat((FormationWorkflowHistory h) ->
				h.getAction() == ActionWorkflowFormation.DEMANDE_COMPLEMENT
						&& "Joindre le devis de l'organisme".equals(h.getCommentaire())));

		// Autre utilisateur → 403 sur /complement
		Collaborateur autre = new Collaborateur();
		autre.setId(UUID.randomUUID());
		Jwt jwtAutre = Jwt.withTokenValue("tok-b").header("alg", "none").claim("sub", "autre-b").build();
		when(collaborateurConnecteService.exigerCollaborateur(jwtAutre)).thenReturn(autre);
		assertThatThrownBy(() -> service.fournirComplement(
				f.getId(), new DemandeComplementRequest("Devis ajouté au dossier", null), jwtAutre))
				.isInstanceOf(AccessDeniedException.class);

		// Demandeur → /complement → EN_COURS + COMPLEMENT_FOURNI + majVariables
		when(moteurClient.complementFourni(eq(instanceId), any(), eq("Devis ajouté au dossier")))
				.thenAnswer(inv -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return IssueComplement.OK;
				});
		DemandeFormationRhResponse repComp = service.fournirComplement(
				f.getId(), new DemandeComplementRequest("Devis ajouté au dossier", null), jwtDemandeur);
		assertThat(repComp.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_COURS);
		verify(historyRepo).save(argThat((FormationWorkflowHistory h) ->
				h.getAction() == ActionWorkflowFormation.COMPLEMENT_FOURNI
						&& "Devis ajouté au dossier".equals(h.getCommentaire())));
		verify(moteurClient).majVariables(eq(instanceId), any());

		// Kafka projette etape/echeance sans toucher statut
		when(societeDefautResolver.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(SOCIETE_ID));
		String json = "{\"objet_type\":\"FORMATION\",\"objet_id\":\"" + f.getId() + "\","
				+ "\"instance_id\":\"" + instanceId + "\",\"statut_instance\":\"EN_COURS\","
				+ "\"etape_libelle\":\"Validation RH\",\"emis_le\":\"2026-11-22T12:00:00Z\"}";
		kafkaListener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 0, f.getId().toString(), json));
		assertThat(f.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_COURS);
		assertThat(f.getWorkflowEtapeLibelle()).isEqualTo("Validation RH");

		// APPROUVER → INTEGREE_PLAN + INTEGRATION_PLAN
		effetService.appliquer(new RequeteEffet("for-2", "APPLIQUER", "FORMATION", f.getId(),
				"APPROUVER", new ActeurEffet(UUID.randomUUID(), null, "Amel RH", null),
				"Budget validé", instanceId, null));
		assertThat(f.getStatut()).isEqualTo(StatutDemandeFormationRh.INTEGREE_PLAN);

		// Repli HERITE remet EN_VALIDATION_RRH + historique + notif
		DemandeFormationRh fRepli = creerFormation(UUID.randomUUID(), StatutDemandeFormationRh.EN_COURS, "MOTEUR");
		fRepli.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
		when(formationRepo.findByIdAvecDemandeur(fRepli.getId())).thenReturn(Optional.of(fRepli));
		when(formationRepo.findByIdForUpdate(fRepli.getId())).thenReturn(Optional.of(fRepli));
		when(moteurClient.demarrer(eq("FORMATION:" + fRepli.getId()), any())).thenReturn(new IssueDemarrage.ModeHerite());

		SocieteContexte.executer(SOCIETE_ID, () -> demarrageService.demarrerFormation(fRepli.getId()));
		assertThat(fRepli.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(fRepli.getStatut()).isEqualTo(StatutDemandeFormationRh.EN_VALIDATION_RRH);
		verify(notificationPublisher).notifierDemandeFormationSoumise(eq(demandeurRo), any(), eq(fRepli.getId()), any());
	}

	private DemandeFormationRh creerFormation(UUID id, StatutDemandeFormationRh statut, String mode) {
		DemandeFormationRh d = new DemandeFormationRh();
		d.setId(id);
		d.setNumero("FOR-202611-0001");
		d.setDemandeur(demandeurRo);
		d.setOrigine(OrigineDemandeFormationRh.RESPONSABLE_OPERATIONNEL);
		d.setCible(CibleDemandeFormationRh.COLLABORATEURS);
		d.setCollaborateursCibles(new LinkedHashSet<>(Set.of(cible.getId())));
		d.setTypeFormation("Sécurité incendie");
		d.setOrganisme("CNFCPP");
		d.setDureeHeures(21);
		d.setCoutEstime(new BigDecimal("1800.00"));
		d.setObjectifsPedagogiques("Habilitation");
		d.setJustification("Renouvellement réglementaire");
		d.setDateSouhaiteeDebut(LocalDate.of(2026, 12, 1));
		d.setDateSouhaiteeFin(LocalDate.of(2026, 12, 3));
		d.setStatut(statut);
		d.setWorkflowMode(mode);
		d.setCreeLe(MAINTENANT);
		return d;
	}
}
