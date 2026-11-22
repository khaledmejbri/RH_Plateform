package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.DelegationDtos.ReaffecterTacheRequest;
import com.hr.workflow.dto.SupervisionDtos.ReaffecterTacheResponse;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.OutboxService;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.repository.WfBeneficiaireRepository;
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
import java.time.temporal.ChronoUnit;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ReaffectationService} (CA-12.1, CA-12.2, D-W23).
 */
@ExtendWith(MockitoExtension.class)
class ReaffectationTest {

	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private OutboxService outboxService;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final PlanificationEcheanceService planifService = new PlanificationEcheanceService();
	private final Instant maintenant = Instant.parse("2026-11-20T09:00:00Z");
	private final Clock clock = Clock.fixed(maintenant, ZoneOffset.UTC);

	private ReaffectationService service;

	private UUID instanceId, tacheId, demandeurCollabId, demandeurCompteId, benefCollabId;
	private UUID rhCollabId, rhCompteId, drhCollabId, drhCompteId, ancienCollabId, ancienCompteId;
	private WfInstance instance;
	private WfTache tache;
	private WfTacheCandidat ancienCandidat;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new ReaffectationService(
				instanceRepository, tacheRepository, candidatRepository, beneficiaireRepository,
				historiqueRepository, catalogue, codec, referentielClient, planifService,
				outboxService, txManager, clock);

		instanceId = UUID.randomUUID();
		tacheId = UUID.randomUUID();
		demandeurCollabId = UUID.randomUUID();
		demandeurCompteId = UUID.randomUUID();
		benefCollabId = UUID.randomUUID();
		rhCollabId = UUID.randomUUID();
		rhCompteId = UUID.randomUUID();
		drhCollabId = UUID.randomUUID();
		drhCompteId = UUID.randomUUID();
		ancienCollabId = UUID.randomUUID();
		ancienCompteId = UUID.randomUUID();

		EtapeDefinition eRh = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
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
		instance.setObjetNumero("DEM-202611-0200");
		instance.setDemandeurCollaborateurId(demandeurCollabId);
		instance.setDemandeurCompteId(demandeurCompteId);
		instance.setDemandeurNom("RH Demandeur");
		instance.setStatut(StatutInstance.BLOQUEE);
		instance.setEtapeCouranteCode("VALID_RH");
		instance.setVariables(Map.of());

		WfEtapeExecution ee = new WfEtapeExecution();
		ee.setId(UUID.randomUUID());
		ee.setInstance(instance);
		ee.setEtapeCode("VALID_RH");
		ee.setOrdre(1);
		ee.setStatut(StatutEtapeExecution.ACTIVE);

		tache = new WfTache();
		tache.setId(tacheId);
		tache.setInstance(instance);
		tache.setEtapeExecution(ee);
		tache.setType(TypeTache.VALIDATION);
		tache.setLibelle("Validation RH");
		tache.setStatut(StatutTache.A_TRAITER);
		tache.setVersion(1L);

