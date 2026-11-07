package com.hr.evaluation.service;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analyse d'une évaluation pour le détail web (D-E09.c) : les compétences du template technique reçoivent leur niveau
 * d'écart (Aligné / Alerte / Critique / Urgente), calculé par {@link EcartEtoiles} comme l'alerte. En transaction de
 * lecture avec les questions chargées (open-in-view désactivé). Le mobile n'utilise pas cette voie : ni le collaborateur
 * ni le RO ne voient de niveau d'alerte.
 */
@Service
public class EvaluationAnalyseAdminService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationScoringService scoringService;
    private final TemplateTechniqueEvaluation templateTechnique;

    public EvaluationAnalyseAdminService(EvaluationRepository evaluationRepository,
                                         EvaluationAnswerRepository answerRepository,
                                         SkillAnswerRepository skillAnswerRepository,
                                         EvaluationScoringService scoringService,
                                         TemplateTechniqueEvaluation templateTechnique) {
        this.evaluationRepository = evaluationRepository;
        this.answerRepository = answerRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.scoringService = scoringService;
        this.templateTechnique = templateTechnique;
    }

    /**
     * D-E12.b : pour la liste de suivi, indique pour chaque évaluation si le responsable a noté et, seulement dans ce
     * cas, le codage couleur C-M07-04 calculé sur ses notes. Sans note du responsable : pas de couleur (« Non évaluée »),
     * jamais un VERT par défaut. Une seule requête pour tout le lot.
     */
    @Transactional(readOnly = true)
    public Map<UUID, NotesResponsable> notesResponsable(Collection<UUID> evaluationIds) {
        if (evaluationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Integer>> notes = new HashMap<>();
        for (EvaluationAnswer a : answerRepository.findAvecNoteManagerParEvaluations(evaluationIds)) {
            notes.computeIfAbsent(a.getEvaluation().getId(), k -> new ArrayList<>()).add(a.getNoteManager());
        }
        Map<UUID, NotesResponsable> out = new HashMap<>();
        notes.forEach((id, valeurs) -> out.put(id, new NotesResponsable(true,
                scoringService.calculer(valeurs.toArray(new Integer[0])).couleurAlerte())));
        return out;
    }

    /** @param couleur codage couleur C-M07-04 ; nul si le responsable n'a noté aucune question. */
    public record NotesResponsable(boolean aNote, CouleurAlerteEvaluationRh couleur) {
    }

    @Transactional(readOnly = true)
    public EvaluationAnalyticsResponse analyser(UUID evaluationId) {
        Evaluation evaluation = evaluationRepository.findByIdWithCampaign(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable"));
        return scoringService.analyser(
                answerRepository.findWithQuestionByEvaluationId(evaluationId),
                skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId),
                templateTechnique.idsQuestions(evaluation));
    }

    /**
     * Analyse pour le mobile (collaborateur, RO) : transaction de lecture, questions chargées, aucun niveau d'écart
     * ({@code niveauEcart} nul, Q4 : ni le collaborateur ni le RO ne voient l'alerte).
     */
    @Transactional(readOnly = true)
    public EvaluationAnalyticsResponse analyserPourMobile(UUID evaluationId) {
        return scoringService.analyser(
                answerRepository.findWithQuestionByEvaluationId(evaluationId),
                skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId));
    }

    /**
     * Analyse de chaque évaluation d'une campagne (statistiques de campagne) : même transaction de lecture, questions
     * chargées, sans niveau d'écart.
     */
    @Transactional(readOnly = true)
    public List<EvaluationAnalyticsResponse> analyserCampagne(List<Evaluation> evaluations) {
        return evaluations.stream()
                .map(evaluation -> scoringService.analyser(
                        answerRepository.findWithQuestionByEvaluationId(evaluation.getId()),
                        skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluation.getId())))
                .toList();
    }
}
