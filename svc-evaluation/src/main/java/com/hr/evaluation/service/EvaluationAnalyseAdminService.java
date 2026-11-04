package com.hr.evaluation.service;

import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Analyse d'une évaluation pour le détail web (D-E09.c) : les compétences du template technique reçoivent leur niveau
 * d'écart (Aligné / Orange / Rouge / Urgente), calculé par {@link EcartEtoiles} comme l'alerte. En transaction de
 * lecture avec les questions chargées (open-in-view désactivé). Le mobile n'utilise pas cette voie : ni le collaborateur
 * ni le RO ne voient de niveau d'alerte.
 */
@Service
public class EvaluationAnalyseAdminService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationScoringService scoringService;
    private final TemplateTechniqueEvaluation templateTechnique;

    public EvaluationAnalyseAdminService(EvaluationRepository evaluationRepository,
                                         EvaluationAnswerRepository answerRepository,
                                         SkillAnswerRepository skillAnswerRepository,
                                         EvaluationScoringService scoringService,
                                         TemplateTechniqueEvaluation templateTechnique) {
        this.evaluationRepository = evaluationRepository;
        this.answerRepository = answerRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.scoringService = scoringService;
        this.templateTechnique = templateTechnique;
    }

    @Transactional(readOnly = true)
    public EvaluationAnalyticsResponse analyser(UUID evaluationId) {
        Evaluation evaluation = evaluationRepository.findByIdWithCampaign(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable: " + evaluationId));
        return scoringService.analyser(
                answerRepository.findWithQuestionByEvaluationId(evaluationId),
                skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId),
                templateTechnique.idsQuestions(evaluation));
    }

    /**
     * Analyse pour le mobile (collaborateur, RO) : transaction de lecture, questions chargées, aucun niveau d'écart
     * ({@code niveauEcart} nul, Q4 : ni le collaborateur ni le RO ne voient l'alerte).
     */
    @Transactional(readOnly = true)
    public EvaluationAnalyticsResponse analyserPourMobile(UUID evaluationId) {
        return scoringService.analyser(
                answerRepository.findWithQuestionByEvaluationId(evaluationId),
                skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId));
    }
}
