package com.hr.evaluation.service;

import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EvaluationTemplateService {

    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationQuestionRepository questionRepository;

    public EvaluationTemplateService(
            EvaluationTemplateRepository templateRepository,
            EvaluationQuestionRepository questionRepository) {
        this.templateRepository = templateRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public EvaluationTemplate creerTemplate(String nom, String description, UUID creePar) {
        EvaluationTemplate template = new EvaluationTemplate();
        template.setNom(nom);
        template.setDescription(description);
        template.setType(com.hr.evaluation.domain.TemplateType.GENERIC);
        template.setStatut(com.hr.evaluation.domain.TemplateStatus.DRAFT);
        template.setActif(true);
        template.setCreePar(creePar);
        return templateRepository.save(template);
    }

    @Transactional
    public void ajouterQuestion(
            UUID templateId,
            String libelle,
            QuestionType typeQuestion,
            Integer ordre,
            boolean obligatoire,
            String optionsReponses,
            Integer valeurMinimale,
            Integer valeurMaximale,
            UUID creePar) {

        EvaluationTemplate template = chargerTemplate(templateId);

        EvaluationQuestion question = new EvaluationQuestion();
        question.setTemplate(template);
        question.setLibelle(libelle);
        question.setTypeQuestion(typeQuestion);
        question.setOrdre(ordre);
        question.setObligatoire(obligatoire);
        
        // Convert options from String to List if provided
        if (optionsReponses != null && !optionsReponses.isEmpty()) {
            question.setOptionsReponses(java.util.Arrays.asList(optionsReponses.split(",")));
        }
        
        // Convert Integer to BigDecimal for min/max values
        if (valeurMinimale != null) {
            question.setValeurMinimale(java.math.BigDecimal.valueOf(valeurMinimale));
        }
        if (valeurMaximale != null) {
            question.setValeurMaximale(java.math.BigDecimal.valueOf(valeurMaximale));
        }
        
        question.setActif(true);
        question.setCreePar(creePar);

        questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public List<EvaluationTemplate> listerTemplates(boolean uniquementReutilisables) {
        // For backward compatibility, return all active templates
        return templateRepository.findAllByActifTrue();
    }

    @Transactional(readOnly = true)
    public EvaluationTemplate obtenirTemplate(UUID id) {
        return chargerTemplate(id);
    }

    @Transactional(readOnly = true)
    public List<EvaluationQuestion> obtenirQuestionsTemplate(UUID templateId) {
        return questionRepository.findByTemplateIdAndActifTrueOrderByOrdreAsc(templateId);
    }

    @Transactional
    public void desactiverTemplate(UUID id) {
        EvaluationTemplate template = chargerTemplate(id);
        template.setActif(false);
        templateRepository.save(template);
    }

    private EvaluationTemplate chargerTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template d'évaluation introuvable: " + id));
    }
}
