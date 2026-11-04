package com.hr.evaluation.service;

import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Résolution unique du template technique : assigné, sinon campagne, sinon résolution ; TECHNICAL seulement. */
class TemplateTechniqueEvaluationTest {

    private final EvaluationQuestionRepository questionRepository = mock(EvaluationQuestionRepository.class);
    private final EvaluationWorkflowService workflow = mock(EvaluationWorkflowService.class);
    private final TemplateTechniqueEvaluation resolution = new TemplateTechniqueEvaluation(questionRepository, workflow);

    private static EvaluationTemplate template(TemplateType type) {
        EvaluationTemplate t = new EvaluationTemplate();
        t.setId(UUID.randomUUID());
        t.setType(type);
        return t;
    }

    @Test
    void templateAssigneEstPrioritaire() {
        EvaluationTemplate assigne = template(TemplateType.TECHNICAL);
        EvaluationCampaign campagne = new EvaluationCampaign();
        campagne.setTemplateCompetence(template(TemplateType.TECHNICAL));
        Evaluation e = new Evaluation();
        e.setCampaign(campagne);
        e.setTemplateCompetenceAssigne(assigne);

        assertThat(resolution.resoudre(e)).containsSame(assigne);
        verifyNoInteractions(workflow);
    }

    @Test
    void sinonTemplateCompetenceDeLaCampagne() {
        EvaluationTemplate deCampagne = template(TemplateType.TECHNICAL);
        EvaluationCampaign campagne = new EvaluationCampaign();
        campagne.setTemplateCompetence(deCampagne);
        Evaluation e = new Evaluation();
        e.setCampaign(campagne);

        assertThat(resolution.resoudre(e)).containsSame(deCampagne);
    }

    @Test
    void sinonResolutionHeritee_quandUnNiveauEstConnu() {
        EvaluationTemplate resolu = template(TemplateType.TECHNICAL);
        Evaluation e = new Evaluation();
        e.setCampaign(new EvaluationCampaign());
        e.setNiveauSeniorite("SENIOR");
        when(workflow.resoudreTemplateCompetence(any(), any(), any())).thenReturn(resolu);

        assertThat(resolution.resoudre(e)).containsSame(resolu);
    }

    @Test
    void templateNonTechnical_ecarte_etAucunTemplate_listeVide() {
        Evaluation e = new Evaluation();
        e.setCampaign(new EvaluationCampaign());
        e.setTemplateCompetenceAssigne(template(TemplateType.GENERIC));

        assertThat(resolution.resoudre(e)).isEmpty();
        assertThat(resolution.questions(e)).isEmpty();

        Evaluation sans = new Evaluation();
        sans.setCampaign(new EvaluationCampaign());
        assertThat(resolution.resoudre(sans)).isEmpty();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void questionsActivesDuTemplateTechnique() {
        EvaluationTemplate assigne = template(TemplateType.TECHNICAL);
        EvaluationQuestion q = new EvaluationQuestion();
        q.setId(UUID.randomUUID());
        when(questionRepository.findByTemplateIdAndActifTrueOrderByOrdreAsc(assigne.getId())).thenReturn(List.of(q));
        Evaluation e = new Evaluation();
        e.setCampaign(new EvaluationCampaign());
        e.setTemplateCompetenceAssigne(assigne);

        assertThat(resolution.questions(e)).containsExactly(q);
        assertThat(resolution.idsQuestions(e)).containsExactly(q.getId());
    }
}
