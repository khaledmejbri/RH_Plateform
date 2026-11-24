package com.hr.workflow.config;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.ErreurDefinition;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelesAmorceRunnerTest {

	@Mock
	private WfActivationRepository activationRepository;
	@Mock
	private WfModeleRepository modeleRepository;
	@Mock
	private WfModeleVersionRepository versionRepository;
	@Mock
	private VerrouPlanificateur verrouPlanificateur;
	@Mock
	private PlatformTransactionManager transactionManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final DefinitionValidateur validateur = new DefinitionValidateur(catalogue, codec);

	@Test
	void toutesLes7DefinitionsAmorcees_passentLeValidateur() {
		List<ModelesAmorceRunner.ModeleAmorce> defs = ModelesAmorceRunner.definitionsAmorcees();
		assertThat(defs).hasSize(7);
		for (ModelesAmorceRunner.ModeleAmorce spec : defs) {
			List<ErreurDefinition> erreurs = validateur.valider(spec.definition(), spec.objetType());
			assertThat(validateur.estValide(erreurs))
					.as("Modèle %s valide (erreurs=%s)", spec.code(), erreurs)
					.isTrue();
		}
	}

	@Test
	void amorcer_estIdempotent_cree7ModelesEt8ActivationsUneSeuleFois() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		Set<String> activationsExistantes = new HashSet<>();
		Set<String> modelesExistants = new HashSet<>();

		when(activationRepository.findByObjetType(anyString())).thenAnswer(inv -> {
			String type = inv.getArgument(0);
			return activationsExistantes.contains(type) ? Optional.of(new WfActivation()) : Optional.empty();
		});
		when(activationRepository.save(any(WfActivation.class))).thenAnswer(inv -> {
			WfActivation a = inv.getArgument(0);
			activationsExistantes.add(a.getObjetType());
			return a;
		});
		when(modeleRepository.existsByCode(anyString()))
				.thenAnswer(inv -> modelesExistants.contains(inv.getArgument(0)));
		when(modeleRepository.saveAndFlush(any(WfModele.class))).thenAnswer(inv -> {
			WfModele m = inv.getArgument(0);
			modelesExistants.add(m.getCode());
			return m;
		});
		lenient().when(versionRepository.saveAndFlush(any(WfModeleVersion.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		ModelesAmorceRunner runner = new ModelesAmorceRunner(
				activationRepository, modeleRepository, versionRepository,
				catalogue, codec, validateur, verrouPlanificateur, transactionManager,
				Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC));

		runner.amorcer();
		runner.amorcer();

		assertThat(activationsExistantes).hasSize(8).contains("POINTAGE");
		assertThat(modelesExistants).hasSize(7);
		assertThat(ModelesAmorceRunner.definitionsAmorcees())
				.extracting(ModelesAmorceRunner.ModeleAmorce::objetType)
				.doesNotContain("POINTAGE");
		verify(activationRepository, times(8)).save(any());
		verify(modeleRepository, times(7)).saveAndFlush(any());
		verify(versionRepository, times(7)).saveAndFlush(any());
	}
}
