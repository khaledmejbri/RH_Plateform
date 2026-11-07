package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validation de l'évaluation par le RO (D-E10, SCRUM-56). Ordre strict dans une transaction : 404, 403, (déjà validée
 * → 200 sans effet), 409, 422, puis pose de la validation et détection des alertes d'écart (D-E06). Le score /20 et la
 * couleur d'alerte existante (C-M07-04) ne sont pas recalculés. Aucune notification au collaborateur ni au RO.
 */
@Service
public class EvaluationValidationSuperieurService {

    public static final String EVALUATION_INTROUVABLE = "EVALUATION_INTROUVABLE";
    public static final String PAS_RO_EVALUATION = "PAS_RO_EVALUATION";
    public static final String COLLABORATEUR_NON_VALIDE = "COLLABORATEUR_NON_VALIDE";
    public static final String COMPETENCES_NON_NOTEES = "COMPETENCES_NON_NOTEES";

    private final EvaluationRepository evaluationRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final TemplateTechniqueEvaluation templateTechnique;
    private final AlerteEcartService alerteEcartService;

    public EvaluationValidationSuperieurService(EvaluationRepository evaluationRepository,
                                                EvaluationAnswerRepository answerRepository,
                                                TemplateTechniqueEvaluation templateTechnique,
                                                AlerteEcartService alerteEcartService) {
        this.evaluationRepository = evaluationRepository;
        this.answerRepository = answerRepository;
        this.templateTechnique = templateTechnique;
        this.alerteEcartService = alerteEcartService;
    }

    /** État de validation pour l'écran du RO (mêmes contrôles 404 puis 403 que la validation). */
    public record EtatValidation(
            UUID evaluationId,
            boolean dejaValidee,
            boolean collaborateurAValide,
            boolean avecCompetencesTechniques,
            List<CompetenceManquante> competencesManquantes,
            int nbCompetencesManquantes,
            boolean peutValider) {
    }

    public record CompetenceManquante(UUID questionId, String libelle) {
    }

    @Transactional
    public Evaluation validerParSuperieur(UUID evaluationId, IdentiteAppelant appelant) {
        Evaluation evaluation = chargerVerrouillee(evaluationId);
        exigerSuperieur(evaluation, appelant);

        if (evaluation.getValidationSuperieurLe() != null) {
            return evaluation; // 2e appel : 200, rien ne change
        }
        if (evaluation.getValidationCollaborateurLe() == null) {
            throw EvaluationMetierException.conflict(COLLABORATEUR_NON_VALIDE,
                    "Le collaborateur n'a pas encore validé son évaluation.");
        }
        List<EvaluationQuestion> questions = templateTechnique.questions(evaluation);
        List<CompetenceManquante> manquantes = competencesManquantes(evaluation, questions);
        if (!manquantes.isEmpty()) {
            List<Map<String, Object>> liste = new ArrayList<>();
            for (CompetenceManquante m : manquantes) {
                Map<String, Object> ligne = new LinkedHashMap<>();
                ligne.put("questionId", m.questionId().toString());
                ligne.put("libelle", m.libelle());
                liste.add(ligne);
            }
            throw EvaluationMetierException.unprocessable(COMPETENCES_NON_NOTEES,
                    "Toutes les compétences techniques doivent être notées.",
                    Map.of("competencesManquantes", liste));
        }

        evaluation.setValidationSuperieurLe(Instant.now());
        evaluation.setValidationSuperieurPar(appelant.compteJwtObligatoire());
        evaluation.setStatut(StatutEvaluationRh.VALIDEE); // collaborateur et RO ont validé
        Evaluation sauvee = evaluationRepository.save(evaluation);
        alerteEcartService.detecter(sauvee);
        return sauvee;
    }

    @Transactional(readOnly = true)
    public EtatValidation etatValidationSuperieur(UUID evaluationId, IdentiteAppelant appelant) {
        Evaluation evaluation = evaluationRepository.findByIdWithCampaign(evaluationId)
                .orElseThrow(() -> introuvable(evaluationId));
        exigerSuperieur(evaluation, appelant);

        List<EvaluationQuestion> questions = templateTechnique.questions(evaluation);
        List<CompetenceManquante> manquantes = competencesManquantes(evaluation, questions);
        boolean deja = evaluation.getValidationSuperieurLe() != null;
        boolean collaborateur = evaluation.getValidationCollaborateurLe() != null;
        return new EtatValidation(evaluation.getId(), deja, collaborateur, !questions.isEmpty(),
                manquantes, manquantes.size(), !deja && collaborateur && manquantes.isEmpty());
    }

    private Evaluation chargerVerrouillee(UUID evaluationId) {
        evaluationRepository.findByIdForUpdate(evaluationId).orElseThrow(() -> introuvable(evaluationId));
        return evaluationRepository.findByIdWithCampaign(evaluationId).orElseThrow(() -> introuvable(evaluationId));
    }

    private static void exigerSuperieur(Evaluation evaluation, IdentiteAppelant appelant) {
        if (!appelant.estSuperieur(evaluation)) {
            throw EvaluationMetierException.forbidden(PAS_RO_EVALUATION,
                    "Vous n'êtes pas le responsable de cette évaluation.");
        }
    }

    private static EvaluationMetierException introuvable(UUID evaluationId) {
        return EvaluationMetierException.notFound(EVALUATION_INTROUVABLE, "Évaluation introuvable");
    }

    private List<CompetenceManquante> competencesManquantes(Evaluation evaluation, List<EvaluationQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<UUID, EvaluationAnswer> reponses = new HashMap<>();
        for (EvaluationAnswer reponse : answerRepository.findWithQuestionByEvaluationId(evaluation.getId())) {
            reponses.put(reponse.getQuestion().getId(), reponse);
        }
        List<CompetenceManquante> manquantes = new ArrayList<>();
        for (EvaluationQuestion question : questions) {
            EvaluationAnswer reponse = reponses.get(question.getId());
            if (reponse == null || reponse.getNoteManager() == null) {
                manquantes.add(new CompetenceManquante(question.getId(), question.getLibelle()));
            }
        }
        return manquantes;
    }
}
