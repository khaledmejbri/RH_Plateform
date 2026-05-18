package com.hr.evaluation.service;

import com.hr.evaluation.entity.TechnicalQuestion;
import com.hr.evaluation.entity.TechnicalTemplate;
import com.hr.evaluation.repository.TechnicalQuestionRepository;
import com.hr.evaluation.repository.TechnicalTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TechnicalTemplateService {

    private final TechnicalTemplateRepository templateRepository;
    private final TechnicalQuestionRepository questionRepository;

    public TechnicalTemplateService(
            TechnicalTemplateRepository templateRepository,
            TechnicalQuestionRepository questionRepository) {
        this.templateRepository = templateRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public TechnicalTemplate creerTemplate(
            String nom,
            String description,
            String niveauSeniorite,
            String roleMetier,
            String departement,
            UUID creePar) {

        TechnicalTemplate template = new TechnicalTemplate();
        template.setName(nom);
        template.setDescription(description);
        template.setNiveauSeniorite(niveauSeniorite);
        template.setRoleMetier(roleMetier);
        template.setDepartement(departement);
        template.setActif(true);
        template.setCreePar(creePar);

        return templateRepository.save(template);
    }

    @Transactional
    public void ajouterQuestionTechnique(
            UUID templateId,
            String competence,
            String description,
            String niveauxPermis,
            Integer ordre) {

        TechnicalTemplate template = chargerTemplate(templateId);

        TechnicalQuestion question = new TechnicalQuestion();
        question.setTemplate(template);
        question.setCompetence(competence);
        question.setDescription(description);
        question.setNiveauxPermis(niveauxPermis);
        question.setOrdre(ordre);
        question.setActif(true);

        questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public Optional<TechnicalTemplate> trouverTemplatePourProfil(
            String niveauSeniorite, 
            String roleMetier) {
        
        // Try exact match first
        Optional<TechnicalTemplate> exactMatch = templateRepository
                .findFirstByNiveauSenioriteAndRoleMetierAndActifTrue(niveauSeniorite, roleMetier);
        
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        // Fallback to seniority level only
        List<TechnicalTemplate> bySeniority = templateRepository
                .findByNiveauSenioriteAndActifTrue(niveauSeniorite);
        
        return bySeniority.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<TechnicalTemplate> listerTemplates() {
        return templateRepository.findByActifTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<TechnicalQuestion> obtenirQuestionsTemplate(UUID templateId) {
        return questionRepository.findByTemplateIdAndActifTrueOrderByOrdreAsc(templateId);
    }

    private TechnicalTemplate chargerTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template technique introuvable: " + id));
    }
}
