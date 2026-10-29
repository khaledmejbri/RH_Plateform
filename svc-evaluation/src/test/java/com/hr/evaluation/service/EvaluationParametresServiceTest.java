package com.hr.evaluation.service;

import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.repository.EvaluationParametresRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SCRUM-41 : durée par défaut d'une campagne, réglable par la société.
 */
class EvaluationParametresServiceTest {

	private final EvaluationParametresRepository repository = mock(EvaluationParametresRepository.class);
	private final EvaluationParametresService service = new EvaluationParametresService(repository, 20);

	@Test
	void pasEncoreRegle_valeurParDefaut20() {
		when(repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)).thenReturn(Optional.empty());
		assertThat(service.dureeCampagneJours()).isEqualTo(20);
	}

	@Test
	void miseAJour10Jours() {
		when(repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
		assertThat(service.mettreAJour(10, UUID.randomUUID()).getDureeCampagneJours()).isEqualTo(10);
	}

	@Test
	void horsBornes_refuse() {
		assertThatThrownBy(() -> service.mettreAJour(0, UUID.randomUUID())).isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> service.mettreAJour(121, UUID.randomUUID())).isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> service.mettreAJour(null, UUID.randomUUID())).isInstanceOf(EvaluationMetierException.class);
	}
}
