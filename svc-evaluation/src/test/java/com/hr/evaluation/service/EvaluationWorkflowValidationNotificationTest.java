package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-32 : le responsable est notifié quand le collaborateur valide son auto-évaluation.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationWorkflowValidationNotificationTest {

    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private EvaluationCampaignRepository campaignRepository;
    @Mock
    private EvaluationAnswerRepository answerRepository;
    @Mock
    private SkillAnswerRepository skillAnswerRepository;
    @Mock
    private EvaluationQuestionRepository questionRepository;
    @Mock
    private TechnicalQuestionRepository technicalQuestionRepository;
    @Mock
    private EvaluationTemplateRepository templateRepository;
    @Mock
    private EvaluationScoringService scoringService;
    @Mock
    private EvaluationEventPublisher eventPublisher;
    @Mock
    private AlerteEcartService alerteEcartService;

    private EvaluationWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new EvaluationWorkflowService(
                evaluationRepository,
                campaignRepository,
                answerRepository,
                skillAnswerRepository,
                questionRepository,
                technicalQuestionRepository,
                templateRepository,
                scoringService,
                eventPublisher,
                alerteEcartService);
        when(scoringService.analyser(any(), any())).thenReturn(mock(EvaluationAnalyticsResponse.class));
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void validationCollaborateurNotifieLeResponsable() {
        Evaluation evaluation = evaluation();

        Evaluation saved = workflowService.validerParCollaborateur(evaluation.getId());

        assertThat(saved.getStatut()).isEqualTo(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
        verify(eventPublisher).publierValidationCollaborateur(saved);
    }

    @Test
    void secondeValidationNeRenotifiePas() {
        Evaluation evaluation = evaluation();
        evaluation.setValidationCollaborateurLe(Instant.now());

        workflowService.validerParCollaborateur(evaluation.getId());

        verify(eventPublisher, never()).publierValidationCollaborateur(any());
    }

    @Test
    void responsableDejaPasseNeRecoitPasDeNotification() {
        Evaluation evaluation = evaluation();
        evaluation.setValidationSuperieurLe(Instant.now());

        Evaluation saved = workflowService.validerParCollaborateur(evaluation.getId());

        assertThat(saved.getStatut()).isEqualTo(StatutEvaluationRh.VALIDEE);
        verify(eventPublisher, never()).publierValidationCollaborateur(any());
    }

    @Test
    void noteCollaborateurEtNoteRo_declenchentLeRecalculDesAlertesApresVerrouDeLEvaluation() {
        Evaluation evaluation = evaluation();
        UUID questionId = UUID.randomUUID();
        com.hr.evaluation.entity.EvaluationQuestion question = new com.hr.evaluation.entity.EvaluationQuestion();
        question.setId(questionId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        com.hr.evaluation.entity.EvaluationAnswer reponse = new com.hr.evaluation.entity.EvaluationAnswer();
        reponse.setQuestion(question);
        when(answerRepository.findByEvaluationIdAndQuestionId(evaluation.getId(), questionId))
                .thenReturn(Optional.of(reponse));

        workflowService.repondreQuestionCollaborateur(evaluation.getId(), questionId, "Avancé", 4);
        workflowService.repondreQuestionManager(evaluation.getId(), questionId, "Avancé", null, 2);

        org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(evaluationRepository, alerteEcartService);
        ordre.verify(evaluationRepository).findByIdForUpdate(evaluation.getId());
        ordre.verify(alerteEcartService).recalculerSiNecessaire(evaluation, question);
        verify(alerteEcartService, org.mockito.Mockito.times(2)).recalculerSiNecessaire(evaluation, question);
        assertThat(reponse.getNoteCollaborateur()).isEqualTo(4);
        assertThat(reponse.getNoteManager()).isEqualTo(2);
    }

    private Evaluation evaluation() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setCollaborateurIdentifiant(UUID.randomUUID());
        evaluation.setSuperieurIdentifiant(UUID.randomUUID());
        evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);
        when(evaluationRepository.findByIdWithCampaign(evaluation.getId())).thenReturn(Optional.of(evaluation));
        return evaluation;
    }
}
