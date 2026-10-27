package com.hr.evaluation.service;

import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationWorkflowMatchingTest {

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
                eventPublisher);
    }

    @Test
    void resolutionPrioriteFamilleNiveauPuisFamilleSeulePuisCampagne() {
        EvaluationTemplate exact = technical("DEV_LOGICIEL", "SENIOR");
        EvaluationTemplate familleSeule = technical("DEV_LOGICIEL", null);
        EvaluationTemplate fallbackCampagne = technical("SUPPORT_ADMIN", "JUNIOR");

        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setTemplateCompetence(fallbackCampagne);

        when(templateRepository.findPublishedByTypeFamilleAndNiveau(
                eq(TemplateType.TECHNICAL), eq(TemplateStatus.PUBLISHED), eq("DEV_LOGICIEL"), eq("SENIOR")))
                .thenReturn(List.of(exact));

        assertThat(workflowService.resoudreTemplateCompetence("DEV_LOGICIEL", "SENIOR", campaign))
                .isSameAs(exact);

        when(templateRepository.findPublishedByTypeFamilleAndNiveau(any(), any(), eq("DEV_LOGICIEL"), eq("JUNIOR")))
                .thenReturn(List.of());
        when(templateRepository.findPublishedByTypeAndFamilleSansNiveau(
                eq(TemplateType.TECHNICAL), eq(TemplateStatus.PUBLISHED), eq("DEV_LOGICIEL")))
                .thenReturn(List.of(familleSeule));

        assertThat(workflowService.resoudreTemplateCompetence("DEV_LOGICIEL", "JUNIOR", campaign))
                .isSameAs(familleSeule);

        when(templateRepository.findPublishedByTypeFamilleAndNiveau(any(), any(), eq("GENIE_CIVIL"), any()))
                .thenReturn(List.of());
        when(templateRepository.findPublishedByTypeAndFamilleSansNiveau(any(), any(), eq("GENIE_CIVIL")))
                .thenReturn(List.of());

        assertThat(workflowService.resoudreTemplateCompetence("GENIE_CIVIL", "SENIOR", campaign))
                .isSameAs(fallbackCampagne);
    }

    @Test
    void sansFamilleRetourneFallbackCampagneUniquement() {
        EvaluationTemplate fallback = technical("SUPPORT_ADMIN", null);
        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setTemplateCompetence(fallback);

        assertThat(workflowService.resoudreTemplateCompetence(null, "SENIOR", campaign))
                .isSameAs(fallback);
        assertThat(workflowService.resoudreTemplateCompetence(null, null, new EvaluationCampaign()))
                .isNull();
    }

    @Test
    void profilAccesNInfluencesPasMatching() {
        // Pas d'appel repo avec profil — résolution ignore totalement profil_acces
        EvaluationCampaign campaign = new EvaluationCampaign();
        assertThat(workflowService.resoudreTemplateCompetence("DEV_LOGICIEL", "SENIOR", campaign)).isNull();
    }

    private static EvaluationTemplate technical(String famille, String niveau) {
        EvaluationTemplate t = new EvaluationTemplate();
        t.setType(TemplateType.TECHNICAL);
        t.setStatut(TemplateStatus.PUBLISHED);
        t.setActif(true);
        t.setFamilleMetierCode(famille);
        t.setNiveauSeniorite(niveau);
        return t;
    }
}
