package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Analyse d'une évaluation pour le mobile (D-E15). Pour le collaborateur avant la validation, tout ce qui dépend des
 * notes du responsable est absent ({@code managerAverage}, {@code finalScore}, {@code averageGap}, écarts...).
 * Aucun niveau d'écart (D-E09).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyseMobile(
		BigDecimal selfAverage,
		BigDecimal managerAverage,
		BigDecimal finalScore,
		BigDecimal averageGap,
		BigDecimal discrepancyPercentage,
		List<Ecart> gaps,
		List<String> strengths,
		List<String> improvementAreas,
		List<String> recommendations,
		boolean aNotesCollaborateur,
		Boolean aNotesResponsable) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Ecart(String questionId, String label, String section, Integer selfScore, Integer managerScore,
			Integer gap) {
	}
}
