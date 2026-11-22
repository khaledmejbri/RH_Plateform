package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DefinitionWorkflow.OptionsWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EcheanceDefinition.ApresEcheance;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires d'{@link EcheanceScheduler} (D-W17 à D-W20, CA-10.4 à 10.7, CA-13.4, Q4).
 */
@ExtendWith(MockitoExtension.class)
class EcheanceSchedulerTest {

	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfEtapeExecutionRepository etapeExecutionRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private WfAppelEffetRepository appelEffetRepository;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private EffetClient effetClient;
	@Mock private OutboxService outboxService;
	@Mock private VerrouPlanificateur verrouPlanificateur;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final PlanificationEcheanceService planifService = new PlanificationEcheanceService();
	private final AvancementService avancementService = new AvancementService();
	private final Instant maintenant = Instant.parse("2026-11-20T12:00:00Z");
	private final Clock clock = Clock.fixed(maintenant, ZoneOffset.UTC);

	private EcheanceScheduler scheduler;

	private UUID instanceId, tacheId, demandeurCollabId, demandeurCompteId, titulaireCollabId, titulaireCompteId;
	private WfInstance instance;
	private WfEtapeExecution etapeExec;
	private WfTache tache;
	private WfTacheCandidat titulaireCand;
	private WfAppelEffet appelEffet;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		PreparationDecisionService preparationService = new PreparationDecisionService(
				catalogue, resolutionService, referentielClient, planifService, avancementService, clock);
		ApplicationDecisionService applicationService = new ApplicationDecisionService(
				instanceRepository, tacheRepository, candidatRepository, etapeExecutionRepository,
				historiqueRepository, outboxService, clock);
		scheduler = new EcheanceScheduler(
				tacheRepository, instanceRepository, candidatRepository, beneficiaireRepository,
				historiqueRepository, appelEffetRepository, catalogue, codec, planifService,
				resolutionService, preparationService, applicationService, referentielClient,
				effetClient, outboxService, verrouPlanificateur, txManager, clock);

		instanceId = UUID.randomUUID();
		tacheId = UUID.randomUUID();
		demandeurCollabId = UUID.randomUUID();
		demandeurCompteId = UUID.randomUUID();
		titulaireCollabId = UUID.randomUUID();
		titulaireCompteId = UUID.randomUUID();

		instance = new WfInstance();
		instance.setId(instanceId);
		instance.setObjetType("CONGE");
		instance.setObjetId(UUID.randomUUID());
		instance.setObjetNumero("DEM-202611-0100");
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
		etapeExec.setActiveLe(maintenant.minus(12, ChronoUnit.HOURS));

		tache = new WfTache();
		tache.setId(tacheId);
		tache.setInstance(instance);
		tache.setEtapeExecution(etapeExec);
		tache.setType(TypeTache.VALIDATION);
		tache.setLibelle("Validation RH");
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setObligatoire(true);
		tache.setVersion(1L);

		titulaireCand = new WfTacheCandidat();
		titulaireCand.setId(new WfTacheCandidatId(tacheId, titulaireCollabId));
		titulaireCand.setCompteId(titulaireCompteId);
		titulaireCand.setNom("Amel Trabelsi");
		titulaireCand.setOrigine(OrigineCandidat.TITULAIRE);

		appelEffet = new WfAppelEffet();
		appelEffet.setId(UUID.randomUUID());

