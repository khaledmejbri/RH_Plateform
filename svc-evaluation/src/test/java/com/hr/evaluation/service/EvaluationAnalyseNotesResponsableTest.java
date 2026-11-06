package com.hr.evaluation.service;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-E12.b : couleur seulement pour une évaluation notée par le responsable, jamais VERT par défaut. */
class EvaluationAnalyseNotesResponsableTest {

	private final EvaluationAnswerRepository answerRepository = mock(EvaluationAnswerRepository.class);
	private final EvaluationAnalyseAdminService service = new EvaluationAnalyseAdminService(
			mock(EvaluationRepository.class), answerRepository, mock(SkillAnswerRepository.class),
			new EvaluationScoringService(3), mock(TemplateTechniqueEvaluation.class));

	private static EvaluationAnswer note(UUID evaluationId, int noteManager) {
		Evaluation e = new Evaluation();
		e.setId(evaluationId);
		EvaluationAnswer a = new EvaluationAnswer();
		a.setEvaluation(e);
		a.setNoteManager(noteManager);
		return a;
	}

	@Test
	void evaluationSansNoteResponsableAbsenteDuResultat_nonEvaluee() {
		UUID sansNote = UUID.randomUUID();
		UUID noteeInsuffisante = UUID.randomUUID();
		when(answerRepository.findAvecNoteManagerParEvaluations(any())).thenReturn(List.of(
				note(noteeInsuffisante, 1), note(noteeInsuffisante, 2), note(noteeInsuffisante, 2), note(noteeInsuffisante, 5)));

		var r = service.notesResponsable(List.of(sansNote, noteeInsuffisante));

		assertThat(r).doesNotContainKey(sansNote);
		assertThat(r.get(noteeInsuffisante).aNote()).isTrue();
		assertThat(r.get(noteeInsuffisante).couleur()).isEqualTo(CouleurAlerteEvaluationRh.ROUGE);
	}

	@Test
	void listeVideNInterrogePasLeDepot() {
		assertThat(service.notesResponsable(List.of())).isEmpty();
		verifyNoInteractions(answerRepository);
	}
}
