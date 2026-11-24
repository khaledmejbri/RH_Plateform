package com.hr.workflow.service;

import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DefinitionWorkflow.OptionsWorkflow;
import com.hr.workflow.definition.NotificationsDynamiquesDefinition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.WorkflowMoteurDtos.RegleActivePointageResponse;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointageRegleInterneServiceTest {

	@Mock private WfActivationRepository activationRepository;
	@Mock private WfModeleRepository modeleRepository;
	@Mock private WfModeleVersionRepository versionRepository;
	@Mock private ModeleVersionService modeleVersionService;

	private PointageRegleInterneService service;

	@BeforeEach
	void setUp() {
		service = new PointageRegleInterneService(
				activationRepository, modeleRepository, versionRepository, modeleVersionService);
	}

	@Test
	void modeHerite_retourneRegleInactive() {
		WfActivation act = new WfActivation();
		act.setObjetType("POINTAGE");
		act.setMode(ModeActivation.HERITE);
		when(activationRepository.findByObjetType("POINTAGE")).thenReturn(Optional.of(act));

		RegleActivePointageResponse res = service.lireRegleActive();
		assertThat(res.actifEffectif()).isFalse();
		assertThat(res.mode()).isEqualTo(ModeActivation.HERITE);
		assertThat(res.notifierRo()).isFalse();
	}

	@Test
	void modeMoteur_sansModeleOuSansVersionPubliee_retourneRegleInactive() {
		WfActivation act = new WfActivation();
		act.setObjetType("POINTAGE");
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("POINTAGE")).thenReturn(Optional.of(act));
		when(modeleRepository.findActifParObjetTypeEtSousType("POINTAGE", null)).thenReturn(Optional.empty());

		RegleActivePointageResponse sansModele = service.lireRegleActive();
		assertThat(sansModele.actifEffectif()).isFalse();
		assertThat(sansModele.mode()).isEqualTo(ModeActivation.MOTEUR);

		WfModele modele = new WfModele();
		modele.setId(UUID.randomUUID());
		when(modeleRepository.findActifParObjetTypeEtSousType("POINTAGE", null)).thenReturn(Optional.of(modele));
		when(versionRepository.findByModeleIdAndStatut(modele.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.empty());

		RegleActivePointageResponse sansVersion = service.lireRegleActive();
		assertThat(sansVersion.actifEffectif()).isFalse();
	}

	@Test
	void modeMoteur_avecVersionPubliee_dedoublonneSitesEtRefleteNotifierRo() {
		WfActivation act = new WfActivation();
		act.setObjetType("POINTAGE");
		act.setMode(ModeActivation.MOTEUR);
		when(activationRepository.findByObjetType("POINTAGE")).thenReturn(Optional.of(act));

		UUID modeleId = UUID.randomUUID();
		WfModele modele = new WfModele();
		modele.setId(modeleId);
		modele.setCode("WF_POINTAGE_RO");
		when(modeleRepository.findActifParObjetTypeEtSousType("POINTAGE", null)).thenReturn(Optional.of(modele));

		UUID versionId = UUID.randomUUID();
		WfModeleVersion version = new WfModeleVersion();
		version.setId(versionId);
		version.setNumero(2);
		when(versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.PUBLIEE)).thenReturn(Optional.of(version));

		UUID site1 = UUID.randomUUID();
		UUID site2 = UUID.randomUUID();
		DefinitionWorkflow def = new DefinitionWorkflow(1, "POINTAGE", new OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(
						true,
						"SITES_SELECTIONNES",
						List.of(site1.toString(), site1.toString(), site2.toString()),
						List.of("ENTREE", "SORTIE", "ENTREE"),
						"BOTH"));
		when(modeleVersionService.chargerDefinition(versionId)).thenReturn(def);

		RegleActivePointageResponse res = service.lireRegleActive();
		assertThat(res.actifEffectif()).isTrue();
		assertThat(res.mode()).isEqualTo(ModeActivation.MOTEUR);
		assertThat(res.modeleCode()).isEqualTo("WF_POINTAGE_RO");
		assertThat(res.versionNumero()).isEqualTo(2);
		assertThat(res.notifierRo()).isTrue();
		assertThat(res.porteeSites()).isEqualTo("SITES_SELECTIONNES");
		assertThat(res.siteIds()).containsExactly(site1, site2);
		assertThat(res.typesPointage()).containsExactly("ENTREE", "SORTIE");
		assertThat(res.canal()).isEqualTo("BOTH");
	}
}
