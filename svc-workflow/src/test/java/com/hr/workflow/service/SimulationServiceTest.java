package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulerVersionRequest;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.moteur.ResolutionIntervenantsService;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfModeleVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Test CA-06.7 : simulation du modèle congé long avec jours_decomptes=12 puis 3. */
@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

	@Mock private WfModeleVersionRepository versionRepository;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private PlatformTransactionManager txManager;

	@Test
	void ca06_7_simulationCongeLong_12JoursActiveDepartement_3JoursSauteDepartement() {
		CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
		DefinitionCodec codec = new DefinitionCodec();
		DefinitionValidateur validateur = new DefinitionValidateur(catalogue, codec);
		when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		EtapeDefinition eResp = new EtapeDefinition("VALID_RESP", "Validation du responsable", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("PASSER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		EtapeDefinition eDep = new EtapeDefinition("VALID_DEP", "Validation du chef de département", "VALIDATION",
				new ConditionFeuille("jours_decomptes", "sup", new BigDecimal("10")),
				new IntervenantsDefinition("RESPONSABLE_TYPE", Map.of("type_noeud", "DEPARTEMENT"), "UN_SEUL"),
				new RepliSiVideDefinition("PASSER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		EtapeDefinition eRh = new EtapeDefinition("VALID_RH", "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);

		WfModele m = new WfModele();
		m.setObjetType("CONGE");
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setModele(m);
		v.setDefinition(codec.serialiser(new DefinitionWorkflow(1, "CONGE", null, List.of(eResp, eDep, eRh))));

		when(versionRepository.findByIdAvecModele(v.getId())).thenReturn(Optional.of(v));
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());
		when(resolutionService.resoudre(any(), any(), any(), any(), any())).thenReturn(new LotResolu(
				Map.of(
						"VALID_RESP", new ResultatRegleResolue(
								List.of(new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), "Omar Belhadj", "Chef")), false, false, null),
						"VALID_DEP", new ResultatRegleResolue(
								List.of(new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), "Youssef Chaabane", "CD")), false, false, null),
						"VALID_RH", new ResultatRegleResolue(
								List.of(new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), "Amel Trabelsi", "DRH")), false, false, null)
				),
				new ContexteDemandeur("COLLABORATEUR", "SERVICE", 24, false)));

		SimulationService simService = new SimulationService(
				versionRepository, catalogue, codec, validateur, resolutionService, referentielClient,
				new PlanificationEcheanceService(), txManager,
				Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC));

		// 12 jours -> les 3 étapes sont ACTIVEE
		SimulationResponse sim12 = simService.simuler(v.getId(),
				new SimulerVersionRequest(UUID.randomUUID(), List.of(), Map.of("jours_decomptes", 12)));
		assertThat(sim12.etapes()).hasSize(3);
		assertThat(sim12.etapes().get(0).candidats().get(0).nom()).isEqualTo("Omar Belhadj");
		assertThat(sim12.etapes().get(1).statut()).isEqualTo("ACTIVEE");
		assertThat(sim12.etapes().get(1).candidats().get(0).nom()).isEqualTo("Youssef Chaabane");

		// 3 jours -> VALID_DEP est SAUTEE (Condition non remplie)
		SimulationResponse sim3 = simService.simuler(v.getId(),
				new SimulerVersionRequest(UUID.randomUUID(), List.of(), Map.of("jours_decomptes", 3)));
		assertThat(sim3.etapes().get(1).statut()).isEqualTo("SAUTEE");
		assertThat(sim3.etapes().get(1).raison()).containsIgnoringCase("Condition");
	}
}
