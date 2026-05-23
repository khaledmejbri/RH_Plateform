package com.hr.evaluation.service;

import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CreateTemplateRequest;
import com.hr.evaluation.dto.CreateQuestionRequest;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TemplateService {

    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationQuestionRepository questionRepository;

    public TemplateService(EvaluationTemplateRepository templateRepository,
                          EvaluationQuestionRepository questionRepository) {
        this.templateRepository = templateRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Create a new template with questions
     */
    public EvaluationTemplate creerTemplate(CreateTemplateRequest request, UUID userId) {
        EvaluationTemplate template = new EvaluationTemplate();
        template.setNom(request.getNom());
        template.setDescription(request.getDescription());
        template.setType(TemplateType.valueOf(request.getType()));
        template.setCreePar(userId);
        
        // Set technical template fields if applicable
        if (TemplateType.TECHNICAL.name().equals(request.getType())) {
            template.setNiveauSeniorite(request.getNiveauSeniorite());
            template.setRole(request.getRole());
            template.setDomaine(request.getDomaine());
        }
        
        // Save template first
        template = templateRepository.save(template);
        
        // Add questions if provided
        final EvaluationTemplate savedTemplate = template; // Make effectively final for lambda
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            List<EvaluationQuestion> questions = request.getQuestions().stream()
                .map(qDto -> convertToQuestion(qDto, savedTemplate))
                .collect(Collectors.toList());
            
            questionRepository.saveAll(questions);
            template.setQuestions(questions);
        }
        
        return template;
    }

    /**
     * Publish a template (make it available for assignment)
     */
    public EvaluationTemplate publierTemplate(UUID templateId, UUID userId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new RuntimeException("Only draft templates can be published");
        }
        
        template.setStatut(TemplateStatus.PUBLISHED);
        template.setPublieLe(Instant.now());
        template.setPubliePar(userId);
        
        return templateRepository.save(template);
    }

    /**
     * Archive a template
     */
    public void archiverTemplate(UUID templateId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        
        template.setStatut(TemplateStatus.ARCHIVED);
        template.setActif(false);
        templateRepository.save(template);
    }

    /**
     * Add question to existing template
     */
    public EvaluationQuestion ajouterQuestion(UUID templateId, CreateQuestionRequest request) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new RuntimeException("Can only add questions to draft templates");
        }
        
        EvaluationQuestion question = convertToQuestion(request, template);
        return questionRepository.save(question);
    }

    /**
     * Delete question from template
     */
    public void supprimerQuestion(UUID templateId, UUID questionId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new RuntimeException("Can only delete questions from draft templates");
        }
        
        questionRepository.deleteById(questionId);
    }

    /**
     * Reorder questions in template
     */
    public void reorderQuestions(UUID templateId, List<UUID> questionIdsInOrder) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new RuntimeException("Can only reorder questions in draft templates");
        }
        
        List<EvaluationQuestion> questions = questionRepository.findAllById(questionIdsInOrder);
        
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setOrdre(i + 1);
        }
        
        questionRepository.saveAll(questions);
    }

    /**
     * Get all templates by type and status
     */
    @Transactional(readOnly = true)
    public List<EvaluationTemplate> listerTemplates(String type, String statut) {
        if (type != null && statut != null) {
            return templateRepository.findByTypeAndStatutWithQuestions(
                TemplateType.valueOf(type), 
                TemplateStatus.valueOf(statut)
            );
        } else if (type != null) {
            return templateRepository.findByTypeWithQuestions(TemplateType.valueOf(type));
        } else if (statut != null) {
            return templateRepository.findByStatutWithQuestions(TemplateStatus.valueOf(statut));
        }
        return templateRepository.findAllByActifTrueWithQuestions();
    }

    /**
     * Get template by ID with questions
     */
    @Transactional(readOnly = true)
    public EvaluationTemplate getTemplateWithQuestions(UUID templateId) {
        return templateRepository.findByIdWithQuestions(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));
    }

    // Helper method
    private EvaluationQuestion convertToQuestion(CreateQuestionRequest dto, EvaluationTemplate template) {
        EvaluationQuestion question = new EvaluationQuestion();
        question.setTemplate(template);
        question.setLibelle(dto.getLibelle());
        question.setDescription(dto.getDescription());
        question.setTypeQuestion(com.hr.evaluation.domain.QuestionType.valueOf(dto.getTypeQuestion()));
        question.setOrdre(dto.getOrdre());
        question.setObligatoire(dto.getObligatoire() != null ? dto.getObligatoire() : false);
        question.setSectionCode(dto.getSectionCode());
        question.setSectionLibelle(dto.getSectionLibelle());
        question.setPoids(dto.getPoids() != null ? dto.getPoids() : java.math.BigDecimal.ONE);
        question.setLabelsEchelle(dto.getLabelsEchelle());
        
        // Convert options to JSONB
        if (dto.getOptionsReponses() != null && !dto.getOptionsReponses().isEmpty()) {
            question.setOptionsReponses(dto.getOptionsReponses());
        }
        
        question.setValeurMinimale(dto.getValeurMinimale());
        question.setValeurMaximale(dto.getValeurMaximale());
        question.setUniteMesure(dto.getUniteMesure());
        question.setPlaceholder(dto.getPlaceholder());
        question.setRegexPattern(dto.getRegexPattern());
        question.setMinLongueur(dto.getMinLongueur());
        question.setMaxLongueur(dto.getMaxLongueur());
        
        return question;
    }
}
