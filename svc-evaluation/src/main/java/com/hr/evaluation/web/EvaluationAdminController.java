package com.hr.evaluation.web;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.dto.*;
import com.hr.evaluation.entity.*;
import com.hr.evaluation.repository.*;
import com.hr.evaluation.service.EvaluationCampaignService;
import com.hr.evaluation.service.EvaluationTemplateService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.TechnicalTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin/RH Controller for evaluation management.
 * Provides endpoints for managing campaigns, templates, and monitoring evaluations.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/evaluations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EvaluationAdminController {

    private final EvaluationCampaignService campaignService;
    private final EvaluationTemplateService templateService;
    private final TechnicalTemplateService technicalTemplateService;
    private final EvaluationWorkflowService workflowService;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationCampaignRepository campaignRepository;

    public EvaluationAdminController(
            EvaluationCampaignService campaignService,
            EvaluationTemplateService templateService,
            TechnicalTemplateService technicalTemplateService,
            EvaluationWorkflowService workflowService,
            EvaluationRepository evaluationRepository,
            EvaluationCampaignRepository campaignRepository) {
        this.campaignService = campaignService;
        this.templateService = templateService;
        this.technicalTemplateService = technicalTemplateService;
        this.workflowService = workflowService;
        this.evaluationRepository = evaluationRepository;
        this.campaignRepository = campaignRepository;
    }

    // ========== CAMPAIGN MANAGEMENT ==========

    @GetMapping("/campaigns")
    public ResponseEntity<List<Map<String, Object>>> listCampaigns(
            @RequestParam(required = false) EvaluationCampaignStatus statut) {
        
        List<EvaluationCampaign> campaigns = campaignService.listerCampagnes(statut);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (EvaluationCampaign campaign : campaigns) {
            Map<String, Object> campaignMap = new HashMap<>();
            campaignMap.put("identifiant", campaign.getId().toString());
            campaignMap.put("nom", campaign.getNom());
            campaignMap.put("description", campaign.getDescription());
            campaignMap.put("type", campaign.getType().name());
            campaignMap.put("statut", campaign.getStatut().name());
            campaignMap.put("annee", campaign.getAnnee());
            campaignMap.put("moisDebut", campaign.getMoisDebut());
            campaignMap.put("moisFin", campaign.getMoisFin());
            campaignMap.put("dateDebut", campaign.getDateDebut());
            campaignMap.put("dateFin", campaign.getDateFin());
            campaignMap.put("creeLe", campaign.getCreeLe());
            
            // Include template information if available
            if (campaign.getTemplateGeneral() != null) {
                Map<String, String> templateInfo = new HashMap<>();
                templateInfo.put("identifiant", campaign.getTemplateGeneral().getId().toString());
                templateInfo.put("nom", campaign.getTemplateGeneral().getName());
                campaignMap.put("templateGeneral", templateInfo);
            }
            
            if (campaign.getTemplateTechnique() != null) {
                Map<String, String> templateInfo = new HashMap<>();
                templateInfo.put("identifiant", campaign.getTemplateTechnique().getId().toString());
                templateInfo.put("nom", campaign.getTemplateTechnique().getNom());
                campaignMap.put("templateTechnique", templateInfo);
            }
            
            response.add(campaignMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @Valid @RequestBody Map<String, Object> request) {
        
        String nom = (String) request.get("nom");
        String description = (String) request.get("description");
        String typeStr = (String) request.get("type");
        Integer annee = (Integer) request.get("annee");
        Integer moisDebut = (Integer) request.get("moisDebut");
        Integer moisFin = (Integer) request.get("moisFin");
        UUID creePar = UUID.fromString((String) request.get("creePar"));
        
        EvaluationCampaignType type = EvaluationCampaignType.valueOf(typeStr);
        
        EvaluationCampaign campaign = campaignService.creerCampagne(
            nom, description, type, annee, moisDebut, moisFin, creePar
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("identifiant", campaign.getId().toString());
        response.put("nom", campaign.getNom());
        response.put("statut", campaign.getStatut().name());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/campaigns/{id}/activate")
    public ResponseEntity<Void> activateCampaign(@PathVariable UUID id) {
        campaignService.activerCampagne(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/campaigns/{id}/terminate")
    public ResponseEntity<Void> terminateCampaign(@PathVariable UUID id) {
        campaignService.terminerCampagne(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/campaigns/{id}/assign-templates")
    public ResponseEntity<Void> assignTemplates(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        
        String templateGeneralId = request.get("templateGeneralId");
        String templateTechniqueId = request.get("templateTechniqueId");
        
        campaignService.assignerTemplates(
            id,
            templateGeneralId != null ? UUID.fromString(templateGeneralId) : null,
            templateTechniqueId != null ? UUID.fromString(templateTechniqueId) : null
        );
        
        return ResponseEntity.ok().build();
    }

    // ========== GENERAL TEMPLATE MANAGEMENT ==========

    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, Object>>> listTemplates() {
        // TODO: Implement template list method in service
        // For now, return empty list
        return ResponseEntity.ok(new ArrayList<>());
    }

    @PostMapping("/templates")
    public ResponseEntity<Map<String, Object>> createTemplate(
            @Valid @RequestBody Map<String, Object> request) {
        
        String nom = (String) request.get("nom");
        String description = (String) request.get("description");
        UUID creePar = UUID.fromString((String) request.get("creePar"));
        
        EvaluationTemplate template = templateService.creerTemplate(nom, description, creePar);
        
        Map<String, Object> response = new HashMap<>();
        response.put("identifiant", template.getId().toString());
        response.put("name", template.getName());
        response.put("creeLe", template.getCreeLe());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.desactiverTemplate(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/templates/{id}/questions")
    public ResponseEntity<List<Map<String, Object>>> getTemplateQuestions(@PathVariable UUID id) {
        List<EvaluationQuestion> questions = templateService.obtenirQuestionsTemplate(id);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (EvaluationQuestion question : questions) {
            Map<String, Object> questionMap = new HashMap<>();
            questionMap.put("identifiant", question.getId().toString());
            questionMap.put("libelle", question.getLibelle());
            questionMap.put("typeQuestion", question.getTypeQuestion().name());
            questionMap.put("ordre", question.getOrdre());
            questionMap.put("obligatoire", question.isObligatoire());
            questionMap.put("optionsReponses", question.getOptionsReponses());
            questionMap.put("valeurMinimale", question.getValeurMinimale());
            questionMap.put("valeurMaximale", question.getValeurMaximale());
            questionMap.put("actif", question.isActif());
            response.add(questionMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/templates/{id}/questions")
    public ResponseEntity<Map<String, Object>> addQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody Map<String, Object> request) {
        
        String libelle = (String) request.get("libelle");
        String typeStr = (String) request.get("typeQuestion");
        Integer ordre = (Integer) request.get("ordre");
        Boolean obligatoire = (Boolean) request.get("obligatoire");
        String optionsReponses = (String) request.get("optionsReponses");
        Integer valeurMinimale = request.get("valeurMinimale") != null ? 
            (Integer) request.get("valeurMinimale") : null;
        Integer valeurMaximale = request.get("valeurMaximale") != null ? 
            (Integer) request.get("valeurMaximale") : null;
        UUID creePar = request.get("creePar") != null ? 
            UUID.fromString((String) request.get("creePar")) : UUID.randomUUID();
        
        QuestionType type = QuestionType.valueOf(typeStr);
        
        templateService.ajouterQuestion(
            id, libelle, type, ordre, obligatoire, optionsReponses, valeurMinimale, valeurMaximale, creePar
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("libelle", libelle);
        response.put("typeQuestion", type.name());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/templates/{templateId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID templateId,
            @PathVariable UUID questionId) {
        templateService.desactiverTemplate(templateId);
        return ResponseEntity.ok().build();
    }

    // ========== TECHNICAL TEMPLATE MANAGEMENT ==========

    @GetMapping("/technical-templates")
    public ResponseEntity<List<Map<String, Object>>> listTechnicalTemplates() {
        // TODO: Implement technical template list method in service
        // For now, return empty list
        return ResponseEntity.ok(new ArrayList<>());
    }

    @PostMapping("/technical-templates")
    public ResponseEntity<Map<String, Object>> createTechnicalTemplate(
            @Valid @RequestBody Map<String, Object> request) {
        
        String nom = (String) request.get("nom");
        String description = (String) request.get("description");
        String niveauSeniorite = (String) request.get("niveauSeniorite");
        String role = (String) request.get("role");
        String domaine = (String) request.get("domaine");
        UUID creePar = UUID.fromString((String) request.get("creePar"));
        
        TechnicalTemplate template = technicalTemplateService.creerTemplate(
            nom, description, niveauSeniorite, role, domaine, creePar
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("identifiant", template.getId().toString());
        response.put("nom", template.getNom());
        response.put("niveauSeniorite", template.getNiveauSeniorite());
        response.put("role", template.getRoleMetier());
        response.put("domaine", template.getDepartement());
        response.put("creeLe", template.getCreeLe());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========== EVALUATION MONITORING ==========

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAllEvaluations() {
        List<Evaluation> evaluations = evaluationRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (Evaluation evaluation : evaluations) {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("identifiant", evaluation.getId().toString());
            evalMap.put("campaignNom", evaluation.getCampaign().getNom());
            evalMap.put("collaborateurIdentifiant", evaluation.getCollaborateurIdentifiant().toString());
            evalMap.put("superieurIdentifiant", evaluation.getSuperieurIdentifiant().toString());
            evalMap.put("statut", evaluation.getStatut().name());
            evalMap.put("etapeActuelle", evaluation.getEtapeActuelle().name());
            evalMap.put("scoreSur20", evaluation.getScoreSur20());
            evalMap.put("creeLe", evaluation.getCreeLe());
            response.add(evalMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEvaluationById(@PathVariable UUID id) {
        Evaluation evaluation = workflowService.obtenirEvaluation(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("identifiant", evaluation.getId().toString());
        response.put("campaignNom", evaluation.getCampaign().getNom());
        response.put("collaborateurIdentifiant", evaluation.getCollaborateurIdentifiant().toString());
        response.put("superieurIdentifiant", evaluation.getSuperieurIdentifiant().toString());
        response.put("statut", evaluation.getStatut().name());
        response.put("etapeActuelle", evaluation.getEtapeActuelle().name());
        response.put("scoreSur20", evaluation.getScoreSur20());
        response.put("creeLe", evaluation.getCreeLe());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/collaborateur/{collaborateurId}")
    public ResponseEntity<List<Map<String, Object>>> getEvaluationsByCollaborateur(
            @PathVariable UUID collaborateurId) {
        
        List<Evaluation> evaluations = workflowService.listerEvaluationsCollaborateur(collaborateurId);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (Evaluation evaluation : evaluations) {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("identifiant", evaluation.getId().toString());
            evalMap.put("campaignNom", evaluation.getCampaign().getNom());
            evalMap.put("statut", evaluation.getStatut().name());
            evalMap.put("etapeActuelle", evaluation.getEtapeActuelle().name());
            evalMap.put("scoreSur20", evaluation.getScoreSur20());
            evalMap.put("creeLe", evaluation.getCreeLe());
            response.add(evalMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/superieur/{superieurId}")
    public ResponseEntity<List<Map<String, Object>>> getEvaluationsBySuperieur(
            @PathVariable UUID superieurId) {
        
        List<Evaluation> evaluations = workflowService.listerEvaluationsManager(superieurId);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (Evaluation evaluation : evaluations) {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("identifiant", evaluation.getId().toString());
            evalMap.put("campaignNom", evaluation.getCampaign().getNom());
            evalMap.put("collaborateurIdentifiant", evaluation.getCollaborateurIdentifiant().toString());
            evalMap.put("statut", evaluation.getStatut().name());
            evalMap.put("etapeActuelle", evaluation.getEtapeActuelle().name());
            evalMap.put("scoreSur20", evaluation.getScoreSur20());
            evalMap.put("creeLe", evaluation.getCreeLe());
            response.add(evalMap);
        }
        
        return ResponseEntity.ok(response);
    }
}
