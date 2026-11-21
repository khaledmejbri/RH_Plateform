package com.hr.referentiel.service;

import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueComplement;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest;
import com.hr.referentiel.dto.DemandeAdministrativeRhResponse;
import com.hr.referentiel.dto.DemandeComplementRequest;
import com.hr.referentiel.dto.DemandeRefusRequest;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandeSousWorkflowTest {

	@Mock
	private DemandeAdministrativeRhRepository demandeRepo;
	@Mock
	private CollaborateurRepository collaborateurRepo;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private DemandeAdministrativeValidationService validationService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private DemandeAdminWorkflowHistoryRepository historyRepo;
	@Mock
	private NumeroLisibleService numeroLisibleService;
	@Mock
	private ParametreService parametreService;
	@Mock
	private CongeDecompteService congeDecompteService;
	@Mock
	private AnnulationCongeService annulationCongeService;
	@Mock
	private ChampsDecideurSolde champsDecideurSolde;
	@Mock
	private AuteurParametreResolver auteurResolver;
	@Mock
	private WorkflowActivationService workflowActivationService;
	@Mock
	private WorkflowDemarrageService workflowDemarrageService;
	@Mock
	private WorkflowMoteurClient moteurClient;
	@Mock
	private WorkflowVariablesBuilder variablesBuilder;

	private DemandeAdministrativeRhService service;
	private Collaborateur demandeur;
	private Collaborateur ro;
	private Collaborateur tiers;
	private Collaborateur rh;
	private DemandeAdministrativeRh demande;
	private Jwt jwtDemandeur;
	private Jwt jwtRo;
	private Jwt jwtTiers;
	private Jwt jwtRh;

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

	private static Jwt jwt(UUID compteId) {
		return Jwt.withTokenValue("tok-" + compteId)
				.header("alg", "none")
				.subject(compteId.toString())
				.claim("identifiant_utilisateur", compteId.toString())
				.build();
	}

	@BeforeEach
	void setUp() {
		service = new DemandeAdministrativeRhService(
				demandeRepo, collaborateurRepo, collaborateurConnecteService, validationService,
				notificationPublisher, historyRepo, numeroLisibleService, parametreService,
				congeDecompteService, annulationCongeService, champsDecideurSolde, auteurResolver,
				workflowActivationService, workflowDemarrageService, moteurClient, variablesBuilder,
				txManagerAvecFlag());

		ro = new Collaborateur();
		ro.setId(UUID.randomUUID());
		ro.setCompteUtilisateurId(UUID.randomUUID());
		ro.setPrenom("Omar");
		ro.setNom("Chef");
		ro.setStatut("ACTIF");

		UniteOrganisation unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setManager(ro);

		demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setCompteUtilisateurId(UUID.randomUUID());
		demandeur.setPrenom("Leila");
		demandeur.setNom("Collab");
		demandeur.setStatut("ACTIF");
		demandeur.setUnite(unite);

		tiers = new Collaborateur();
		tiers.setId(UUID.randomUUID());
		tiers.setCompteUtilisateurId(UUID.randomUUID());
		tiers.setPrenom("Tiers");
		tiers.setNom("Autre");
		tiers.setStatut("ACTIF");

		rh = new Collaborateur();
		rh.setId(UUID.randomUUID());
		rh.setCompteUtilisateurId(UUID.randomUUID());
		rh.setPrenom("Amel");
		rh.setNom("RH");
		rh.setStatut("ACTIF");

		demande = new DemandeAdministrativeRh();
		demande.setId(UUID.randomUUID());
		demande.setNumero("DEM-202611-0099");
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setDemandeur(demandeur);
		demande.setStatut(StatutDemandeAdministrativeRh.EN_COURS);
		demande.setWorkflowMode("MOTEUR");
		demande.setWorkflowEtat("DEMARRE");
		demande.setWorkflowInstanceId(UUID.randomUUID());
		demande.setWorkflowEtapeLibelle("Validation responsable");
		demande.setWorkflowEcheanceLe(Instant.parse("2026-12-02T17:00:00Z"));
		demande.setContenu(new HashMap<>(Map.of("type_conge", "ANNUEL", "pieces_jointes", List.of("doc1.pdf"))));

		jwtDemandeur = jwt(demandeur.getCompteUtilisateurId());
		jwtRo = jwt(ro.getCompteUtilisateurId());
		jwtTiers = jwt(tiers.getCompteUtilisateurId());
		jwtRh = jwt(rh.getCompteUtilisateurId());

		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);
		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwtRo)).thenReturn(ro);
		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwtTiers)).thenReturn(tiers);
		lenient().when(collaborateurConnecteService.resoudre(jwtRh)).thenReturn(Optional.of(rh));
		lenient().when(collaborateurConnecteService.resoudre(jwtDemandeur)).thenReturn(Optional.of(demandeur));
		lenient().when(collaborateurRepo.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		lenient().when(demandeRepo.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(demandeRepo.findByIdAvecDemandeur(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(demandeRepo.findById(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(parametreService.libelleOuDefaut(any(), any())).thenReturn("Libellé");
	}

	@Test
	void quatreAnciennesRoutesRenvoient409DemandeSousWorkflowAvecMessageExact() {
		DemandeRefusRequest refus = new DemandeRefusRequest();
		refus.setMotifRefus("Motif de refus valide");

		assertThatThrownBy(() -> service.validerSuperieur(demande.getId(), jwtRo))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> verifier409SousWorkflow((ReferentielMetierException) ex));

		assertThatThrownBy(() -> service.refuserSuperieur(demande.getId(), jwtRo, refus))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> verifier409SousWorkflow((ReferentielMetierException) ex));

		assertThatThrownBy(() -> service.validerRrh(demande.getId(), jwtRh))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> verifier409SousWorkflow((ReferentielMetierException) ex));

		assertThatThrownBy(() -> service.refuserRrh(demande.getId(), jwtRh, refus))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> verifier409SousWorkflow((ReferentielMetierException) ex));
	}

	private static void verifier409SousWorkflow(ReferentielMetierException ex) {
		assertThat(ex.getHttpStatus()).isEqualTo(409);
		assertThat(ex.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
		assertThat(ex.getMessage()).isEqualTo("Cette demande se traite depuis Mes tâches.");
	}

	@Test
	void tiersGardeSon403AvantLe409SurAnciennesRoutes() {
		assertThatThrownBy(() -> service.validerSuperieur(demande.getId(), jwtTiers))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.validerRrh(demande.getId(), jwtDemandeur))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void annulationMoteurNominalHorsTransactionAnnuleEtNeNotifiePas() {
		when(moteurClient.annuler(eq(demande.getWorkflowInstanceId()), eq("Annulée par le demandeur"), any()))
				.thenAnswer(inv -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return IssueAnnulation.ANNULEE;
				});

		DemandeAdministrativeRhResponse rep = service.annuler(demande.getId(), jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.ANNULEE);
		verify(notificationPublisher, never()).notifierAnnulationDemandeur(any(), any(), any(), any(), any());
	}

	@Test
	void annulationMoteurInstanceTermineeRenvoie409DejaDecidee() {
		when(moteurClient.annuler(any(), any(), any())).thenReturn(IssueAnnulation.INSTANCE_TERMINEE);

		assertThatThrownBy(() -> service.annuler(demande.getId(), jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException rme = (ReferentielMetierException) ex;
					assertThat(rme.getHttpStatus()).isEqualTo(409);
					assertThat(rme.getCode()).isEqualTo("DEMANDE_DEJA_DECIDEE");
				});
	}

	@Test
	void annulationMoteurIndisponibleRenvoie503SansModifierLaDemande() {
		when(moteurClient.annuler(any(), any(), any())).thenReturn(IssueAnnulation.INDISPONIBLE);

		assertThatThrownBy(() -> service.annuler(demande.getId(), jwtDemandeur))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException rme = (ReferentielMetierException) ex;
					assertThat(rme.getHttpStatus()).isEqualTo(503);
					assertThat(rme.getCode()).isEqualTo("SERVICE_INDISPONIBLE");
				});
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
	}

	@Test
	void annulationMoteurADemarrerAnnuleLocalementSansAppelerLeMoteur() {
		demande.setWorkflowEtat("A_DEMARRER");
		demande.setWorkflowInstanceId(null);

		DemandeAdministrativeRhResponse rep = service.annuler(demande.getId(), jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.ANNULEE);
		verify(moteurClient, never()).annuler(any(), any(), any());
	}

	@Test
	void complementFourniPasseEnCoursAjoutePiecesJointesEtMajVariablesHorsTx() {
		demande.setStatut(StatutDemandeAdministrativeRh.A_COMPLETER);
		when(moteurClient.complementFourni(eq(demande.getWorkflowInstanceId()), any(), eq("Voici le justificatif demandé")))
				.thenAnswer(inv -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return IssueComplement.OK;
				});
		when(variablesBuilder.construire(demande)).thenReturn(Map.of("piece_jointe_fournie", true));
		when(moteurClient.majVariables(eq(demande.getWorkflowInstanceId()), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return true;
		});

		DemandeAdministrativeRhResponse rep = service.fournirComplement(
				demande.getId(),
				new DemandeComplementRequest("Voici le justificatif demandé", List.of("justif2.pdf")),
				jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
		assertThat(demande.getContenu().get("pieces_jointes"))
				.isEqualTo(List.of("doc1.pdf", "justif2.pdf"));
		verify(moteurClient).majVariables(eq(demande.getWorkflowInstanceId()), any());
	}

	@Test
	void complementFourniNeReecritPasStatutSiDejaApprouveePendantReactivation() {
		demande.setStatut(StatutDemandeAdministrativeRh.A_COMPLETER);
		when(moteurClient.complementFourni(eq(demande.getWorkflowInstanceId()), any(), any()))
				.thenAnswer(inv -> {
					// Simule un effet APPROUVER déclenché par le moteur pendant complement-fourni (D-W5-12)
					demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
					return IssueComplement.OK;
				});
		when(variablesBuilder.construire(demande)).thenReturn(Map.of("piece_jointe_fournie", true));

		DemandeAdministrativeRhResponse rep = service.fournirComplement(
				demande.getId(),
				new DemandeComplementRequest("Complément envoyé", List.of("pj.pdf")),
				jwtDemandeur);

		assertThat(rep.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.APPROUVEE);
	}

	@Test
	void enAttenteRoExclutMoteurEtReponsesEnrichiesExposentChampsWorkflow() {
		when(demandeRepo.findEnAttenteRoHerite(ro.getId(), StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR))
				.thenReturn(List.of());

		assertThat(service.demandesEnAttenteRo(jwtRo)).isEmpty();
		verify(demandeRepo).findEnAttenteRoHerite(ro.getId(), StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);

		DemandeAdministrativeRhResponse rep = service.obtenir(demande.getId(), jwtDemandeur, false);
		assertThat(rep.getWorkflowMode()).isEqualTo("MOTEUR");
		assertThat(rep.getWorkflowInstanceId()).isEqualTo(demande.getWorkflowInstanceId());
		assertThat(rep.getEtapeCouranteLibelle()).isEqualTo("Validation responsable");
		assertThat(rep.getEcheanceLe()).isEqualTo(Instant.parse("2026-12-02T17:00:00Z"));
	}

	// ─── D-W5-04 : création en mode MOTEUR ────────────────────────────────────

	private static DemandeAdministrativeRhCreationRequest requeteConge() {
		DemandeAdministrativeRhCreationRequest req = new DemandeAdministrativeRhCreationRequest();
		req.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		req.setContenu(new HashMap<>(Map.of("type_conge", "ANNUEL",
				"date_debut", "2026-12-07", "date_fin", "2026-12-08")));
		return req;
	}

	private void preparerCreationMoteur(List<Boolean> txPendantAppelsMoteur, UUID[] idCree) {
		when(workflowActivationService.estMoteur(TypeDemandeAdministrativeRh.CONGE)).thenAnswer(inv -> {
			txPendantAppelsMoteur.add(TransactionSynchronizationManager.isActualTransactionActive());
			return true;
		});
		when(validationService.validerContenu(eq(TypeDemandeAdministrativeRh.CONGE), any())).thenReturn("ANNUEL");
		when(numeroLisibleService.attribuer(any(), any())).thenReturn("DEM-202612-0001");
		when(demandeRepo.save(any(DemandeAdministrativeRh.class))).thenAnswer(inv -> {
			DemandeAdministrativeRh d = inv.getArgument(0);
			if (d.getId() == null) {
				d.setId(UUID.randomUUID());
			}
			idCree[0] = d.getId();
			return d;
		});
	}

	@Test
	void creationMoteurEnCoursADemarrerSansNotificationEtAppelsMoteurHorsTransaction() {
		List<Boolean> txPendantAppelsMoteur = new ArrayList<>();
		UUID[] idCree = new UUID[1];
		preparerCreationMoteur(txPendantAppelsMoteur, idCree);
		doAnswer(inv -> {
			txPendantAppelsMoteur.add(TransactionSynchronizationManager.isActualTransactionActive());
			return null;
		}).when(workflowDemarrageService).demarrer(any());

		DemandeAdministrativeRhResponse r = service.creer(requeteConge(), jwtDemandeur);

		ArgumentCaptor<DemandeAdministrativeRh> capt = ArgumentCaptor.forClass(DemandeAdministrativeRh.class);
		verify(demandeRepo).save(capt.capture());
		DemandeAdministrativeRh saved = capt.getValue();
		assertThat(saved.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
		assertThat(saved.getWorkflowMode()).isEqualTo("MOTEUR");
		assertThat(saved.getWorkflowEtat()).isEqualTo("A_DEMARRER");
		assertThat(saved.getValideurAttendu()).isNull();
		verify(workflowDemarrageService).demarrer(idCree[0]);
		// estMoteur puis demarrer : jamais dans une transaction
		assertThat(txPendantAppelsMoteur).containsExactly(false, false);
		verifyNoInteractions(notificationPublisher);
		assertThat(r).isNotNull();
		assertThat(r.getIdentifiant()).isEqualTo(idCree[0]);
	}

	@Test
	void creationMoteurRendLaReponseMemeSiLeDemarrageEchoue() {
		List<Boolean> txPendantAppelsMoteur = new ArrayList<>();
		UUID[] idCree = new UUID[1];
		preparerCreationMoteur(txPendantAppelsMoteur, idCree);
		doThrow(new IllegalStateException("moteur injoignable")).when(workflowDemarrageService).demarrer(any());

		DemandeAdministrativeRhResponse r = service.creer(requeteConge(), jwtDemandeur);

		assertThat(r).isNotNull();
		assertThat(r.getIdentifiant()).isEqualTo(idCree[0]);
		assertThat(r.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
		verifyNoInteractions(notificationPublisher);
	}

	// ─── D-W5-08 / D-W5-13 : suivi d'une demande MOTEUR ───────────────────────

	@Test
	void suiviMoteurEnCoursMontreEtapeCouranteSansMessageNiEtapeHeritee() {
		var suivi = service.suivi(demande.getId(), jwtDemandeur, false);

		assertThat(suivi.getMessageExplication())
				.isEqualTo(DemandeAdministrativeRhService.MSG_SUIVI_MOTEUR)
				.doesNotContain("directement en validation RH");
		assertThat(suivi.getEtapes()).extracting(e -> e.getCode())
				.containsExactly("DEPOT", "WORKFLOW", "CLOTURE");
		assertThat(suivi.getEtapes().get(1).getLibelle()).isEqualTo("Validation responsable");
		assertThat(suivi.getEtapes().get(1).isEnCours()).isTrue();
		assertThat(suivi.getValideurAttenduNomComplet()).isNull();
		assertThat(suivi.getWorkflowMode()).isEqualTo("MOTEUR");
	}

	@Test
	void suiviMoteurAnnuleeNeCochePasLEtapeDeValidation() {
		demande.setStatut(StatutDemandeAdministrativeRh.ANNULEE);

		var suivi = service.suivi(demande.getId(), jwtDemandeur, false);

		assertThat(suivi.getEtapes().get(1).isTerminee()).isFalse();
		assertThat(suivi.getEtapes().get(2).getLibelle()).isEqualTo("Annulée par le demandeur");
	}

	@Test
	void suiviMoteurApprouveeEstClotureeSansMessage() {
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);

		var suivi = service.suivi(demande.getId(), jwtDemandeur, false);

		assertThat(suivi.getMessageExplication()).isNull();
		assertThat(suivi.getEtapes().get(2).getLibelle()).isEqualTo("Approuvée ✓");
		assertThat(suivi.getEtapes().get(2).isTerminee()).isTrue();
		assertThat(suivi.getEtapes().get(1).isEnCours()).isFalse();
	}
}
