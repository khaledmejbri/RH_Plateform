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
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.repository.TechnicalQuestionRepository;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.EvaluationValidationSuperieurService;
import com.hr.evaluation.service.TemplateTechniqueEvaluation;
import com.hr.evaluation.service.EvaluationAnalyseAdminService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.LibellesProfilService;
import com.hr.evaluation.service.ParametresEvaluation;
import com.hr.evaluation.service.EvaluationMobileLectureService;
import com.hr.evaluation.dto.AnalyseMobile;
import com.hr.evaluation.dto.EvaluationDetailMobile;
import com.hr.evaluation.dto.ReponseMobile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for mobile evaluation feature.
 * Ownership : collaborateur = ses évaluations ; manager = périmètre supérieur uniquement.
 */
@RestController
@RequestMapping("/api/rh/v1/mobile/evaluations")
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize(EvaluationSecurityExpressions.MOBILE_USER)
public class EvaluationMobileController {

    private final EvaluationWorkflowService workflowService;
    private final EvaluationQuestionRepository questionRepository;
    private final TechnicalQuestionRepository technicalQuestionRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationAnalyseAdminService analyseService;
    private final EvaluationAccessService evaluationAccess;
    private final TemplateTechniqueEvaluation templateTechnique;
    private final EvaluationValidationSuperieurService validationSuperieurService;
    private final IdentiteAppelantResolver identiteResolver;
    private final LibellesProfilService libellesProfil;
    private final EvaluationMobileLectureService lectureService;
    private final ParametresEvaluation parametres;

    public EvaluationMobileController(
            EvaluationWorkflowService workflowService,
            EvaluationQuestionRepository questionRepository,
            TechnicalQuestionRepository technicalQuestionRepository,
            SkillAnswerRepository skillAnswerRepository,
            EvaluationAnalyseAdminService analyseService,
            EvaluationAccessService evaluationAccess,
            TemplateTechniqueEvaluation templateTechnique,
            EvaluationValidationSuperieurService validationSuperieurService,
            IdentiteAppelantResolver identiteResolver,
            LibellesProfilService libellesProfil,
            EvaluationMobileLectureService lectureService,
            ParametresEvaluation parametres) {
        this.workflowService = workflowService;
        this.questionRepository = questionRepository;
        this.technicalQuestionRepository = technicalQuestionRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.analyseService = analyseService;
        this.evaluationAccess = evaluationAccess;
        this.templateTechnique = templateTechnique;
        this.validationSuperieurService = validationSuperieurService;
        this.identiteResolver = identiteResolver;
        this.libellesProfil = libellesProfil;
        this.lectureService = lectureService;
        this.parametres = parametres;
    }

