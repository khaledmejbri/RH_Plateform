package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.moteur.DecisionService.ResultatDecision;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.repository.WfAppelEffetRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link EffetRepriseScheduler} (revue W3 n°1) :
 * reprise 200 (APPROUVER fin, REFUSER, METTRE_A_COMPLETER), 422, 24 h -> ERREUR,
 * et rejeu client avec la même {@code cle_idempotence} après reprise -> 200.
 */
@ExtendWith(MockitoExtension.class)
class EffetRepriseSchedulerTest {

	@Mock private WfAppelEffetRepository appelEffetRepository;
	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfEtapeExecutionRepository etapeExecutionRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private EffetClient effetClient;
	@Mock private OutboxService outboxService;
	@Mock private VerrouPlanificateur verrouPlanificateur;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final Instant maintenant = Instant.parse("2026-11-20T10:00:00Z");
	private final Clock clock = Clock.fixed(maintenant, ZoneOffset.UTC);

	private EffetRepriseScheduler scheduler;
	private DecisionService decisionService;

	private UUID instanceId, tacheId, appelEffetId, demandeurCollabId, demandeurCompteId, decideurCollabId, decideurCompteId;
	private WfInstance instance;
	private WfEtapeExecution etapeExec;
	private WfTache tache;
	private WfAppelEffet appelEffet;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		PreparationDecisionService preparationService = new PreparationDecisionService(
				catalogue, resolutionService, referentielClient,
				new PlanificationEcheanceService(), new AvancementService(), clock);
		ApplicationDecisionService applicationService = new ApplicationDecisionService(
				instanceRepository, tacheRepository, candidatRepository, etapeExecutionRepository,
				historiqueRepository, outboxService, clock);

		scheduler = new EffetRepriseScheduler(
				appelEffetRepository, instanceRepository, tacheRepository, beneficiaireRepository,
				historiqueRepository, catalogue, codec, preparationService, applicationService,
				effetClient, outboxService, verrouPlanificateur, txManager, clock);
		decisionService = new DecisionService(
				instanceRepository, tacheRepository, candidatRepository, beneficiaireRepository,
				historiqueRepository, appelEffetRepository, catalogue, codec,
				preparationService, applicationService, effetClient, txManager, clock);

		instanceId = UUID.randomUUID();
		tacheId = UUID.randomUUID();
		appelEffetId = UUID.randomUUID();
		demandeurCollabId = UUID.randomUUID();
		demandeurCompteId = UUID.randomUUID();
		decideurCollabId = UUID.randomUUID();
		decideurCompteId = UUID.randomUUID();

