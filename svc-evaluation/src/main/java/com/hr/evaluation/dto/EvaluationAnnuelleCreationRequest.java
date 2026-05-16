package com.hr.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record EvaluationAnnuelleCreationRequest(
		@NotNull UUID collaborateurIdentifiant,
		@NotNull UUID superieurIdentifiant,
		@NotNull @Min(2000) @Max(2100) Integer annee,
		@NotNull @Min(1) @Max(5) Integer savoirTechnique,
		@NotNull @Min(1) @Max(5) Integer savoirFaire,
		@NotNull @Min(1) @Max(5) Integer savoirEtre,
		String bilanSavoir,
		String bilanSavoirFaire,
		String bilanSavoirEtre,
		String resultatsObjectifsN,
		String objectifsNPlus1,
		String pointsForts,
		String pointsAAmeliorer,
		String planActionRecommande,
		List<String> formationsRecommandees
) {
}
