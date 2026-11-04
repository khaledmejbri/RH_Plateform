package com.hr.evaluation.service;

import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Résolution (sans état) du template technique et de ses questions pour une évaluation : une seule vérité pour le
 * mobile, la complétude de la validation RO (D-E10) et la détection des écarts (D-E04). Reprend la résolution de
 * {@code getTechnicalQuestions} pour la voie {@code evaluation_answer} : template assigné, sinon template compétence de
 * la campagne, sinon résolution par famille × niveau ; seul un template de type {@code TECHNICAL} est retenu.
 * <p>
 * L'évaluation doit avoir été chargée avec campagne et templates ({@code findByIdWithCampaign}).
 */
@Service
public class TemplateTechniqueEvaluation {

    private final EvaluationQuestionRepository questionRepository;
    private final EvaluationWorkflowService workflowService;

    public TemplateTechniqueEvaluation(EvaluationQuestionRepository questionRepository,
                                       @Lazy EvaluationWorkflowService workflowService) {
        this.questionRepository = questionRepository;
        this.workflowService = workflowService;
    }

    public Optional<EvaluationTemplate> resoudre(Evaluation evaluation) {
        EvaluationTemplate template = evaluation.getTemplateCompetenceAssigne() != null
                ? evaluation.getTemplateCompetenceAssigne()
                : (evaluation.getCampaign() != null ? evaluation.getCampaign().getTemplateCompetence() : null);

        if (template == null && evaluation.getNiveauSeniorite() != null) {
            // Même appel que la voie historique du contrôleur mobile (arguments dans le même ordre qu'avant).
            template = workflowService.resoudreTemplateCompetence(
                    evaluation.getNiveauSeniorite(),
                    evaluation.getRoleMetier(),
                    evaluation.getCampaign());
        }
        return Optional.ofNullable(template).filter(t -> t.getType() == TemplateType.TECHNICAL);
    }

    /** Questions techniques actives dans l'ordre ; liste vide si l'évaluation n'a pas de template technique. */
    public List<EvaluationQuestion> questions(Evaluation evaluation) {
        return resoudre(evaluation)
                .map(t -> questionRepository.findByTemplateIdAndActifTrueOrderByOrdreAsc(t.getId()))
                .orElse(List.of());
    }

    public Set<UUID> idsQuestions(Evaluation evaluation) {
        return questions(evaluation).stream().map(EvaluationQuestion::getId).collect(Collectors.toSet());
    }
}
