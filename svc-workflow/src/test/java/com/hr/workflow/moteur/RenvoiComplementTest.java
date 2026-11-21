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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests CA-13.1 à CA-13.3 : renvoi pour complément, réponse et plafond de 3 renvois (D-W10). */
@ExtendWith(MockitoExtension.class)
class RenvoiComplementTest {

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
	private DecisionService service;

	private UUID instanceId, tacheId, demandeurCollabId, demandeurCompteId, respCollabId, respCompteId;
	private WfInstance instance;
	private WfEtapeExecution ee;
	private WfTache tache;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
		PreparationDecisionService preparationService = new PreparationDecisionService(
				catalogue, resolutionService, referentielClient, new PlanificationEcheanceService(),
				new AvancementService(), clock);
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
		respCollabId = UUID.randomUUID();
		respCompteId = UUID.randomUUID();

		EtapeDefinition etape = new EtapeDefinition("VALID_RESP", "Validation responsable", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("PASSER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setNumero(1);
		v.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "ORDRE_MISSION", null, List.of(etape))));

		instance = new WfInstance();
		instance.setId(instanceId);
		instance.setModeleVersion(v);
		instance.setObjetType("ORDRE_MISSION");
		instance.setObjetId(UUID.randomUUID());
		instance.setObjetNumero("DEM-202611-0010");
		instance.setServiceProprietaire("svc-referentiel-rh");
		instance.setDemandeurCollaborateurId(demandeurCollabId);
		instance.setDemandeurCompteId(demandeurCompteId);
		instance.setDemandeurNom("Sarra");
		instance.setStatut(StatutInstance.EN_COURS);
		instance.setEtapeCouranteCode("VALID_RESP");

		ee = new WfEtapeExecution();
		ee.setId(UUID.randomUUID());
		ee.setInstance(instance);
		ee.setEtapeCode("VALID_RESP");
		ee.setOrdre(1);
		ee.setStatut(StatutEtapeExecution.ACTIVE);
		ee.setNbRenvois(0);

		tache = new WfTache();
		tache.setId(tacheId);
		tache.setInstance(instance);
		tache.setEtapeExecution(ee);
		tache.setType(TypeTache.VALIDATION);
		tache.setLibelle("Validation responsable");
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setVersion(1L);

		WfTacheCandidat candResp = new WfTacheCandidat();
		candResp.setId(new WfTacheCandidatId(tacheId, respCollabId));
		candResp.setCompteId(respCompteId);
		candResp.setNom("Omar Belhadj");

		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId)).thenReturn(List.of());
		lenient().when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, respCompteId)).thenReturn(true);
		lenient().when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, respCompteId))
				.thenReturn(Optional.of(candResp));
		lenient().when(tacheRepository.findByEtapeExecutionId(ee.getId())).thenReturn(List.of(tache));
		lenient().when(tacheRepository.findApprobateursCollaborateurIds(instanceId)).thenReturn(List.of());
		lenient().when(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		lenient().when(tacheRepository.saveAndFlush(any(WfTache.class))).thenAnswer(inv -> {
			WfTache saved = inv.getArgument(0);
			if (saved.getId() == null) saved.setId(UUID.randomUUID());
			return saved;
		});
	}

	@Test
	void ca13_1_renvoiCreeTacheComplementPourDemandeur() {
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		ResultatDecision res = service.decider(tacheId,
				new DecisionRequest("RENVOYER", "Joindre l'invitation officielle", 1L, "cle-renvoi-1"),
				respCompteId, "Omar", false);
		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().decision()).isEqualTo("RENVOYEE");
		assertThat(ee.getNbRenvois()).isEqualTo(1);

		ArgumentCaptor<WfTache> captor = ArgumentCaptor.forClass(WfTache.class);
		verify(tacheRepository, atLeastOnce()).saveAndFlush(captor.capture());
		assertThat(captor.getAllValues()).anyMatch(t -> t.getType() == TypeTache.COMPLEMENT);
	}

	@Test
	void ca13_2_reponseAuComplement_reactiveEtapeEtNotifieResponsable() {
		tache.setType(TypeTache.COMPLEMENT);
		WfTacheCandidat candDem = new WfTacheCandidat();
		candDem.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candDem.setCompteId(demandeurCompteId);
		candDem.setNom("Sarra");
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candDem));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("VALID_RESP", new ResultatRegleResolue(
						List.of(new IntervenantResolu(respCollabId, respCompteId, "Omar", "Chef")), false, false, null)),
				null));

		ResultatDecision res = service.decider(tacheId,
				new DecisionRequest("FAIT", "Invitation jointe", 1L, "cle-comp-1"),
				demandeurCompteId, "Sarra", false);
		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().decision()).isEqualTo("FAITE");
	}

	@Test
	void ca13_3_quatriemeRenvoiSurMemeEtape_422_RENVOIS_EPUISES() {
		ee.setNbRenvois(3);
		assertThatThrownBy(() -> service.decider(tacheId,
				new DecisionRequest("RENVOYER", "Encore un renvoi", 1L, "cle-renvoi-4"),
				respCompteId, "Omar", false))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "RENVOIS_EPUISES");
	}

	@Test
	void reactivationComplement_utiliseLibelleEtape_etModeTousCreeUneTacheParCandidat() {
		EtapeDefinition etapeTous = new EtapeDefinition("VALID_RESP", "Validation responsable", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "TOUS"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		instance.getModeleVersion().setDefinition(
				codec.serialiser(new DefinitionWorkflow(1, "ORDRE_MISSION", null, List.of(etapeTous))));

		tache.setType(TypeTache.COMPLEMENT);
		WfTacheCandidat candDem = new WfTacheCandidat();
		candDem.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candDem.setCompteId(demandeurCompteId);
		candDem.setNom("Sarra");
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candDem));

		UUID cand2CollabId = UUID.randomUUID();
		UUID cand2CompteId = UUID.randomUUID();
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("VALID_RESP", new ResultatRegleResolue(List.of(
						new IntervenantResolu(respCollabId, respCompteId, "Omar", "Chef"),
						new IntervenantResolu(cand2CollabId, cand2CompteId, "Youssef", "Directeur")),
						false, false, null)),
				null));

		ResultatDecision res = service.decider(tacheId,
				new DecisionRequest("FAIT", "Pièces ajoutées", 1L, "cle-comp-tous"),
				demandeurCompteId, "Sarra", false);

		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().instance().statut()).isEqualTo(StatutInstance.EN_COURS);
		assertThat(res.reponse().instance().etapeCourante().libelle()).isEqualTo("Validation responsable");

		ArgumentCaptor<WfTache> tacheCaptor = ArgumentCaptor.forClass(WfTache.class);
		verify(tacheRepository, atLeastOnce()).saveAndFlush(tacheCaptor.capture());
		List<WfTache> creees = tacheCaptor.getAllValues().stream()
				.filter(t -> t.getType() == TypeTache.VALIDATION && "Validation responsable".equals(t.getLibelle()))
				.toList();
		assertThat(creees).hasSize(2);
		verify(outboxService).publierNotification(any(), eq(respCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), any(), any(), eq("ORDRE_MISSION"), any(), any());
		verify(outboxService).publierNotification(any(), eq(cand2CompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), any(), any(), eq("ORDRE_MISSION"), any(), any());
	}

	@Test
	void reactivationComplement_listeVideEtRepliBloquer_passeInstanceBloqueeEtNotifieRhAdmin() {
		EtapeDefinition etapeBloquer = new EtapeDefinition("VALID_RESP", "Validation responsable", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER", "RENVOYER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		instance.getModeleVersion().setDefinition(
				codec.serialiser(new DefinitionWorkflow(1, "ORDRE_MISSION", null, List.of(etapeBloquer))));

		tache.setType(TypeTache.COMPLEMENT);
		WfTacheCandidat candDem = new WfTacheCandidat();
		candDem.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candDem.setCompteId(demandeurCompteId);
		candDem.setNom("Sarra");
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candDem));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("VALID_RESP", new ResultatRegleResolue(List.of(), false, true, "AUCUN_TITULAIRE")),
				null));
		UUID rhCompte = UUID.randomUUID();
		when(resolutionService.resoudreComptesRoles(any(), eq(List.of("RH", "ADMIN"))))
				.thenReturn(List.of(rhCompte));

		ResultatDecision res = service.decider(tacheId,
				new DecisionRequest("FAIT", "Pièces ajoutées", 1L, "cle-comp-vide"),
				demandeurCompteId, "Sarra", false);

		assertThat(res.httpStatus()).isEqualTo(200);
		assertThat(res.reponse().instance().statut()).isEqualTo(StatutInstance.BLOQUEE);
		verify(outboxService).publierNotification(any(), eq(rhCompte.toString()), eq("WEBSOCKET"),
				eq("WF_BLOQUEE"), eq("Workflow bloqué"), any(), eq("ORDRE_MISSION"), any(), any());
		verify(outboxService).publierEvenementWorkflow(any(), eq("INSTANCE_BLOQUEE"), eq(instance),
				eq("VALID_RESP"), eq("Validation responsable"), any());
	}

	@Test
	void reactivationComplement_repliPasserTerminal_appliqueEffetApprouverAvantApprouvee() {
		// Étape unique, repli PASSER (setUp) : plus aucun manager résolu au moment du complément.
		tache.setType(TypeTache.COMPLEMENT);
		WfTacheCandidat candDem = new WfTacheCandidat();
		candDem.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candDem.setCompteId(demandeurCompteId);
		candDem.setNom("Sarra");
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candDem));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("VALID_RESP", new ResultatRegleResolue(List.of(), true, false, "AUCUN_TITULAIRE")),
				null));
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Succes(Map.of("ok", true)));

		ResultatDecision res = service.decider(tacheId,
				new DecisionRequest("FAIT", "Pièces ajoutées", 1L, "cle-comp-passer"),
				demandeurCompteId, "Sarra", false);

		assertThat(res.httpStatus()).isEqualTo(200);
		verify(effetClient).appeler(any(), any(), argThat(r -> "APPLIQUER".equals(r.mode()) && "APPROUVER".equals(r.effet())));
		assertThat(res.reponse().instance().statut()).isEqualTo(StatutInstance.APPROUVEE);
	}

	@Test
	void reactivationComplement_repliPasserTerminal_effetIndisponible_instanceNonApprouvee() {
		tache.setType(TypeTache.COMPLEMENT);
		WfTacheCandidat candDem = new WfTacheCandidat();
		candDem.setId(new WfTacheCandidatId(tacheId, demandeurCollabId));
		candDem.setCompteId(demandeurCompteId);
		candDem.setNom("Sarra");
		when(candidatRepository.existsByIdTacheIdAndCompteId(tacheId, demandeurCompteId)).thenReturn(true);
		when(candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, demandeurCompteId))
				.thenReturn(Optional.of(candDem));
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("VALID_RESP", new ResultatRegleResolue(List.of(), true, false, "AUCUN_TITULAIRE")),
				null));
		WfAppelEffet ae = new WfAppelEffet();
		ae.setId(UUID.randomUUID());
		when(appelEffetRepository.saveAndFlush(any())).thenReturn(ae);
		lenient().when(appelEffetRepository.findById(ae.getId())).thenReturn(Optional.of(ae));
		when(effetClient.appeler(any(), any(), any())).thenReturn(new ResultatAppelEffet.Incertain("timeout"));

		service.decider(tacheId, new DecisionRequest("FAIT", "Pièces ajoutées", 1L, "cle-comp-passer-2"),
				demandeurCompteId, "Sarra", false);

		assertThat(instance.getStatut()).isNotEqualTo(StatutInstance.APPROUVEE);
		assertThat(tache.getStatut()).isEqualTo(StatutTache.RESERVEE);
	}
}
