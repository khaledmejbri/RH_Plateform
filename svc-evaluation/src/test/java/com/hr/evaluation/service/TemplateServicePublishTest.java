package com.hr.evaluation.service;

import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CreateQuestionRequest;
import com.hr.evaluation.dto.CreateTemplateRequest;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServicePublishTest {

    @Mock
    private EvaluationTemplateRepository templateRepository;
    @Mock
    private EvaluationQuestionRepository questionRepository;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        var client = org.mockito.Mockito.mock(com.hr.evaluation.client.ReferentielParametresClient.class);
        org.mockito.Mockito.lenient().when(client.utiliser(org.mockito.ArgumentMatchers.anyList())).thenReturn(
                new com.hr.evaluation.client.ReferentielParametresClient.Resultat(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        templateService = new TemplateService(templateRepository, questionRepository, client,
                ParametresTestes.parDefaut());
    }

    @Test
    void creerTechnicalAccepteFamilleMetierCode() {
        when(templateRepository.save(any(EvaluationTemplate.class))).thenAnswer(inv -> {
            EvaluationTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTemplateRequest req = new CreateTemplateRequest();
        req.setNom("Compétences Dev Senior");
        req.setType("TECHNICAL");
        req.setFamilleMetierCode("DEV_LOGICIEL");
        req.setNiveauSeniorite("SENIOR");
        req.setCreePar(UUID.randomUUID());

        EvaluationTemplate saved = templateService.creerTemplate(req, req.getCreePar());
        assertThat(saved.getFamilleMetierCode()).isEqualTo("DEV_LOGICIEL");
        assertThat(saved.getNiveauSeniorite()).isEqualTo("SENIOR");
        assertThat(saved.getRole()).isEqualTo("DEV_LOGICIEL");
    }

    @Test
    void publierTechnicalSansFamilleRefuse() {
        UUID id = UUID.randomUUID();
        EvaluationTemplate t = new EvaluationTemplate();
        t.setId(id);
        t.setType(TemplateType.TECHNICAL);
        t.setStatut(TemplateStatus.DRAFT);
        t.setQuestions(List.of(questionActive()));
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> templateService.publierTemplate(id, UUID.randomUUID()))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> assertThat(((EvaluationMetierException) ex).getCode())
                        .isEqualTo("TECHNICAL_PROFIL_INCOMPLET"));
    }

    @Test
    void publierSansQuestionRefuse() {
        UUID id = UUID.randomUUID();
        EvaluationTemplate t = new EvaluationTemplate();
        t.setId(id);
        t.setType(TemplateType.GENERIC);
        t.setStatut(TemplateStatus.DRAFT);
        t.setQuestions(new ArrayList<>());
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));
        when(templateRepository.findByIdWithQuestions(id)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> templateService.publierTemplate(id, UUID.randomUUID()))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> assertThat(((EvaluationMetierException) ex).getCode())
                        .isEqualTo("TEMPLATE_SANS_QUESTION"));
    }

    private static EvaluationQuestion questionActive() {
        EvaluationQuestion q = new EvaluationQuestion();
        q.setLibelle("Q1");
        q.setTypeQuestion(QuestionType.TEXT);
        q.setActif(true);
        q.setOrdre(1);
        return q;
    }
}