		EtapeDefinition eRh = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion version = new WfModeleVersion();
		version.setId(UUID.randomUUID());
		version.setNumero(1);
		version.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(eRh))));

		instance = new WfInstance();
		instance.setId(instanceId);
		instance.setModeleVersion(version);
		instance.setObjetType("CONGE");
		instance.setObjetId(UUID.randomUUID());
		instance.setObjetNumero("DEM-202611-0050");
		instance.setServiceProprietaire("svc-referentiel-rh");
		instance.setDemandeurCollaborateurId(demandeurCollabId);
		instance.setDemandeurCompteId(demandeurCompteId);
		instance.setDemandeurNom("Sarra Khemiri");
		instance.setStatut(StatutInstance.EN_COURS);
		instance.setEtapeCouranteCode("VALID_RH");
		instance.setVariables(Map.of());

		etapeExec = new WfEtapeExecution();
		etapeExec.setId(UUID.randomUUID());
		etapeExec.setInstance(instance);
		etapeExec.setEtapeCode("VALID_RH");
		etapeExec.setOrdre(1);
		etapeExec.setStatut(StatutEtapeExecution.ACTIVE);

		tache = new WfTache();
		tache.setId(tacheId);
		tache.setInstance(instance);
		tache.setEtapeExecution(etapeExec);
		tache.setType(TypeTache.VALIDATION);
		tache.setLibelle("Validation RH");
		tache.setStatut(StatutTache.RESERVEE);
		tache.setDecision(DecisionTache.APPROUVEE);
		tache.setDecideParCompteId(decideurCompteId);
		tache.setDecideParCollaborateurId(decideurCollabId);
		tache.setDecideParNom("Amel Trabelsi");
		tache.setCleIdempotence("cle-client-1");
		tache.setVersion(1L);

		appelEffet = new WfAppelEffet();
		appelEffet.setId(appelEffetId);
		appelEffet.setCleIdempotence("decision:" + tacheId + ":cle-client-1");
		appelEffet.setInstanceId(instanceId);
		appelEffet.setTacheId(tacheId);
		appelEffet.setEffet("APPROUVER");
		appelEffet.setServiceProprietaire("svc-referentiel-rh");
		appelEffet.setStatut(StatutAppelEffet.INCERTAIN);
		appelEffet.setTentatives(1);
		appelEffet.setCreeLe(maintenant.minus(Duration.ofMinutes(2)));

		lenient().when(appelEffetRepository.verrouillerLotIncertains(any(), anyInt())).thenReturn(List.of(appelEffet));
		lenient().when(appelEffetRepository.findById(appelEffetId)).thenReturn(Optional.of(appelEffet));
		lenient().when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(tacheRepository.findById(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(tacheRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(etapeExecutionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId)).thenReturn(List.of());
		lenient().when(resolutionService.resoudre(any(), any(), any(), any(), any()))
				.thenReturn(new LotResolu(Map.of(), null));
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
	}

	@Test
	void reprise200_approuverFin_appliqueDecisionCompleteEtRejeuClientRetourne200() {
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		int n = scheduler.reprendreIncertains();

		assertThat(n).isEqualTo(1);
		assertThat(appelEffet.getStatut()).isEqualTo(StatutAppelEffet.APPLIQUE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.TRAITEE);
		assertThat(tache.getReponseDecision()).isNotNull();
		assertThat(etapeExec.getStatut()).isEqualTo(StatutEtapeExecution.TERMINEE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.APPROUVEE);

		// Historique DECISION + INSTANCE_TERMINEE
		ArgumentCaptor<WfHistorique> histCaptor = ArgumentCaptor.forClass(WfHistorique.class);
		verify(historiqueRepository, atLeastOnce()).save(histCaptor.capture());
		assertThat(histCaptor.getAllValues()).extracting(WfHistorique::getTypeEvenement)
				.contains("DECISION", "INSTANCE_TERMINEE");

		// Notification WF_FIN au demandeur + événement INSTANCE_TERMINEE
		verify(outboxService).publierNotification(
				any(), eq(demandeurCompteId.toString()), eq("WEBSOCKET"), eq("WF_FIN"),
				any(), any(), eq("CONGE"), eq(instance.getObjetId()), eq("DEM-202611-0050"));
		verify(outboxService).publierEvenementWorkflow(any(), eq("INSTANCE_TERMINEE"), eq(instance), any(), any(), any());

		// Rejeu client avec la même cle_idempotence -> 200 avec la réponse mémorisée
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, decideurCompteId)).thenReturn(true);
		ResultatDecision rejeu = decisionService.decider(
				tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-client-1"),
				decideurCompteId, "Amel Trabelsi", false);
		assertThat(rejeu.httpStatus()).isEqualTo(200);
		assertThat(rejeu.reponse().decision()).isEqualTo("APPROUVEE");
		assertThat(rejeu.reponse().instance().statut()).isEqualTo(StatutInstance.APPROUVEE);
	}

	@Test
	void reprise200_refuser_termineInstanceRefuseeEtNotifieDemandeur() {
		tache.setDecision(DecisionTache.REFUSEE);
		tache.setCommentaire("Solde insuffisant");
		appelEffet.setEffet("REFUSER");
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		scheduler.reprendreIncertains();

		assertThat(instance.getStatut()).isEqualTo(StatutInstance.REFUSEE);
		assertThat(instance.getResultatMotif()).isEqualTo("Solde insuffisant");
		assertThat(tache.getStatut()).isEqualTo(StatutTache.TRAITEE);
		verify(outboxService).publierNotification(
				any(), eq(demandeurCompteId.toString()), eq("WEBSOCKET"), eq("WF_FIN"),
				eq("Demande refusée"), any(), eq("CONGE"), any(), any());
	}

	@Test
	void reprise200_mettreACompleter_creeTacheComplementEtIncrementeNbRenvois() {
		tache.setDecision(DecisionTache.RENVOYEE);
		tache.setCommentaire("Joindre certificat");
		appelEffet.setEffet("METTRE_A_COMPLETER");
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		scheduler.reprendreIncertains();

		assertThat(etapeExec.getNbRenvois()).isEqualTo(1);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.EN_COURS);
		verify(tacheRepository).saveAndFlush(argThat(nt ->
				nt.getType() == TypeTache.COMPLEMENT && nt.getStatut() == StatutTache.A_TRAITER));
		verify(candidatRepository).save(argThat(c ->
				c.getCompteId().equals(demandeurCompteId)));
		verify(outboxService).publierNotification(
				any(), eq(demandeurCompteId.toString()), eq("WEBSOCKET"), eq("WF_DECISION"),
				eq("Complément demandé"), any(), eq("CONGE"), any(), any());
	}

	@Test
	void reprise422_rouvreTacheATraiterEtNotifieDecideur() {
		when(effetClient.appeler(any(), any(), any()))
				.thenReturn(new ResultatAppelEffet.RejetMetier(422, "SOLDE_INSUFFISANT", "Solde insuffisant", Map.of()));

		scheduler.reprendreIncertains();

		assertThat(appelEffet.getStatut()).isEqualTo(StatutAppelEffet.REJETE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tache.getCleIdempotence()).isNull();
		verify(outboxService).publierNotification(
				any(), eq(decideurCompteId.toString()), eq("WEBSOCKET"), eq("WF_DECISION"),
				eq("Décision refusée par le service métier"), eq("Solde insuffisant"), eq("CONGE"), any(), any());
	}

	@Test
	void reprise24hDepassees_passeInstanceEnErreur() {
		appelEffet.setCreeLe(maintenant.minus(Duration.ofHours(25)));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Incertain("Timeout"));

		scheduler.reprendreIncertains();

		assertThat(instance.getStatut()).isEqualTo(StatutInstance.ERREUR);
		assertThat(appelEffet.getProchaineTentativeLe()).isNull();
		verify(historiqueRepository).save(argThat(h -> "INSTANCE_ERREUR".equals(h.getTypeEvenement())));
		verify(outboxService).publierEvenementWorkflow(any(), eq("INSTANCE_TERMINEE"), eq(instance), any(), any(), any());
	}
}
