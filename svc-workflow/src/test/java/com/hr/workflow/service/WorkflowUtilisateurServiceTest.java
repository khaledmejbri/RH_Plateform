package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheDetailResponse;
import com.hr.workflow.entity.WfBeneficiaire;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfInstanceRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests de visibilité § 9.10 (bénéficiaire résolu hors transaction via {@code collaborateurParCompte}).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowUtilisateurServiceTest {

	@Mock private WfActivationRepository activationRepository;
	@Mock private WfModeleVersionRepository versionRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfEtapeExecutionRepository etapeExecutionRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final Instant maintenant = Instant.parse("2026-11-20T10:00:00Z");

	private WorkflowUtilisateurService service;
	private UUID instanceId, tacheId, demandeurCompteId, benefCompteId, benefCollabId, tiersCompteId, tiersCollabId;
	private WfInstance instance;
	private WfTache tache;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new WorkflowUtilisateurService(
				catalogue, activationRepository, versionRepository, tacheRepository, candidatRepository,
				instanceRepository, etapeExecutionRepository, beneficiaireRepository, referentielClient,
				null, codec, txManager, Clock.fixed(maintenant, ZoneOffset.UTC));

		instanceId = UUID.randomUUID();
		tacheId = UUID.randomUUID();
		demandeurCompteId = UUID.randomUUID();
		benefCompteId = UUID.randomUUID();
		benefCollabId = UUID.randomUUID();
		tiersCompteId = UUID.randomUUID();
		tiersCollabId = UUID.randomUUID();

		EtapeDefinition e1 = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				null, null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null, null, null, null, null);
		WfModeleVersion version = new WfModeleVersion();
		version.setId(UUID.randomUUID());
		version.setNumero(1);
		version.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "FORMATION", null, List.of(e1))));

		instance = new WfInstance();
		instance.setId(instanceId);
		instance.setModeleVersion(version);
		instance.setModeleCode("M04_FORMATION");
		instance.setModeleVersionNumero(1);
		instance.setObjetType("FORMATION");
		instance.setObjetId(UUID.randomUUID());
		instance.setObjetNumero("FOR-202611-0001");
		instance.setDemandeurCompteId(demandeurCompteId);
		instance.setDemandeurNom("Manager");
		instance.setStatut(StatutInstance.EN_COURS);
		instance.setDemarreLe(maintenant);

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

		lenient().when(tacheRepository.findByIdAvecDetails(tacheId)).thenReturn(Optional.of(tache));
		lenient().when(instanceRepository.findByIdAvecVersion(instanceId)).thenReturn(Optional.of(instance));
		lenient().when(instanceRepository.findByObjetTypeAndObjetIdAvecVersion("FORMATION", instance.getObjetId()))
				.thenReturn(Optional.of(instance));
		lenient().when(etapeExecutionRepository.findByInstanceIdOrderByOrdreAsc(instanceId)).thenReturn(List.of(ee));
		lenient().when(tacheRepository.findByInstanceId(instanceId)).thenReturn(List.of(tache));
		lenient().when(beneficiaireRepository.findByIdInstanceId(instanceId))
				.thenReturn(List.of(WfBeneficiaire.of(instanceId, benefCollabId)));
		lenient().when(beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(instanceId, benefCollabId))
				.thenReturn(true);
		lenient().when(referentielClient.collaborateurParCompte(benefCompteId))
				.thenReturn(Optional.of(new CollaborateurResume(benefCollabId, benefCompteId, "Bénéficiaire", "M-01", "ACTIF")));
		lenient().when(referentielClient.collaborateurParCompte(tiersCompteId))
				.thenReturn(Optional.of(new CollaborateurResume(tiersCollabId, tiersCompteId, "Tiers", "M-02", "ACTIF")));
	}

	@Test
	void visibilite_beneficiaireVoitDetailTacheEtSuivi_tiersRecoit404() {
		TacheDetailResponse detail = service.detailTache(tacheId, benefCompteId, false, false);
		assertThat(detail.tacheId()).isEqualTo(tacheId);

		SuiviInstanceResponse suiviInst = service.suiviParInstance(instanceId, benefCompteId, false, false);
		assertThat(suiviInst.instanceId()).isEqualTo(instanceId);

		SuiviInstanceResponse suiviObj = service.suiviParObjet("FORMATION", instance.getObjetId(), benefCompteId, false, false);
		assertThat(suiviObj.instanceId()).isEqualTo(instanceId);

		assertThatThrownBy(() -> service.detailTache(tacheId, tiersCompteId, false, false))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 404);
		assertThatThrownBy(() -> service.suiviParInstance(instanceId, tiersCompteId, false, false))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 404);
	}
}