		lenient().when(tacheRepository.verrouillerLotEcheances(any(), anyInt())).thenReturn(List.of(tache));
		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(tacheRepository.findByEtapeExecutionId(etapeExec.getId())).thenReturn(List.of(tache));
		lenient().when(tacheRepository.findByInstanceIdAndStatutIn(eq(instanceId), any())).thenReturn(List.of());
		lenient().when(tacheRepository.findApprobateursCollaborateurIds(instanceId)).thenReturn(List.of());
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId)).thenReturn(List.of());
		lenient().when(candidatRepository.findByIdTacheIdAndRetireLeIsNull(tacheId)).thenReturn(List.of(titulaireCand));
		lenient().when(candidatRepository.findByIdTacheId(tacheId)).thenReturn(List.of(titulaireCand));
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		lenient().when(appelEffetRepository.saveAndFlush(any(WfAppelEffet.class))).thenAnswer(inv -> {
			WfAppelEffet ae = inv.getArgument(0);
			if (ae.getId() == null) ae.setId(appelEffet.getId());
			appelEffet = ae;
			return ae;
		});
		lenient().when(appelEffetRepository.findById(any())).thenAnswer(inv -> Optional.of(appelEffet));
		lenient().when(tacheRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	private void configurerDefinition(String objetType, EcheanceDefinition aEcheance, RelancesDefinition relances) {
		instance.setObjetType(objetType);
		EtapeDefinition etape = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), relances, aEcheance, null, null, null);
		WfModeleVersion version = new WfModeleVersion();
		version.setId(UUID.randomUUID());
		version.setNumero(1);
		version.setDefinition(codec.serialiser(new DefinitionWorkflow(1, objetType, null, List.of(etape))));
		instance.setModeleVersion(version);
	}

	@Test
	void relance_notifieCandidatActif_incrementeNbRelances_etPlanifieSuivantePuisNull() {
		configurerDefinition("CONGE", new EcheanceDefinition("RIEN", null, null),
				new RelancesDefinition(2, 4, null));
		tache.setEcheanceLe(maintenant.plus(10, ChronoUnit.HOURS));
		tache.setProchaineRelanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		tache.setNbRelances(0);

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getNbRelances()).isEqualTo(1);
		// Q2 : intervalle par défaut 24 h après echeanceLe
		assertThat(tache.getProchaineRelanceLe()).isEqualTo(tache.getEcheanceLe().plus(24, ChronoUnit.HOURS));
		verify(outboxService).publierNotification(any(), eq(titulaireCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_RELANCE"), eq("Rappel : demande à traiter"),
				contains("Rappel : la demande Congé de Sarra Khemiri attend votre décision"),
				eq("CONGE"), eq(instance.getObjetId()), eq("DEM-202611-0100"));
		verify(historiqueRepository).save(argThat(h -> "RELANCE".equals(h.getTypeEvenement())));

		// 2e relance (nbRelances passe à 2 == nombre max -> prochaineRelanceLe = null)
		tache.setProchaineRelanceLe(maintenant);
		scheduler.traiterEcheances();
		assertThat(tache.getNbRelances()).isEqualTo(2);
		assertThat(tache.getProchaineRelanceLe()).isNull();
	}

	@Test
	void echeanceRien_poseEcheanceTraiteeLe_tacheResteATraiter() {
		configurerDefinition("CONGE", new EcheanceDefinition("RIEN", null, null), null);
		tache.setEcheanceLe(maintenant.minus(5, ChronoUnit.MINUTES));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tache.getEcheanceTraiteeLe()).isEqualTo(maintenant);
		verify(historiqueRepository).save(argThat(h -> "ECHEANCE_ATTEINTE".equals(h.getTypeEvenement())));
		verify(effetClient, never()).appeler(any(), any(), any());
	}

	@Test
	void ca10_5_escalade_ajouteCandidatsSansRetirerTitulaires_etReactiveSiRetire() {
		configurerDefinition("CONGE", new EcheanceDefinition("ESCALADER", new ApresEcheance(4, "HEURES"),
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL")), null);
		tache.setEcheanceLe(maintenant.minus(5, ChronoUnit.HOURS));
		tache.setEcheanceTraiteeLe(maintenant.minus(4, ChronoUnit.HOURS));
		tache.setEscaladeAFaireLe(maintenant.minus(1, ChronoUnit.MINUTES));

		UUID nouveauCollab = UUID.randomUUID();
		UUID nouveauCompte = UUID.randomUUID();
		WfTacheCandidat ancienRetire = new WfTacheCandidat();
		ancienRetire.setId(new WfTacheCandidatId(tacheId, nouveauCollab));
		ancienRetire.setCompteId(nouveauCompte);
		ancienRetire.setNom("Nadia RH");
		ancienRetire.setRetireLe(maintenant.minus(2, ChronoUnit.DAYS));
		when(candidatRepository.findByIdTacheId(tacheId)).thenReturn(List.of(titulaireCand, ancienRetire));

		when(resolutionService.resoudre(any(), eq(demandeurCollabId), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("escalade", new ResultatRegleResolue(List.of(
						new IntervenantResolu(titulaireCollabId, titulaireCompteId, "Amel Trabelsi", "Titulaire"),
						new IntervenantResolu(nouveauCollab, nouveauCompte, "Nadia RH", "RH")),
						false, false, null)),
				null));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getEscaladeLe()).isEqualTo(maintenant);
		// Titulaire conservé intact
		assertThat(titulaireCand.getRetireLe()).isNull();
		assertThat(titulaireCand.getOrigine()).isEqualTo(OrigineCandidat.TITULAIRE);
		// Ancien candidat réactivé en ESCALADE
		assertThat(ancienRetire.getRetireLe()).isNull();
		assertThat(ancienRetire.getOrigine()).isEqualTo(OrigineCandidat.ESCALADE);

		verify(outboxService).publierNotification(any(), eq(nouveauCompte.toString()), eq("WEBSOCKET"),
				eq("WF_ESCALADE"), eq("Tâche escaladée"), contains("en retard, transmise pour décision"),
				eq("CONGE"), any(), any());
		// Le titulaire déjà actif n'est pas re-notifié WF_ESCALADE
		verify(outboxService, never()).publierNotification(any(), eq(titulaireCompteId.toString()), any(),
				eq("WF_ESCALADE"), any(), any(), any(), any(), any());
		verify(outboxService).publierEvenementWorkflow(any(), eq("TACHE_ESCALADEE"), eq(instance), any(), any(), any());
		verify(historiqueRepository).save(argThat(h -> "ESCALADEE".equals(h.getTypeEvenement())));
	}

	@Test
	void ca10_6_expirer_appelleEffetEtTermineInstanceExpiree_notifieDemandeurEtCandidats() {
		configurerDefinition("POINTAGE_MANUEL", new EcheanceDefinition("EXPIRER", null, null), null);
		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.EXPIREE);
		assertThat(tache.getEcheanceTraiteeLe()).isEqualTo(maintenant);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.EXPIREE);
		assertThat(instance.getResultatMotif()).isEqualTo("Délai de décision dépassé");

		verify(effetClient).appeler(eq("svc-referentiel-rh"), any(), argThat(r ->
				"APPLIQUER".equals(r.mode()) && "EXPIRER".equals(r.effet()) && "Système".equals(r.acteur().nom())));
		verify(outboxService).publierNotification(any(), eq(demandeurCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_FIN"), eq("Demande expirée"), contains("a expiré sans décision"), any(), any(), any());
		verify(outboxService).publierNotification(any(), eq(titulaireCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_FIN"), eq("Demande expirée"), contains("a expiré sans décision"), any(), any(), any());
		verify(outboxService).publierEvenementWorkflow(any(), eq("INSTANCE_TERMINEE"), eq(instance), any(), any(), any());
	}

	@Test
	void refuserAuto_appliqueEffetRefuserAvecActeurSysteme() {
		configurerDefinition("CONGE", new EcheanceDefinition("REFUSER_AUTO", null, null), null);
		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(Map.of(), null));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.TRAITEE);
		assertThat(tache.getDecision()).isEqualTo(DecisionTache.REFUSEE);
		assertThat(tache.getDecideParNom()).isEqualTo("Système");
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.REFUSEE);
		assertThat(instance.getResultatMotif()).isEqualTo("Délai de décision dépassé");
		verify(effetClient).appeler(any(), any(), argThat(r -> "REFUSER".equals(r.effet())));
	}

	@Test
	void approuverAuto_surOrdreMission_approuve_etSurCongeBloqueDefensivement_I9() {
		// 1. ORDRE_MISSION (validationHumaineObligatoire = false) -> APPROUVER_AUTO autorisé
		configurerDefinition("ORDRE_MISSION", new EcheanceDefinition("APPROUVER_AUTO", null, null), null);
		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(Map.of(), null));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		scheduler.traiterEcheances();

		assertThat(tache.getStatut()).isEqualTo(StatutTache.TRAITEE);
		assertThat(tache.getDecision()).isEqualTo(DecisionTache.APPROUVEE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.APPROUVEE);
		verify(effetClient).appeler(any(), any(), argThat(r ->
				"APPROUVER".equals(r.effet()) && "Approuvée automatiquement : délai dépassé".equals(r.commentaire())));

		// 2. CONGE (validationHumaineObligatoire = true, I9) -> bloqué défensivement, jamais approuvé
		instance.setStatut(StatutInstance.EN_COURS);
		instance.setTermineLe(null);
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setDecision(null);
		tache.setEcheanceTraiteeLe(null);
		configurerDefinition("CONGE", new EcheanceDefinition("APPROUVER_AUTO", null, null), null);
		UUID rhCompte = UUID.randomUUID();
		when(resolutionService.resoudreComptesRoles(any(), eq(List.of("RH", "ADMIN")))).thenReturn(List.of(rhCompte));

		scheduler.traiterEcheances();

		assertThat(instance.getStatut()).isEqualTo(StatutInstance.BLOQUEE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tache.getEcheanceTraiteeLe()).isEqualTo(maintenant);
		verify(outboxService).publierNotification(any(), eq(rhCompte.toString()), eq("WEBSOCKET"),
				eq("WF_BLOQUEE"), eq("Workflow bloqué"), any(), eq("CONGE"), any(), any());
	}

	@Test
	void ca13_4_expirationComplement_appelleEffetAnnulerEtTermineInstanceAnnulee() {
		configurerDefinition("CONGE", new EcheanceDefinition("RIEN", null, null), null);
		tache.setType(TypeTache.COMPLEMENT);
		tache.setLibelle("Complément demandé : Validation RH");
		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.EXPIREE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.ANNULEE);
		assertThat(instance.getResultatMotif()).isEqualTo("Complément non fourni dans le délai");
		verify(effetClient).appeler(any(), any(), argThat(r ->
				"ANNULER".equals(r.effet()) && ("echeance:" + tacheId + ":ANNULER").equals(r.cleIdempotence())));
		verify(outboxService).publierNotification(any(), eq(demandeurCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_FIN"), eq("Demande annulée"), contains("complément non fourni dans le délai"), any(), any(), any());
		ArgumentCaptor<WfHistorique> histCap = ArgumentCaptor.forClass(WfHistorique.class);
		verify(historiqueRepository, atLeastOnce()).save(histCap.capture());
		assertThat(histCap.getAllValues()).extracting(WfHistorique::getTypeEvenement)
				.contains("COMPLEMENT_EXPIRE", "INSTANCE_TERMINEE");
	}

	@Test
	void q4_rejetMetier422SurActionAuto_bloqueInstanceEtNotifieRhAdmin() {
		configurerDefinition("CONGE", new EcheanceDefinition("REFUSER_AUTO", null, null), null);
		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.MINUTES));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(Map.of(), null));
		when(effetClient.appeler(any(), any(), any()))
				.thenReturn(new ResultatAppelEffet.RejetMetier(422, "SOLDE_BLOQUE", "Impossible", Map.of()));
		UUID rhCompte = UUID.randomUUID();
		when(resolutionService.resoudreComptesRoles(any(), eq(List.of("RH", "ADMIN")))).thenReturn(List.of(rhCompte));

		int traites = scheduler.traiterEcheances();

		assertThat(traites).isEqualTo(1);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.BLOQUEE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.A_TRAITER);
		assertThat(tache.getEcheanceTraiteeLe()).isEqualTo(maintenant);
		assertThat(tache.getDecision()).isNull();
		assertThat(tache.getCommentaire()).isNull();
		assertThat(tache.getDecideParNom()).isNull();
		assertThat(tache.getCleIdempotence()).isNull();
		verify(outboxService).publierNotification(any(), eq(rhCompte.toString()), eq("WEBSOCKET"),
				eq("WF_BLOQUEE"), eq("Workflow bloqué"), any(), eq("CONGE"), any(), any());
		verify(historiqueRepository).save(argThat(h -> "EFFET_REJETE".equals(h.getTypeEvenement())));
	}

	@Test
	void c4_escalade_utiliseFuseauSocieteEtExclutApprobateursPrecedentsSiSeparationDesTaches() {
		// 23:30 UTC le 20/11 -> 00:30 Africa/Tunis le 21/11
		Instant tardUtc = Instant.parse("2026-11-20T23:30:00Z");
		Clock clockTard = Clock.fixed(tardUtc, ZoneOffset.UTC);
		PreparationDecisionService prep = new PreparationDecisionService(
				catalogue, resolutionService, referentielClient, planifService, avancementService, clockTard);
		ApplicationDecisionService app = new ApplicationDecisionService(
				instanceRepository, tacheRepository, candidatRepository, etapeExecutionRepository,
				historiqueRepository, outboxService, clockTard);
		EcheanceScheduler schedTard = new EcheanceScheduler(
				tacheRepository, instanceRepository, candidatRepository, beneficiaireRepository,
				historiqueRepository, appelEffetRepository, catalogue, codec, planifService,
				resolutionService, prep, app, referentielClient, effetClient, outboxService,
				verrouPlanificateur, txManager, clockTard);

		EtapeDefinition etape = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("ESCALADER", new ApresEcheance(4, "HEURES"),
						new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL")),
				null, null, null);
		WfModeleVersion version = new WfModeleVersion();
		version.setId(UUID.randomUUID());
		version.setNumero(1);
		version.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", new OptionsWorkflow(true), List.of(etape))));
		instance.setModeleVersion(version);

		UUID approbateurPrecedent = UUID.randomUUID();
		when(tacheRepository.findApprobateursCollaborateurIds(instanceId)).thenReturn(List.of(approbateurPrecedent));
		tache.setEcheanceLe(tardUtc.minus(5, ChronoUnit.HOURS));
		tache.setEcheanceTraiteeLe(tardUtc.minus(4, ChronoUnit.HOURS));
		tache.setEscaladeAFaireLe(tardUtc.minus(1, ChronoUnit.MINUTES));

		when(resolutionService.resoudre(eq(LocalDate.of(2026, 11, 21)), eq(demandeurCollabId), any(), eq(List.of(approbateurPrecedent)), any()))
				.thenReturn(new LotResolu(Map.of("escalade", new ResultatRegleResolue(
						List.of(new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), "Autre RH", "RH")),
						false, false, null)), null));

		int traites = schedTard.traiterEcheances();
		assertThat(traites).isEqualTo(1);
		verify(resolutionService).resoudre(
				eq(LocalDate.of(2026, 11, 21)),
				eq(demandeurCollabId),
				any(),
				eq(List.of(approbateurPrecedent)),
				any());
	}

	@Test
	void c5_marquerEcheanceEscaladeDejaTraitee_retourneZero_etTypeInconnuExpireSansNpe() {
		// 1. Tâche dont l'échéance et l'escalade ont déjà été traitées entre lecture et écriture -> 0 traité
		configurerDefinition("CONGE", new EcheanceDefinition("ESCALADER", new ApresEcheance(4, "HEURES"),
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL")), null);
		WfTache tacheSnapshot = new WfTache();
		tacheSnapshot.setId(tacheId);
		tacheSnapshot.setInstance(instance);
		tacheSnapshot.setEtapeExecution(etapeExec);
		tacheSnapshot.setType(TypeTache.VALIDATION);
		tacheSnapshot.setLibelle("Validation RH");
		tacheSnapshot.setStatut(StatutTache.A_TRAITER);
		tacheSnapshot.setEcheanceLe(maintenant.minus(1, ChronoUnit.HOURS));
		tacheSnapshot.setEcheanceTraiteeLe(null);
		tacheSnapshot.setEscaladeAFaireLe(maintenant.plus(3, ChronoUnit.HOURS));

		tache.setEcheanceLe(maintenant.minus(1, ChronoUnit.HOURS));
		tache.setEcheanceTraiteeLe(maintenant.minus(30, ChronoUnit.MINUTES));
		tache.setEscaladeAFaireLe(maintenant.plus(3, ChronoUnit.HOURS));

		when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tacheSnapshot));
		when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));

		int traitesDejaTraite = scheduler.traiterEcheances();
		assertThat(traitesDejaTraite).isZero();

		// 2. Type inconnu du catalogue lors de l'expiration -> expire sans NPE ni appel HTTP
		configurerDefinition("OBJET_HISTORIQUE_INCONNU", new EcheanceDefinition("EXPIRER", null, null), null);
		tache.setEcheanceTraiteeLe(null);
		tache.setEscaladeAFaireLe(null);
		when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));

		int traitesInconnu = scheduler.traiterEcheances();
		assertThat(traitesInconnu).isEqualTo(1);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.EXPIREE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.EXPIREE);
		verify(effetClient, never()).appeler(any(), any(), any());
	}
}
