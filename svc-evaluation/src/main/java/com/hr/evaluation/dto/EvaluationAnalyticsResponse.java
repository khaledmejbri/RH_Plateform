package com.hr.evaluation.dto;

import com.hr.evaluation.domain.NiveauEcart;

import java.math.BigDecimal;
import java.util.List;

public record EvaluationAnalyticsResponse(
        BigDecimal selfAverage,
        BigDecimal managerAverage,
        BigDecimal finalScore,
        BigDecimal totalSelfScore,
        BigDecimal totalManagerScore,
        BigDecimal averageGap,
        BigDecimal discrepancyPercentage,
        List<GapItem> gaps,
        List<SectionScore> sections,
        List<String> strengths,
        List<String> improvementAreas,
        List<String> recommendations,
        /** D-E14 : au moins une note du collaborateur (auto-évaluation). */
        boolean aNotesCollaborateur,
        /** D-E14 : au moins une note du responsable. */
        boolean aNotesResponsable
) {
    public record GapItem(
            String questionId,
            String label,
            String section,
            Integer selfScore,
            Integer managerScore,
            Integer gap,
            String severity,
            /** Vrai pour une compétence du template technique (D-E09.c). */
            boolean competenceTechnique,
            /** ALIGNE / ORANGE / ROUGE / URGENTE pour une compétence technique, null pour une question générale. */
            NiveauEcart niveauEcart
    ) {}

    public record SectionScore(
            String section,
            BigDecimal selfAverage,
            BigDecimal managerAverage,
            BigDecimal gap
    ) {}
}
