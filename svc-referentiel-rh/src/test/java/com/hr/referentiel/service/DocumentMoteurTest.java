package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.referentiel.client.WorkflowMoteurClient.EtapeCouranteDto;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueComplement;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueDemarrage;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.dto.DemandeComplementRequest;
import com.hr.referentiel.dto.DemandeDocumentAdministratifCreationRequest;
import com.hr.referentiel.dto.DemandeDocumentAdministratifRhResponse;
import com.hr.referentiel.dto.DemandeDocumentDisponibleRequest;
import com.hr.referentiel.dto.DemandeDocumentSuiviResponse;
import com.hr.referentiel.dto.DocumentRejetRhRequest;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.RhWorkflowEffet;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.kafka.WorkflowEvenementsKafkaListener;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import com.hr.referentiel.repository.RhWorkflowEffetRepository;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentMoteurTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-22T10:00:00Z");
	private static final UUID SOCIETE_ID = UUID.randomUUID();

	@Mock
	private DemandeDocumentAdministratifRhRepository docRepo;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private ParametreService parametreService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private NumeroLisibleService numeroLisibleService;
	@Mock
	private WorkflowActivationService activationService;
	@Mock
	private WorkflowMoteurClient moteurClient;
	@Mock
	private CollaborateurRepository collaborateurRepo;
	@Mock
	private DemandeAdministrativeRhRepository demandeAdminRepo;
	@Mock
	private DemandeAdminWorkflowHistoryRepository adminHistoryRepo;
	@Mock
	private RhWorkflowEffetRepository effetRepo;
	@Mock
	private CongeDecompteService congeDecompteService;
	@Mock
	private DemandeFormationRhRepository formationRepo;
	@Mock
	private FormationWorkflowHistoryRepository formationHistoryRepo;
	@Mock
	private SocieteDefautResolver societeDefautResolver;

	private WorkflowVariablesBuilder variablesBuilder;
	private WorkflowEffetService effetService;
	private WorkflowDemarrageService demarrageService;
	private DemandeDocumentAdministratifRhService service;
	private WorkflowEvenementsKafkaListener kafkaListener;

	private Collaborateur demandeur;
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
				docRepo, formationRepo, formationHistoryRepo);
		demarrageService = new WorkflowDemarrageService(
				demandeAdminRepo, collaborateurRepo, adminHistoryRepo, moteurClient,
				variablesBuilder, effetService, notificationPublisher, txManagerAvecFlag(),
				docRepo, formationRepo, formationHistoryRepo, parametreService);
		service = new DemandeDocumentAdministratifRhService(
				docRepo, collaborateurConnecteService, parametreService, notificationPublisher, numeroLisibleService,
				activationService, demarrageService, moteurClient, variablesBuilder, txManagerAvecFlag());

		TransactionTemplate tx = new TransactionTemplate() {
			@Override
			public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
				return action.doInTransaction(null);
			}
		};
		kafkaListener = new WorkflowEvenementsKafkaListener(
				demandeAdminRepo, societeDefautResolver, tx, docRepo, formationRepo);

		demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setCompteUtilisateurId(UUID.randomUUID());
		demandeur.setPrenom("Sonia");
		demandeur.setNom("Mrad");
		demandeur.setMatricule("EMP-042");

		jwtDemandeur = Jwt.withTokenValue("tok")
				.header("alg", "none")
				.claim("identifiant_utilisateur", demandeur.getCompteUtilisateurId().toString())
				.build();

		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);
		lenient().when(collaborateurRepo.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		lenient().when(numeroLisibleService.attribuer(any(), any())).thenReturn("DOC-202611-0001");
		lenient().when(parametreService.lire("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL")).thenReturn(Optional.of(
				new ParametreVue("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL", null,
						"Attestation de travail", null, "NEUTRE", 1, true, true, 48, false, false)));
		lenient().when(parametreService.libelleOuDefaut("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL"))
				.thenReturn("Attestation de travail");
		lenient().when(parametreService.libelleOuInconnu(eq("STATUT_DOCUMENT"), any()))
				.thenAnswer(inv -> inv.getArgument(1));
		lenient().when(docRepo.save(any())).thenAnswer(inv -> {
			DemandeDocumentAdministratifRh d = inv.getArgument(0);
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
	void soumettreEnModeMoteur_enCoursSansNotif_demarreHorsTxAvecSla() {
		when(activationService.mode("DOCUMENT")).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return WorkflowActivationService.MODE_MOTEUR;
		});
		when(docRepo.findFirstByDemandeurIdAndTypeDocumentAndStatutInOrderByCreeLeAsc(any(), any(), any()))
				.thenReturn(Optional.empty());
		UUID instanceId = UUID.randomUUID();
		Instant echeance = MAINTENANT.plusSeconds(172_800);
		when(docRepo.findByIdAvecDemandeur(any())).thenAnswer(inv -> {
			DemandeDocumentAdministratifRh d = creerDocument(inv.getArgument(0), StatutDocumentAdministratifDemandeRh.EN_COURS, "MOTEUR");
			d.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
			return Optional.of(d);
		});
		when(docRepo.findByIdForUpdate(any())).thenAnswer(inv -> {
			DemandeDocumentAdministratifRh d = creerDocument(inv.getArgument(0), StatutDocumentAdministratifDemandeRh.EN_COURS, "MOTEUR");
			d.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
			return Optional.of(d);
		});
		when(moteurClient.demarrer(any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			WorkflowMoteurClient.DemarrerInstanceRequest req = inv.getArgument(1);
			assertThat(req.sousType()).isEqualTo("ATTESTATION_TRAVAIL");
			assertThat(req.variables()).containsEntry("type_document", "ATTESTATION_TRAVAIL");
			assertThat(req.variables()).containsKey("affichage_sla");
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "EN_COURS", null, new EtapeCouranteDto("TRAITEMENT_RH", "Préparer le document", echeance)));
		});

		DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
		req.setTypeDocument("ATTESTATION_TRAVAIL");

		DemandeDocumentAdministratifRhResponse rep = service.soumettre(req, jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_COURS);
		assertThat(rep.getWorkflowMode()).isEqualTo("MOTEUR");
		verifyNoInteractions(notificationPublisher);
	}

	@Test
	void fileAttenteEtRetard_exclutMoteurDeFifoEtExclutAnnuleeDuRetard() {
		DemandeDocumentAdministratifRh herite = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, "HERITE");
		DemandeDocumentAdministratifRh moteur = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, "MOTEUR");
		when(docRepo.findByStatutInOrderByCreeLeAsc(DemandeDocumentAdministratifRhService.STATUTS_FILE_FIFO))
				.thenReturn(List.of(herite, moteur));

		List<DemandeDocumentAdministratifRhResponse> file = service.fileAttentePourRh(null);
		assertThat(file).extracting(DemandeDocumentAdministratifRhResponse::getIdentifiant)
				.containsExactly(herite.getId());

		// Statuts ouverts incluent EN_COURS et A_COMPLETER pour D-F06
		assertThat(DemandeDocumentAdministratifRhService.STATUTS_OUVERTS).containsExactlyInAnyOrder(
				StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE,
				StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH,
				StatutDocumentAdministratifDemandeRh.EN_COURS,
				StatutDocumentAdministratifDemandeRh.A_COMPLETER);

		// ANNULEE échue dans le passé → enRetard = false dans obtenir et suivi
		DemandeDocumentAdministratifRh annulee = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.ANNULEE, "MOTEUR");
		annulee.setDateEcheanceTraitement(MAINTENANT.minusSeconds(3600));
		when(docRepo.findById(annulee.getId())).thenReturn(Optional.of(annulee));

		assertThat(service.obtenir(annulee.getId(), jwtDemandeur, false).isEnRetard()).isFalse();
		DemandeDocumentSuiviResponse suivi = service.suivi(annulee.getId(), jwtDemandeur, false);
		assertThat(suivi.isEnRetard()).isFalse();
		assertThat(suivi.getEtapes()).anyMatch(e -> "CLOTURE".equals(e.getCode()) && e.isTerminee());
	}

	@Test
	void anciennesRoutesSurMoteur_renvoient409DemandeSousWorkflow() {
		DemandeDocumentAdministratifRh d = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.EN_COURS, "MOTEUR");
		when(docRepo.findById(d.getId())).thenReturn(Optional.of(d));

		DemandeDocumentDisponibleRequest dispReq = new DemandeDocumentDisponibleRequest();
		dispReq.setReferenceLivrable("s3://docs/a.pdf");
		assertThatThrownBy(() -> service.marquerDisponible(d.getId(), dispReq, jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException r = (ReferentielMetierException) ex;
					assertThat(r.getHttpStatus()).isEqualTo(409);
					assertThat(r.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
				});

		DocumentRejetRhRequest rejReq = new DocumentRejetRhRequest();
		rejReq.setMotif("Invalide");
		assertThatThrownBy(() -> service.rejeter(d.getId(), rejReq, jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> assertThat(((ReferentielMetierException) ex).getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW"));
	}

	@Test
	void effetsMoteurEtComplementEtKafkaProjection() {
		DemandeDocumentAdministratifRh d = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.EN_COURS, "MOTEUR");
		UUID instanceId = UUID.randomUUID();
		d.setWorkflowInstanceId(instanceId);
		when(docRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
		when(docRepo.findByIdAvecDemandeur(d.getId())).thenReturn(Optional.of(d));

		// METTRE_A_COMPLETER enregistre le commentaire valideur dans commentaireRh
		effetService.appliquer(new RequeteEffet("doc-1", "APPLIQUER", "DOCUMENT", d.getId(),
				"METTRE_A_COMPLETER", new ActeurEffet(UUID.randomUUID(), null, "Amel RH", null),
				"Précisez la langue souhaitée", instanceId, "Préparer le document"));
		assertThat(d.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.A_COMPLETER);
		assertThat(d.getCommentaireRh()).isEqualTo("Précisez la langue souhaitée");

		// Autre collaborateur → 403 sur /complement
		Collaborateur autre = new Collaborateur();
		autre.setId(UUID.randomUUID());
		Jwt jwtAutre = Jwt.withTokenValue("tok2").header("alg", "none").claim("sub", "autre").build();
		when(collaborateurConnecteService.exigerCollaborateur(jwtAutre)).thenReturn(autre);
		assertThatThrownBy(() -> service.fournirComplement(
				d.getId(), new DemandeComplementRequest("Version française SVP", null), jwtAutre))
				.isInstanceOf(AccessDeniedException.class);

		// Demandeur → complementFourni hors tx puis EN_COURS + majVariables
		when(moteurClient.complementFourni(eq(instanceId), any(), eq("Version française SVP")))
				.thenAnswer(inv -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return IssueComplement.OK;
				});
		DemandeDocumentAdministratifRhResponse repComp = service.fournirComplement(
				d.getId(), new DemandeComplementRequest("Version française SVP", null), jwtDemandeur);
		assertThat(repComp.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_COURS);
		verify(moteurClient).majVariables(eq(instanceId), any());

		// Kafka projette etape/echeance sans toucher statut
		when(societeDefautResolver.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(SOCIETE_ID));
		String json = "{\"objet_type\":\"DOCUMENT\",\"objet_id\":\"" + d.getId() + "\","
				+ "\"instance_id\":\"" + instanceId + "\",\"statut_instance\":\"EN_COURS\","
				+ "\"etape_libelle\":\"Préparer le document\",\"echeance_le\":\"2026-11-24T10:00:00Z\","
				+ "\"emis_le\":\"2026-11-22T12:00:00Z\"}";
		kafkaListener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 0, d.getId().toString(), json));
		assertThat(d.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_COURS);
		assertThat(d.getWorkflowEtapeLibelle()).isEqualTo("Préparer le document");

		// APPROUVER → DISPONIBLE ; ANNULER impossible après décision (409)
		effetService.appliquer(new RequeteEffet("doc-2", "APPLIQUER", "DOCUMENT", d.getId(),
				"APPROUVER", new ActeurEffet(UUID.randomUUID(), null, "Amel RH", null),
				"Prêt à l'accueil", instanceId, null));
		assertThat(d.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.DISPONIBLE);

		// Repli HERITE sur WorkflowAbsent remet EN_ATTENTE_FILE et notifie
		DemandeDocumentAdministratifRh dRepli = creerDocument(
				UUID.randomUUID(), StatutDocumentAdministratifDemandeRh.EN_COURS, "MOTEUR");
		dRepli.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
		when(docRepo.findByIdAvecDemandeur(dRepli.getId())).thenReturn(Optional.of(dRepli));
		when(docRepo.findByIdForUpdate(dRepli.getId())).thenReturn(Optional.of(dRepli));
		when(moteurClient.demarrer(eq("DOCUMENT:" + dRepli.getId()), any())).thenReturn(new IssueDemarrage.WorkflowAbsent());

		SocieteContexte.executer(SOCIETE_ID, () -> demarrageService.demarrerDocument(dRepli.getId()));
		assertThat(dRepli.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(dRepli.getStatut()).isEqualTo(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		verify(notificationPublisher).notifierDemandeDocumentRecue(eq(demandeur), any(), eq(dRepli.getId()), any());
	}

	private DemandeDocumentAdministratifRh creerDocument(
			UUID id, StatutDocumentAdministratifDemandeRh statut, String mode) {
		DemandeDocumentAdministratifRh d = new DemandeDocumentAdministratifRh();
		d.setId(id);
		d.setNumero("DOC-202611-0001");
		d.setDemandeur(demandeur);
		d.setTypeDocument("ATTESTATION_TRAVAIL");
		d.setStatut(statut);
		d.setWorkflowMode(mode);
		d.setDelaiSlaHeures(48);
		d.setDateEcheanceTraitement(MAINTENANT.plusSeconds(172_800));
		d.setCreeLe(MAINTENANT);
		return d;
	}
}