		ancienCandidat = new WfTacheCandidat();
		ancienCandidat.setId(new WfTacheCandidatId(tacheId, ancienCollabId));
		ancienCandidat.setCompteId(ancienCompteId);
		ancienCandidat.setNom("Ancien Valideur");
		ancienCandidat.setOrigine(OrigineCandidat.TITULAIRE);
		ancienCandidat.setAjouteLe(maintenant.minus(1, ChronoUnit.DAYS));

		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(tacheRepository.findByIdForUpdate(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId)).thenReturn(List.of());
		lenient().when(candidatRepository.findByIdTacheId(tacheId)).thenReturn(List.of());
		lenient().when(referentielClient.collaborateurParCompte(rhCompteId))
				.thenReturn(Optional.of(new CollaborateurResume(rhCollabId, rhCompteId, "RH02", "M-RH", "ACTIF")));
		lenient().when(referentielClient.collaborateurParId(drhCollabId))
				.thenReturn(Optional.of(new CollaborateurResume(drhCollabId, drhCompteId, "DRH01", "M-DRH", "ACTIF")));
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		lenient().when(tacheRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void ca12_1_reaffectationDebloqueInstanceBloquee_calculeEcheance_etNotifieNouveauxCandidats() {
		when(candidatRepository.findByIdTacheId(tacheId)).thenReturn(List.of(ancienCandidat));

		ReaffecterTacheResponse rep = service.reaffecter(
				tacheId,
				new ReaffecterTacheRequest(List.of(drhCollabId), "Aucun valideur RH disponible", 1L),
				rhCompteId, "RH02");

		assertThat(rep.statutInstance()).isEqualTo(StatutInstance.EN_COURS);
		assertThat(rep.echeanceLe()).isNotNull();
		assertThat(instance.getStatut()).isEqualTo(StatutInstance.EN_COURS);
		assertThat(ancienCandidat.getRetireLe()).isEqualTo(maintenant);

		ArgumentCaptor<WfTacheCandidat> candCap = ArgumentCaptor.forClass(WfTacheCandidat.class);
		verify(candidatRepository, atLeastOnce()).save(candCap.capture());
		assertThat(candCap.getAllValues()).anyMatch(c ->
				c.getId().getCollaborateurId().equals(drhCollabId)
						&& c.getOrigine() == OrigineCandidat.REAFFECTATION
						&& c.getRetireLe() == null);

		verify(outboxService).publierNotification(any(), eq(drhCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), eq("Demande à traiter"), any(), eq("CONGE"), any(), any());
		verify(outboxService).publierEvenementWorkflow(any(), eq("TACHE_CREEE"), eq(instance),
				eq("VALID_RH"), eq("Validation RH"), eq(rep.echeanceLe()));
		verify(historiqueRepository).save(argThat(h -> "REAFFECTEE".equals(h.getTypeEvenement())
				&& "RH02".equals(h.getActeurNom())));
	}

	@Test
	void reaffectation_reactiveCandidatAnciennementRetire_sansDoublonPk() {
		WfTacheCandidat drhRetire = new WfTacheCandidat();
		drhRetire.setId(new WfTacheCandidatId(tacheId, drhCollabId));
		drhRetire.setCompteId(drhCompteId);
		drhRetire.setNom("DRH01");
		drhRetire.setOrigine(OrigineCandidat.DELEGUE);
		drhRetire.setPourNom("Quelqu'un");
		drhRetire.setRetireLe(maintenant.minus(1, ChronoUnit.HOURS));
		when(candidatRepository.findByIdTacheId(tacheId)).thenReturn(List.of(drhRetire));

		service.reaffecter(tacheId, new ReaffecterTacheRequest(List.of(drhCollabId), "Réaffectation manuelle", 1L),
				rhCompteId, "RH02");

		assertThat(drhRetire.getRetireLe()).isNull();
		assertThat(drhRetire.getOrigine()).isEqualTo(OrigineCandidat.REAFFECTATION);
		assertThat(drhRetire.getPourNom()).isNull();
		verify(outboxService).publierNotification(any(), eq(drhCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), any(), any(), any(), any(), any());
	}

	@Test
	void ca12_2_reglesEtErreurs_i1_403_motif422_inactif422_traitee409_version409() {
		// 1. Motif trop court ou absent -> 422 MOTIF_OBLIGATOIRE
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(drhCollabId), "abc", 1L), rhCompteId, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "MOTIF_OBLIGATOIRE");

		// 2. Réaffecter vers le demandeur -> 403 AUTO_DECISION_INTERDITE (I1)
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(demandeurCollabId), "Motif valide", 1L), rhCompteId, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "AUTO_DECISION_INTERDITE");

		// 3. Appelant == demandeur -> 403 AUTO_DECISION_INTERDITE (I1, D-W23)
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(drhCollabId), "Motif valide", 1L), demandeurCompteId, "Demandeur"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "AUTO_DECISION_INTERDITE");

		// 4. Collaborateur cible inactif -> 422 COLLABORATEUR_INACTIF
		UUID inactifId = UUID.randomUUID();
		when(referentielClient.collaborateurParId(inactifId))
				.thenReturn(Optional.of(new CollaborateurResume(inactifId, UUID.randomUUID(), "Inactif", "M-IN", "INACTIF")));
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(inactifId), "Motif valide", 1L), rhCompteId, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 422)
				.hasFieldOrPropertyWithValue("code", "COLLABORATEUR_INACTIF");

		// 5. Version obsolète -> 409 MODIFIE_ENTRE_TEMPS
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(drhCollabId), "Motif valide", 99L), rhCompteId, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "MODIFIE_ENTRE_TEMPS");

		// 6. Tâche déjà traitée -> 409 TACHE_DEJA_TRAITEE
		tache.setStatut(StatutTache.TRAITEE);
		assertThatThrownBy(() -> service.reaffecter(
				tacheId, new ReaffecterTacheRequest(List.of(drhCollabId), "Motif valide", 1L), rhCompteId, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "TACHE_DEJA_TRAITEE");
	}

	@Test
	void c2_reaffectationEnCours_poseReaffecteeLeAvantSaveAndFlush() {
		instance.setStatut(StatutInstance.EN_COURS);
		tache.setEcheanceLe(maintenant.plus(6, ChronoUnit.HOURS));
		tache.setReaffecteeLe(null);

		service.reaffecter(
				tacheId,
				new ReaffecterTacheRequest(List.of(drhCollabId), "Réaffectation charge équipe", 1L),
				rhCompteId, "RH02");

		assertThat(tache.getReaffecteeLe()).isEqualTo(maintenant);
		verify(tacheRepository).saveAndFlush(argThat(saved -> maintenant.equals(saved.getReaffecteeLe())));
	}
}
