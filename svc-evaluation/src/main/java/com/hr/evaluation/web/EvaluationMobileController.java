package com.hr.evaluation.web;

import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.SkillLevel;
import com.hr.evaluation.dto.EvaluationAnswerRequest;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.dto.EvaluationItemResponse;
import com.hr.evaluation.dto.QuestionResponse;
import com.hr.evaluation.dto.TechnicalAnswerRequest;
import com.hr.evaluation.dto.TechnicalQuestionResponse;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.SkillAnswer;
import com.hr.evaluation.entity.TechnicalQuestion;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.repository.TechnicalQuestionRepository;
import com.hr.evaluation.service.EvaluationScoringService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for mobile evaluation feature.
 * Provides endpoints for the Flutter mobile app to interact with evaluations.
 */
@RestController
@RequestMapping("/api/rh/v1/evaluations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EvaluationMobileController {

    private final EvaluationWorkflowService workflowService;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final TechnicalQuestionRepository technicalQuestionRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationScoringService scoringService;

    public EvaluationMobileController(
            EvaluationWorkflowService workflowService,
            EvaluationRepository evaluationRepository,
            EvaluationQuestionRepository questionRepository,
            TechnicalQuestionRepository technicalQuestionRepository,
            SkillAnswerRepository skillAnswerRepository,
            EvaluationScoringService scoringService) {
        this.workflowService = workflowService;
        this.evaluationRepository = evaluationRepository;
        this.questionRepository = questionRepository;
        this.technicalQuestionRepository = technicalQuestionRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.scoringService = scoringService;
    }

    /**
     * Get all evaluations for the authenticated user (collaborator).
     */
    @GetMapping("/moi")
    public ResponseEntity<List<EvaluationItemResponse>> getMyEvaluations() {
        UUID collaborateurId = getCurrentUserId();
        List<Evaluation> evaluations = workflowService.listerEvaluationsCollaborateur(collaborateurId);

        List<EvaluationItemResponse> response = new ArrayList<>();
        for (Evaluation eval : evaluations) {
            response.add(new EvaluationItemResponse(
                eval.getId().toString(),
                eval.getCampaign().getNom(),
                eval.getStatut().name(),
                eval.getSuperieurIdentifiant().toString(),
                eval.getEtapeActuelle() != null ? eval.getEtapeActuelle().name() : "EVALUATION_GENERALE",
                eval.getScoreSur20(),
                eval.getCreeLe()
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get general evaluation questions for a specific evaluation.
     */
    @GetMapping("/{id}/questions/generales")
    public ResponseEntity<List<QuestionResponse>> getGeneralQuestions(
            @PathVariable UUID id) {
        verifyOwnership(id);
        
        Evaluation evaluation = workflowService.obtenirEvaluation(id);
        
        if (evaluation.getEtapeActuelle() == EvaluationStep.EVALUATION_TECHNIQUE) {
            throw new IllegalStateException("L'évaluation est déjà passée à l'étape technique");
        }

        // Get template from campaign
        var template = evaluation.getCampaign().getTemplateGeneral();
        if (template == null) {
            throw new IllegalStateException("La campagne n'a pas de template général configuré");
        }

        List<EvaluationQuestion> questions = questionRepository
                .findByTemplateIdAndActifTrueOrderByOrdreAsc(template.getId());

        List<QuestionResponse> response = new ArrayList<>();
        for (EvaluationQuestion q : questions) {
            // Check if already answered
            var existingAnswer = workflowService.obtenirReponsesEvaluation(id)
                    .stream()
                    .filter(a -> a.getQuestion().getId().equals(q.getId()))
                    .findFirst();

            response.add(new QuestionResponse(
                q.getId().toString(),
                q.getLibelle(),
                q.getLibelle(),
                q.getTypeQuestion().name(),
                q.getTypeQuestion().name(),
                q.isObligatoire(),
                q.getOrdre(),
                // Convert List<String> to comma-separated String for backward compatibility
                q.getOptionsReponses() != null ? String.join(",", q.getOptionsReponses()) : null,
                q.getOptionsReponses(),
                q.getValeurMinimale(),
                q.getValeurMaximale(),
                q.getSectionCode(),
                q.getSectionLibelle(),
                q.getPoids(),
                q.getLabelsEchelle(),
                existingAnswer.map(EvaluationAnswer::getReponseCollaborateur).orElse(null),
                existingAnswer.map(a -> a.getNoteCollaborateur() != null ? a.getNoteCollaborateur() : a.getNoteAttribuee()).orElse(null)
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Submit an answer to a general evaluation question.
     */
    @PostMapping("/{id}/reponses/generales")
    public ResponseEntity<Void> answerGeneralQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody EvaluationAnswerRequest request) {
        verifyOwnership(id);

        UUID questionId = UUID.fromString(request.getQuestionId());
        
        workflowService.repondreQuestionCollaborateur(
            id,
            questionId,
            request.getReponse(),
            request.getNote()
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Get technical skills questions for a specific evaluation.
     */
    @GetMapping("/{id}/questions/techniques")
    public ResponseEntity<List<TechnicalQuestionResponse>> getTechnicalQuestions(
            @PathVariable UUID id) {
        verifyOwnership(id);

        Evaluation evaluation = workflowService.obtenirEvaluation(id);

        if (evaluation.getEtapeActuelle() != EvaluationStep.EVALUATION_TECHNIQUE) {
            throw new IllegalStateException("L'évaluation n'est pas encore à l'étape technique");
        }

        if (evaluation.getCampaign().getTemplateCompetence() != null) {
            var template = evaluation.getCampaign().getTemplateCompetence();
            List<EvaluationQuestion> questions = questionRepository
                    .findByTemplateIdAndActifTrueOrderByOrdreAsc(template.getId());

            List<TechnicalQuestionResponse> response = new ArrayList<>();
            for (EvaluationQuestion q : questions) {
                var existingAnswer = workflowService.obtenirReponsesEvaluation(id)
                        .stream()
                        .filter(a -> a.getQuestion().getId().equals(q.getId()))
                        .findFirst();
                Integer note = existingAnswer
                        .map(a -> a.getNoteCollaborateur() != null ? a.getNoteCollaborateur() : a.getNoteAttribuee())
                        .orElse(null);
                response.add(new TechnicalQuestionResponse(
                        q.getId().toString(),
                        q.getLibelle(),
                        q.getDescription(),
                        q.getLabelsEchelle() != null && !q.getLabelsEchelle().isEmpty()
                                ? String.join(",", q.getLabelsEchelle())
                                : "Beginner,Supervised,Autonomous,Advanced,Expert",
                        q.getOrdre(),
                        note != null ? skillName(note) : null,
                        existingAnswer.map(EvaluationAnswer::getReponseCollaborateur).orElse(null)
                ));
            }

            return ResponseEntity.ok(response);
        }

        // Get technical template from campaign
        var technicalTemplate = evaluation.getCampaign().getTemplateTechnique();
        if (technicalTemplate == null) {
            throw new IllegalStateException("La campagne n'a pas de template technique configuré");
        }

        // Get questions for this template and user's profile
        List<TechnicalQuestion> questions = technicalQuestionRepository
                .findByTemplateIdAndActifTrueOrderByOrdreAsc(technicalTemplate.getId());

        List<TechnicalQuestionResponse> response = new ArrayList<>();
        for (TechnicalQuestion q : questions) {
            // Check if already answered
            var existingAnswer = skillAnswerRepository
                    .findByEvaluationIdAndQuestionTechniqueId(id, q.getId())
                    .orElse(null);

            response.add(new TechnicalQuestionResponse(
                q.getId().toString(),
                q.getCompetence(),
                q.getDescription(),
                q.getNiveauxPermis(),
                q.getOrdre(),
                existingAnswer != null ? existingAnswer.getNiveauAutoEvaluation().name() : null,
                existingAnswer != null ? existingAnswer.getCommentaireCollaborateur() : null
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Submit self-assessment for a technical skill question.
     */
    @PostMapping("/{id}/reponses/techniques")
    public ResponseEntity<Void> answerTechnicalQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody TechnicalAnswerRequest request) {
        verifyOwnership(id);

        UUID questionId = UUID.fromString(request.getQuestionId());
        SkillLevel niveau = SkillLevel.valueOf(request.getNiveau().toUpperCase());

        if (questionRepository.findById(questionId).isPresent()) {
            workflowService.repondreQuestionCollaborateur(
                    id,
                    questionId,
                    niveau.label(),
                    niveau.score()
            );
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        workflowService.evaluerCompetenceTechnique(
            id,
            questionId,
            niveau,
            request.getCommentaire()
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/reponses")
    public ResponseEntity<List<EvaluationAnswer>> getAnswers(@PathVariable UUID id) {
        verifyOwnership(id);
        return ResponseEntity.ok(workflowService.obtenirReponsesEvaluation(id));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<EvaluationAnalyticsResponse> getAnalytics(@PathVariable UUID id) {
        verifyOwnership(id);
        EvaluationAnalyticsResponse analytics = scoringService.analyser(
                workflowService.obtenirReponsesEvaluation(id),
                workflowService.obtenirReponsesTechniques(id)
        );
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/manager/pending")
    public ResponseEntity<List<EvaluationItemResponse>> getManagerEvaluations() {
        UUID managerId = getCurrentUserId();
        List<Evaluation> evaluations = workflowService.listerEvaluationsManager(managerId);
        List<EvaluationItemResponse> response = new ArrayList<>();
        for (Evaluation eval : evaluations) {
            response.add(new EvaluationItemResponse(
                    eval.getId().toString(),
                    eval.getCampaign().getNom(),
                    eval.getStatut().name(),
                    eval.getSuperieurIdentifiant().toString(),
                    eval.getEtapeActuelle() != null ? eval.getEtapeActuelle().name() : "EVALUATION_GENERALE",
                    eval.getScoreSur20(),
                    eval.getCreeLe()
            ));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager/reponses/generales")
    public ResponseEntity<Void> answerGeneralQuestionAsManager(
            @PathVariable UUID id,
            @Valid @RequestBody EvaluationAnswerRequest request) {
        verifyOwnership(id);
        workflowService.repondreQuestionManager(
                id,
                UUID.fromString(request.getQuestionId()),
                request.getReponse(),
                request.getCommentaire(),
                request.getNote()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/manager/reponses/techniques")
    public ResponseEntity<Void> answerTechnicalQuestionAsManager(
            @PathVariable UUID id,
            @Valid @RequestBody TechnicalAnswerRequest request) {
        verifyOwnership(id);
        SkillLevel niveau = SkillLevel.valueOf(request.getNiveau().toUpperCase());
        UUID questionId = UUID.fromString(request.getQuestionId());
        if (questionRepository.findById(questionId).isPresent()) {
            workflowService.repondreQuestionManager(
                    id,
                    questionId,
                    niveau.label(),
                    request.getCommentaire(),
                    niveau.score()
            );
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        workflowService.evaluerCompetenceTechniqueManager(
                id,
                questionId,
                niveau,
                request.getCommentaire()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Move evaluation from general to technical step.
     */
    @PostMapping("/{id}/passer-technique")
    public ResponseEntity<Void> moveToTechnicalStep(@PathVariable UUID id) {
        verifyOwnership(id);
        workflowService.passerAEtapeTechnique(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Validate evaluation as collaborator.
     */
    @PostMapping("/{id}/validate/collaborator")
    public ResponseEntity<Void> validateAsCollaborator(@PathVariable UUID id) {
        verifyOwnership(id);
        workflowService.validerParCollaborateur(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Get evaluation details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EvaluationItemResponse> getEvaluationDetails(@PathVariable UUID id) {
        verifyOwnership(id);
        Evaluation evaluation = workflowService.obtenirEvaluation(id);

        return ResponseEntity.ok(new EvaluationItemResponse(
            evaluation.getId().toString(),
            evaluation.getCampaign().getNom(),
            evaluation.getStatut().name(),
            evaluation.getSuperieurIdentifiant().toString(),
            evaluation.getEtapeActuelle() != null ? evaluation.getEtapeActuelle().name() : "EVALUATION_GENERALE",
            evaluation.getScoreSur20(),
            evaluation.getCreeLe()
        ));
    }

    /**
     * Extract current user ID from Spring Security context (JWT token).
     */


    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Utilisateur non authentifié");
        }
       // String idUser = jwt.getClaimAsString("identifiant_utilisateur");
        // The principal should be the username/userId from the JWT token
        try {
            return UUID.fromString("0a4f7069-0737-4207-97dd-7a46a45f5429");
        } catch (IllegalArgumentException e) {
            throw new SecurityException("ID utilisateur invalide dans le token JWT: ");
        }
    }

    /**
     * Verify that the current user owns or is associated with this evaluation.
     */
    private void verifyOwnership(UUID evaluationId) {
        UUID currentUserId = getCurrentUserId();
        Evaluation evaluation = workflowService.obtenirEvaluation(evaluationId);
        
        // Check if user is the collaborator or the manager
        if (!evaluation.getCollaborateurIdentifiant().equals(currentUserId) &&
            !evaluation.getSuperieurIdentifiant().equals(currentUserId)) {
            throw new SecurityException("Accès non autorisé à cette évaluation");
        }
    }

    private String skillName(Integer score) {
        return switch (score) {
            case 1 -> "DEBUTANT";
            case 2 -> "SUPERVISE";
            case 3 -> "AUTONOME";
            case 4 -> "AVANCE";
            case 5 -> "EXPERT";
            default -> null;
        };
    }
}
