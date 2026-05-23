package com.hr.evaluation.dto;

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
        List<String> recommendations
) {
    public record GapItem(
            String questionId,
            String label,
            String section,
            Integer selfScore,
            Integer managerScore,
            Integer gap,
            String severity
    ) {}

    public record SectionScore(
            String section,
            BigDecimal selfAverage,
            BigDecimal managerAverage,
            BigDecimal gap
    ) {}
}
