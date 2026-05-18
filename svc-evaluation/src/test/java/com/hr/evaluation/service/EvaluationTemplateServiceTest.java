package com.hr.evaluation.service;

import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationTemplateServiceTest {

    @Mock
    private EvaluationTemplateRepository templateRepository;

    @Mock
    private EvaluationQuestionRepository questionRepository;

    private EvaluationTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new EvaluationTemplateService(templateRepository, questionRepository);
    }

    @Test
    void creerTemplateReussit() {
        UUID adminId = UUID.randomUUID();
        
        when(templateRepository.save(any(EvaluationTemplate.class)))
                .thenAnswer(invocation -> {
                    EvaluationTemplate t = invocation.getArgument(0);
                    t.setId(UUID.randomUUID());
                    return t;
                });

        EvaluationTemplate template = templateService.creerTemplate(
                "Template Évaluation Générale",
                "Questions standards pour évaluation",
                adminId
        );

        assertThat(template).isNotNull();
        assertThat(template.getName()).isEqualTo("Template Évaluation Générale");
        assertThat(template.isReutilisable()).isTrue();
        assertThat(template.isActif()).isTrue();
    }

    @Test
    void ajouterQuestionReussit() {
        UUID templateId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        EvaluationTemplate template = new EvaluationTemplate();
        template.setId(templateId);

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(questionRepository.save(any(EvaluationQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        templateService.ajouterQuestion(
                templateId,
                "Quels sont vos objectifs pour l'année prochaine?",
                QuestionType.TEXTE_LIBRE,
                1,
                true,
                null,
                null,
                null,
                adminId
        );

        verify(questionRepository, times(1)).save(any(EvaluationQuestion.class));
    }

    @Test
    void listerTemplatesRetourneListe() {
        when(templateRepository.findByActifTrueOrderByNameAsc())
                .thenReturn(List.of(new EvaluationTemplate(), new EvaluationTemplate()));

        List<EvaluationTemplate> templates = templateService.listerTemplates(false);

        assertThat(templates).hasSize(2);
    }
}
