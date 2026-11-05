package com.hr.evaluation.service;

import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A3 : l'analyse mobile charge les questions (transaction de lecture) et ne porte jamais de niveau d'écart (Q4). */
class EvaluationAnalyseMobileTest {

	@Test
	void analyserPourMobile_chargeLesQuestionsEtNiveauEcartNul() {
		UUID evaluationId = UUID.randomUUID();
		EvaluationAnswerRepository answers = mock(EvaluationAnswerRepository.class);
		SkillAnswerRepository skills = mock(SkillAnswerRepository.class);
		EvaluationQuestion q = new EvaluationQuestion();
		q.setId(UUID.randomUUID());
		q.setLibelle("SQL");
		EvaluationAnswer a = new EvaluationAnswer();
		a.setQuestion(q);
		a.setNoteCollaborateur(5);
		a.setNoteManager(2);
		when(answers.findWithQuestionByEvaluationId(evaluationId)).thenReturn(List.of(a));
		when(skills.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId)).thenReturn(List.of());
		EvaluationAnalyseAdminService service = new EvaluationAnalyseAdminService(
				mock(EvaluationRepository.class), answers, skills,
				new EvaluationScoringService(3), mock(TemplateTechniqueEvaluation.class));

		EvaluationAnalyticsResponse reponse = service.analyserPourMobile(evaluationId);

		assertThat(reponse.gaps()).hasSize(1);
		assertThat(reponse.gaps().get(0).gap()).isEqualTo(3);
		assertThat(reponse.gaps().get(0).niveauEcart()).isNull();
		verify(answers, never()).findByEvaluationIdOrderByQuestionOrdreAsc(evaluationId);
	}

	/** Statistiques de campagne : questions chargées par la requête avec jointure (plus de chargement paresseux hors transaction). */
	@Test
	void analyserCampagne_chargeLesQuestionsDeChaqueEvaluation() {
		Evaluation evaluation = new Evaluation();
		evaluation.setId(UUID.randomUUID());
		EvaluationAnswerRepository answers = mock(EvaluationAnswerRepository.class);
		SkillAnswerRepository skills = mock(SkillAnswerRepository.class);
		EvaluationQuestion q = new EvaluationQuestion();
		q.setId(UUID.randomUUID());
		q.setLibelle("Communication");
		EvaluationAnswer a = new EvaluationAnswer();
		a.setQuestion(q);
		a.setNoteCollaborateur(4);
		a.setNoteManager(4);
		when(answers.findWithQuestionByEvaluationId(evaluation.getId())).thenReturn(List.of(a));
		when(skills.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluation.getId())).thenReturn(List.of());
		EvaluationAnalyseAdminService service = new EvaluationAnalyseAdminService(
				mock(EvaluationRepository.class), answers, skills,
				new EvaluationScoringService(3), mock(TemplateTechniqueEvaluation.class));

		List<EvaluationAnalyticsResponse> reponses = service.analyserCampagne(List.of(evaluation));

		assertThat(reponses).hasSize(1);
		assertThat(reponses.get(0).finalScore()).isNotNull();
		verify(answers, never()).findByEvaluationIdOrderByQuestionOrdreAsc(evaluation.getId());
	}
}