    /**
     * Get all evaluations for the authenticated user (collaborator).
     */
    @GetMapping("/moi")
    public ResponseEntity<List<EvaluationItemResponse>> getMyEvaluations(
            @RequestParam(name = "ensure", defaultValue = "false") boolean ensure,
            @RequestParam(name = "niveau_seniorite", required = false) String niveauSenioriteIgnored,
            @RequestParam(name = "role_metier", required = false) String roleMetierIgnored) {
        UUID collaborateurId = evaluationAccess.requireCurrentActorId();
        // E4-R11 : params matching client ignorés ; ensure ne crée plus d'évaluation (activation only)
        if (ensure) {
            try {
                workflowService.assurerEvaluationPourCollaborateur(
                        collaborateurId, collaborateurId, null, null);
            } catch (IllegalStateException ex) {
                // Pas de campagne active : liste vide
            }
        }
        List<Evaluation> evaluations = workflowService.listerEvaluationsCollaborateur(collaborateurId);

        List<EvaluationItemResponse> response = new ArrayList<>();
        for (Evaluation eval : evaluations) {
            if (eval.getStatut() == com.hr.evaluation.domain.StatutEvaluationRh.ARCHIVEE) {
                continue;
            }
            response.add(toItem(eval, true));
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/ensure")
    public ResponseEntity<?> ensureMyEvaluation(
            @RequestBody(required = false) Map<String, String> bodyIgnored) {
        UUID collaborateurId = evaluationAccess.requireCurrentActorId();
        // Matching client déprécié — retourne l'éval existante si présente, sinon 204
        Evaluation evaluation = null;
        try {
            evaluation = workflowService.assurerEvaluationPourCollaborateur(
                    collaborateurId, collaborateurId, null, null);
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(List.of());
        }
        if (evaluation == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toItem(evaluation, true));
    }

    /**
     * Get general evaluation questions for a specific evaluation.
     */
    @GetMapping("/{id}/questions/generales")
    public ResponseEntity<List<QuestionResponse>> getGeneralQuestions(
            @PathVariable UUID id) {
        evaluationAccess.requireParticipantEvaluation(id);
        
        Evaluation evaluation = workflowService.obtenirEvaluation(id);
        
        // Check campaign and template
        if (evaluation.getCampaign() == null) {
            throw new IllegalStateException("L'évaluation n'a pas de campagne associée");
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
                q.getLabelsEchelle() != null && !q.getLabelsEchelle().isEmpty() ? q.getLabelsEchelle()
                        : q.getTypeQuestion() == com.hr.evaluation.domain.QuestionType.RATING
                                ? parametres.libellesEchelle() : q.getLabelsEchelle(),
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
        evaluationAccess.requireCollaborateurEvaluation(id);

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
        evaluationAccess.requireParticipantEvaluation(id);

        Evaluation evaluation = workflowService.obtenirEvaluation(id);

        if (evaluation.getEtapeActuelle() != EvaluationStep.EVALUATION_TECHNIQUE) {
            throw new IllegalStateException("L'évaluation n'est pas encore à l'étape technique");
        }

        // Une seule résolution pour le mobile, la validation RO et la détection d'écart (TemplateTechniqueEvaluation)
        var templateCompetence = templateTechnique.resoudre(evaluation).orElse(null);

        if (templateCompetence != null) {
            List<EvaluationQuestion> questions = questionRepository
                    .findByTemplateIdAndActifTrueOrderByOrdreAsc(templateCompetence.getId());

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
                        // D-R09 / D-E04 : compétences notées en étoiles = TOUJOURS l'échelle du référentiel
                        // ECHELLE_ETOILES (libellés courants) ; les labels_echelle stockés sur la question sont ignorés
                        String.join(",", parametres.libellesEchelle()),
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
            throw new IllegalStateException(
                    "Aucun template de compétences pour le grade "
                            + (evaluation.getNiveauSeniorite() != null ? evaluation.getNiveauSeniorite() : "inconnu")
                            + ". Publiez un template TECHNIQUE avec ce niveau côté admin.");
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
        evaluationAccess.requireCollaborateurEvaluation(id);

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

    /** B8 : réponses de l'évaluation ; la notation du responsable est masquée au collaborateur avant VALIDEE (D-E15). */
    @GetMapping("/{id}/reponses")
    public ResponseEntity<List<ReponseMobile>> getAnswers(@PathVariable UUID id) {
        evaluationAccess.requireParticipantEvaluation(id);
        return ResponseEntity.ok(lectureService.reponses(id, identiteResolver.courant()));
    }

    /** B7 : détail lisible de l'évaluation (404 si inconnue, 403 si l'appelant n'est pas participant). */
    @GetMapping("/{id}/detail")
    public ResponseEntity<EvaluationDetailMobile> getDetail(@PathVariable UUID id) {
        lectureService.verifierExiste(id);
        evaluationAccess.requireParticipantEvaluation(id);
        return ResponseEntity.ok(lectureService.detail(id, identiteResolver.courant()));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<AnalyseMobile> getAnalytics(@PathVariable UUID id) {
        evaluationAccess.requireParticipantEvaluation(id);
        return ResponseEntity.ok(lectureService.analyse(id, identiteResolver.courant()));
    }

    @GetMapping("/manager/pending")
    public ResponseEntity<List<EvaluationItemResponse>> getManagerEvaluations() {
        UUID managerId = evaluationAccess.requireCurrentActorId();
        List<Evaluation> evaluations = workflowService.listerEvaluationsManager(managerId);
        List<EvaluationItemResponse> response = new ArrayList<>();
        for (Evaluation eval : evaluations) {
            response.add(toItem(eval, false));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager/reponses/generales")
    public ResponseEntity<Void> answerGeneralQuestionAsManager(
            @PathVariable UUID id,
            @Valid @RequestBody EvaluationAnswerRequest request) {
        evaluationAccess.requireManagerEvaluation(id);
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
        evaluationAccess.requireManagerEvaluation(id);
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
        evaluationAccess.requireCollaborateurEvaluation(id);
        workflowService.passerAEtapeTechnique(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Validate evaluation as collaborator.
     */
    @PostMapping("/{id}/validate/collaborator")
    public ResponseEntity<Void> validateAsCollaborator(@PathVariable UUID id) {
        evaluationAccess.requireCollaborateurEvaluation(id);
        workflowService.validerParCollaborateur(id);
        return ResponseEntity.ok().build();
    }

    /**
     * État de validation par le RO (D-E10) : sert au bouton « Valider l'évaluation » de RH Connect.
     * Identité du RO lue dans le JWT (404 puis 403 {@code PAS_RO_EVALUATION}).
     */
    @GetMapping("/{id}/validate/manager/etat")
    public ResponseEntity<EvaluationValidationSuperieurService.EtatValidation> etatValidationManager(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                validationSuperieurService.etatValidationSuperieur(id, identiteResolver.courant()));
    }

    /**
     * Validation de l'évaluation par le RO (D-E10) : 200, ou 404 / 403 / 409 / 422 (dans cet ordre de précédence).
     * La détection des alertes d'écart se fait dans la même transaction ; aucun champ d'alerte n'est renvoyé.
     */
    @PostMapping("/{id}/validate/manager")
    public ResponseEntity<EvaluationItemResponse> validateAsManager(@PathVariable UUID id) {
        Evaluation evaluation = validationSuperieurService.validerParSuperieur(id, identiteResolver.courant());
        return ResponseEntity.ok(toItem(evaluation, false));
    }

    /**
     * Get evaluation details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EvaluationItemResponse> getEvaluationDetails(@PathVariable UUID id) {
        Evaluation evaluation = evaluationAccess.requireParticipantEvaluation(id);
        
        // Add null check for campaign
        if (evaluation.getCampaign() == null) {
            throw new IllegalStateException("L'évaluation n'a pas de campagne associée");
        }

        return ResponseEntity.ok(toItem(evaluation, EvaluationMobileLectureService.masquerPour(identiteResolver.courant(), evaluation)));
    }

    /**
     * @param masquerScore D-E15 : le collaborateur ne voit pas le score consolidé (qui contient la notation du
     *                     responsable) avant VALIDEE ; le champ est alors nul.
     */
    private EvaluationItemResponse toItem(Evaluation evaluation, boolean masquerScore) {
        boolean cacher = masquerScore && !EvaluationMobileLectureService.notesResponsableVisibles(evaluation.getStatut());
        String campaignNom = evaluation.getCampaign() != null
                ? evaluation.getCampaign().getNom()
                : "Campagne évaluation";
        return new EvaluationItemResponse(
                evaluation.getId().toString(),
                campaignNom,
                evaluation.getStatut().name(),
                evaluation.getCollaborateurIdentifiant().toString(),
                evaluation.getCollaborateurNom(),
                evaluation.getCollaborateurPoste(),
                evaluation.getSuperieurIdentifiant().toString(),
                evaluation.getSuperieurNom(),
                evaluation.getEtapeActuelle() != null
                        ? evaluation.getEtapeActuelle().name()
                        : "EVALUATION_GENERALE",
                cacher ? null : evaluation.getScoreSur20(),
                evaluation.getFamilleMetierCode(),
                libellesProfil.famille(evaluation.getFamilleMetierCode(), evaluation.getFamilleMetierLibelle()),
                evaluation.getNiveauSeniorite(),
                libellesProfil.niveau(evaluation.getNiveauSeniorite(), evaluation.getNiveauSenioriteLibelle()),
                libellesProfil.niveau(evaluation.getNiveauSeniorite(), evaluation.getNiveauSenioriteLibelle()),
                evaluation.getValidationCollaborateurLe(),
                evaluation.getValidationSuperieurLe(),
                evaluation.getCreeLe()
        );
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
