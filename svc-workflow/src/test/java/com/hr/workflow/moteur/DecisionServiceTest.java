package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.DecisionService.ResultatDecision;
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
import com.hr.workflow.web.WorkflowMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfEtapeExecutionRepository etapeExecutionRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private WfAppelEffetRepository appelEffetRepository;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private EffetClient effetClient;
	@Mock private OutboxService outboxService;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final PlanificationEcheanceService planifService = new PlanificationEcheanceService();
	private final AvancementService avancementService = new AvancementService();
	private DecisionService service;

	private UUID instanceId, tacheId, demandeurCollabId, demandeurCompteId, valideurCollabId, valideurCompteId;
	private WfInstance instance;
	private WfEtapeExecution etapeExec;
	private WfTache tache;
	private WfTacheCandidat candidat;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
		PreparationDecisionService preparationService = new PreparationDecisionService(
				catalogue, resolutionService, referentielClient, planifService, avancementService, clock);
		ApplicationDecisionService applicationService = new ApplicationDecisionService(
				instanceRepository, tacheRepository, candidatRepository, etapeExecutionRepository,
				historiqueRepository, outboxService, clock);
		service = new DecisionService(
				instanceRepository, tacheRepository, candidatRepository,
				beneficiaireRepository, historiqueRepository, appelEffetRepository, catalogue, codec,
				preparationService, applicationService, effetClient, txManager, clock);

		instanceId = UUID.randomUUID();
		tacheId = UUID.randomUUID();
		demandeurCollabId = UUID.randomUUID();
		demandeurCompteId = UUID.randomUUID();
		valideurCollabId = UUID.randomUUID();
		valideurCompteId = UUID.randomUUID();

		EtapeDefinition eRh = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				true, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setNumero(1);
		v.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(eRh))));

		instance = new WfInstance();
		instance.setId(instanceId);
		instance.setModeleVersion(v);
		instance.setObjetType("CONGE");
		instance.setObjetId(UUID.randomUUID());
		instance.setObjetNumero("DEM-202611-0001");
		instance.setServiceProprietaire("svc-referentiel-rh");
		instance.setDemandeurCollaborateurId(demandeurCollabId);
		instance.setDemandeurCompteId(demandeurCompteId);
		instance.setDemandeurNom("Sarra Khemiri");
		instance.setStatut(StatutInstance.EN_COURS);
		instance.setEtapeCouranteCode("VALID_RH");

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
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setVersion(1L);

		candidat = new WfTacheCandidat();
		candidat.setId(new WfTacheCandidatId(tacheId, valideurCollabId));
		candidat.setCompteId(valideurCompteId);
		candidat.setNom("Amel Trabelsi");

		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId)).thenReturn(List.of());
		lenient().when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, valideurCompteId)).thenReturn(true);
		lenient().when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, valideurCompteId))
				.thenReturn(Optional.of(candidat));
		lenient().when(tacheRepository.findByEtapeExecutionId(etapeExec.getId())).thenReturn(List.of(tache));
		lenient().when(tacheRepository.findApprobateursCollaborateurIds(instanceId)).thenReturn(List.of());
		lenient().when(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(Map.of(), null));
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		lenient().when(tacheRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(appelEffetRepository.saveAndFlush(any(WfAppelEffet.class))).thenAnswer(inv -> {
			WfAppelEffet ae = inv.getArgument(0);
			if (ae.getId() == null) ae.setId(UUID.randomUUID());
			return ae;
		});
	}

	@Test
	void ca07_6_refusSansMotifOuMotifTropCourt_422_MOTIF_OBLIGATOIRE() {
		DecisionRequest req = new DecisionRequest("REFUSER", "non", 1L, "cle-refus");
		assertThatThrownBy(() -> service.decider(tacheId, req, valideurCompteId, "Amel", true))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "MOTIF_OBLIGATOIRE");
	}

	@Test
	void ca07_7_nonCandidat403_etAutoDecision403() {
		UUID autreCompte = UUID.randomUUID();
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, autreCompte)).thenReturn(false);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, autreCompte)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "k1"), autreCompte, "Autre", true))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "PAS_INTERVENANT");

		// Auto-décision (demandeur = candidat) -> 403 AUTO_DECISION_INTERDITE (I1)
		WfTacheCandidat candAuto = new WfTacheCandidat();
		candAuto.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candAuto.setCompteId(demandeurCompteId);
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candAuto));

		assertThatThrownBy(() -> service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "k2"), demandeurCompteId, "Sarra", false))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "AUTO_DECISION_INTERDITE");
	}

	@Test
	void ca07_5_idempotence_memeCleRenvoieReponseMemorisee() {
		tache.setCleIdempotence("cle-idem");
		tache.setReponseDecision(Map.of(
				"tache_id", tacheId.toString(),
				"decision", "APPROUVEE",
				"instance", Map.of("statut", "APPROUVEE")));

		ResultatDecision res = service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-idem"),
				valideurCompteId, "Amel", true);
		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().decision()).isEqualTo("APPROUVEE");
	}

	@Test
	void ca07_8_soldeInsuffisantSurEffet_422EtTacheResteATraiter() {
		// CONTROLER OK, APPLIQUER renvoie 422 SOLDE_INSUFFISANT
		when(effetClient.appeler(any(), any(), any()))
				.thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)))
				.thenReturn(new ResultatAppelEffet.RejetMetier(422, "SOLDE_INSUFFISANT", "Solde insuffisant", Map.of()));
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		ae.setEffet("APPROUVER");
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));

		assertThatThrownBy(() -> service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-solde"),
				valideurCompteId, "Amel", true))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "SOLDE_INSUFFISANT");
		assertThat(tache.getStatut()).isEqualTo(StatutTache.A_TRAITER);
	}

	@Test
	void effetTerminal_200_puisDelaiDepasse202() {
		// 1. Succès 200
		when(effetClient.appeler(any(), any(), any()))
				.thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));

		ResultatDecision ok = service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-ok"),
				valideurCompteId, "Amel", true);
		assertThat(ok.httpStatus()).isEqualTo(200);
		assertThat(ok.reponse().instance().statut()).isEqualTo(StatutInstance.APPROUVEE);

		// 2. Délai dépassé -> 202 DECISION_EN_COURS (D-W29)
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setCleIdempotence(null);
		tache.setReponseDecision(null);
		instance.setStatut(StatutInstance.EN_COURS);
		when(effetClient.appeler(any(), any(), any()))
				.thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)))
				.thenReturn(new ResultatAppelEffet.Incertain("Timeout"));

		ResultatDecision incertain = service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-timeout"),
				valideurCompteId, "Amel", true);
		assertThat(incertain.httpStatus()).isEqualTo(202);
		assertThat(incertain.decisionEnCours()).isTrue();
		assertThat(tache.getStatut()).isEqualTo(StatutTache.RESERVEE);
	}

	@Test
	void annulationPendantEffet_puis409Proprietaire_tacheResteAnnulee() {
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		ae.setEffet("APPROUVER");
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));
		when(effetClient.appeler(any(), any(), any())).thenAnswer(inv -> {
			EffetClient.RequeteEffet req = inv.getArgument(2);
			if ("CONTROLER".equals(req.mode())) {
				return new ResultatAppelEffet.Succes(Map.of("ok", true));
			}
			// Annulation concurrente pendant l'appel APPLIQUER
			instance.setStatut(StatutInstance.ANNULEE);
			tache.setStatut(StatutTache.ANNULEE);
			return new ResultatAppelEffet.RejetMetier(409, "DEJA_ANNULEE", "Demande annulée", Map.of());
		});

		assertThatThrownBy(() -> service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-annul-409"),
				valideurCompteId, "Amel", true))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409);

		assertThat(ae.getStatut()).isEqualTo(StatutAppelEffet.REJETE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.ANNULEE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.ANNULEE);
	}

	@Test
	void annulationPendantEffet_puis200Proprietaire_instanceResteAnnulee() {
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		ae.setEffet("APPROUVER");
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));
		when(effetClient.appeler(any(), any(), any())).thenAnswer(inv -> {
			EffetClient.RequeteEffet req = inv.getArgument(2);
			if ("CONTROLER".equals(req.mode())) {
				return new ResultatAppelEffet.Succes(Map.of("ok", true));
			}
			instance.setStatut(StatutInstance.ANNULEE);
			tache.setStatut(StatutTache.ANNULEE);
			return new ResultatAppelEffet.Succes(Map.of("ok", true));
		});

		assertThatThrownBy(() -> service.decider(tacheId, new DecisionRequest("APPROUVER", null, 1L, "cle-annul-200"),
				valideurCompteId, "Amel", true))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("code", "INSTANCE_TERMINEE");

		assertThat(ae.getStatut()).isEqualTo(StatutAppelEffet.APPLIQUE);
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.ANNULEE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.ANNULEE);
	}
}
