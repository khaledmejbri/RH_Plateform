package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.WorkflowMoteurDtos.DemarrerInstanceRequest;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.moteur.DemarrageService.ResultatDemarrage;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemarrageServiceTest {

	@Mock private WfActivationRepository activationRepository;
	@Mock private WfModeleRepository modeleRepository;
	@Mock private WfModeleVersionRepository versionRepository;
	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfEtapeExecutionRepository etapeExecutionRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private OutboxService outboxService;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final PlanificationEcheanceService planifService = new PlanificationEcheanceService();
	private final AvancementService avancementService = new AvancementService();
	private DemarrageService service;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new DemarrageService(
				activationRepository, modeleRepository, versionRepository, instanceRepository,
				beneficiaireRepository, etapeExecutionRepository, tacheRepository, candidatRepository,
				historiqueRepository, catalogue, codec, resolutionService, referentielClient,
				planifService, avancementService, outboxService, txManager,
				Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC));
	}

	private DemarrerInstanceRequest req(String type, Map<String, Object> vars) {
		return new DemarrerInstanceRequest(type, null, UUID.randomUUID(), "DEM-202611-0001",
				UUID.randomUUID(), UUID.randomUUID(), "Sarra Khemiri", List.of(), vars);
	}

	@Test
	void idempotence_memeCle_renvoie200SansRecree() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		WfInstance existante = new WfInstance();
		existante.setId(UUID.randomUUID());
		existante.setStatut(StatutInstance.EN_COURS);
		when(instanceRepository.findByCleDemarrage("cle-1")).thenReturn(Optional.of(existante));

		ResultatDemarrage res = service.demarrer("cle-1", r);
		assertThat(res.cree()).isFalse();
		assertThat(res.reponse().instanceId()).isEqualTo(existante.getId());
	}

	@Test
	void instanceExistanteAutreCle_409_INSTANCE_EXISTANTE() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		when(instanceRepository.findByCleDemarrage("cle-2")).thenReturn(Optional.empty());
		when(instanceRepository.findByObjetTypeAndObjetId("CONGE", r.objetId())).thenReturn(Optional.of(new WfInstance()));

		assertThatThrownBy(() -> service.demarrer("cle-2", r))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "INSTANCE_EXISTANTE");
	}

	@Test
	void modeHerite_409_MODE_HERITE() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.HERITE);
		when(activationRepository.findByObjetType("CONGE")).thenReturn(Optional.of(act));

		assertThatThrownBy(() -> service.demarrer("cle-3", r))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "MODE_HERITE");
	}

	@Test
	void aucunModeleActifPublie_422_WORKFLOW_ABSENT() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("CONGE")).thenReturn(Optional.of(act));
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", null)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.demarrer("cle-4", r))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "WORKFLOW_ABSENT");
	}

	@Test
	void aucuneEtapeHumaine_effetImmediatApprouver() {
		DemarrerInstanceRequest r = req("HEURES_SUP", Map.of("cree_par_rh", true));
		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("HEURES_SUP")).thenReturn(Optional.of(act));

		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setCode("M09_HEURES_SUP");
		when(modeleRepository.findActifParObjetTypeEtSousType("HEURES_SUP", null)).thenReturn(Optional.of(m));

		EtapeDefinition etapeCond = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION",
				new ConditionNon(new ConditionFeuille("cree_par_rh", "egal", true)),
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(24, "HEURES", null), null,
				new EcheanceDefinition("EXPIRER", null, null), null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "HEURES_SUP", null, List.of(etapeCond));

		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setNumero(1);
		v.setStatut(StatutVersion.PUBLIEE);
		v.setDefinition(codec.serialiser(def));
		when(versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.of(v));
		when(versionRepository.getReferenceById(v.getId())).thenReturn(v);

		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(
				new LotResolu(Map.of("VALID_RH", new ResultatRegleResolue(
						List.of(new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), "RH", "RH")), false, false, null)),
						new ContexteDemandeur("RH", "DIRECTION", 12, false)));
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		when(instanceRepository.saveAndFlush(any(WfInstance.class))).thenAnswer(inv -> {
			WfInstance i = inv.getArgument(0);
			i.setId(UUID.randomUUID());
			return i;
		});

		ResultatDemarrage res = service.demarrer("cle-hs", r);
		assertThat(res.cree()).isTrue();
		assertThat(res.reponse().statut()).isEqualTo(StatutInstance.APPROUVEE);
		assertThat(res.reponse().effetImmediat()).isEqualTo("APPROUVER");
	}

	@Test
	void referentielIndisponible_503_SERVICE_INDISPONIBLE() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("CONGE")).thenReturn(Optional.of(act));
		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setCode("M01_CONGE");
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", null)).thenReturn(Optional.of(m));

		EtapeDefinition e1 = new EtapeDefinition("E1", "E1", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setNumero(1);
		v.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(e1))));
		when(versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.of(v));
		when(resolutionService.resoudre(any(), any(), any(), any(), any()))
				.thenThrow(WorkflowMetierException.indisponible("SERVICE_INDISPONIBLE", "Indisponible"));

		assertThatThrownBy(() -> service.demarrer("cle-503", r))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 503)
				.hasFieldOrPropertyWithValue("code", "SERVICE_INDISPONIBLE");
	}

	@Test
	void etapeBloquee_notifieComptesRhAdminResolusEtJamaisLitteralRh() {
		DemarrerInstanceRequest r = req("CONGE", Map.of());
		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("CONGE")).thenReturn(Optional.of(act));
		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setCode("M01_CONGE");
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", null)).thenReturn(Optional.of(m));

		EtapeDefinition e1 = new EtapeDefinition("E1", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setNumero(1);
		v.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(e1))));
		when(versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.of(v));
		when(versionRepository.getReferenceById(v.getId())).thenReturn(v);

		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(
				new LotResolu(Map.of("E1", new ResultatRegleResolue(List.of(), false, true, "AUCUN_TITULAIRE")),
						new ContexteDemandeur("COLLABORATEUR", "SERVICE", 12, false)));
		UUID rhCompte = UUID.randomUUID();
		UUID adminCompte = UUID.randomUUID();
		when(resolutionService.resoudreComptesRoles(any(), eq(List.of("RH", "ADMIN"))))
				.thenReturn(List.of(rhCompte, adminCompte));
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		when(instanceRepository.saveAndFlush(any(WfInstance.class))).thenAnswer(inv -> {
			WfInstance i = inv.getArgument(0);
			i.setId(UUID.randomUUID());
			return i;
		});
		when(etapeExecutionRepository.saveAndFlush(any(WfEtapeExecution.class))).thenAnswer(inv -> {
			WfEtapeExecution e = inv.getArgument(0);
			e.setId(UUID.randomUUID());
			return e;
		});
		when(tacheRepository.saveAndFlush(any(WfTache.class))).thenAnswer(inv -> {
			WfTache t = inv.getArgument(0);
			t.setId(UUID.randomUUID());
			return t;
		});

		ResultatDemarrage res = service.demarrer("cle-bloquee", r);

		assertThat(res.reponse().statut()).isEqualTo(StatutInstance.BLOQUEE);
		verify(outboxService).publierNotification(any(), eq(rhCompte.toString()), eq("WEBSOCKET"), eq("WF_BLOQUEE"),
				any(), any(), eq("CONGE"), any(), any());
		verify(outboxService).publierNotification(any(), eq(adminCompte.toString()), eq("WEBSOCKET"), eq("WF_BLOQUEE"),
				any(), any(), eq("CONGE"), any(), any());
		verify(outboxService, never()).publierNotification(any(), eq("RH"), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void sousTypeEnBrouillonSeul_utiliseModeleGeneriquePublie() {
		DemarrerInstanceRequest r = new DemarrerInstanceRequest(
				"CONGE", "MALADIE", UUID.randomUUID(), "DEM-202611-0099",
				UUID.randomUUID(), UUID.randomUUID(), "Sarra Khemiri", List.of(), Map.of());

		WfActivation act = new WfActivation();
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("CONGE")).thenReturn(Optional.of(act));

		WfModele modeleMaladie = new WfModele();
		modeleMaladie.setId(UUID.randomUUID());
		modeleMaladie.setCode("M01_CONGE_MALADIE");
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", "MALADIE")).thenReturn(Optional.of(modeleMaladie));
		when(versionRepository.findByModeleIdAndStatut(modeleMaladie.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.empty());

		WfModele modeleGenerique = new WfModele();
		modeleGenerique.setId(UUID.randomUUID());
		modeleGenerique.setCode("M01_CONGE");
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", null)).thenReturn(Optional.of(modeleGenerique));

		EtapeDefinition e1 = new EtapeDefinition("E1", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("PASSER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		WfModeleVersion vGen = new WfModeleVersion();
		vGen.setId(UUID.randomUUID());
		vGen.setModele(modeleGenerique);
		vGen.setNumero(1);
		vGen.setStatut(StatutVersion.PUBLIEE);
		vGen.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(e1))));
		when(versionRepository.findByModeleIdAndStatut(modeleGenerique.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.of(vGen));
		when(versionRepository.getReferenceById(vGen.getId())).thenReturn(vGen);

		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(
				new LotResolu(Map.of("E1", new ResultatRegleResolue(List.of(), true, false, "AUCUN")),
						new ContexteDemandeur("COLLABORATEUR", "SERVICE", 12, false)));
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		when(instanceRepository.saveAndFlush(any(WfInstance.class))).thenAnswer(inv -> {
			WfInstance i = inv.getArgument(0);
			i.setId(UUID.randomUUID());
			return i;
		});

		ResultatDemarrage res = service.demarrer("cle-gen", r);

		assertThat(res.cree()).isTrue();
		assertThat(res.reponse().statut()).isEqualTo(StatutInstance.APPROUVEE);
	}
}
