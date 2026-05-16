package com.hr.evaluation.dto;

import com.hr.evaluation.domain.SemestreEvaluationRh;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EvaluationSemestrielleCreationRequest(
		@NotNull UUID collaborateurIdentifiant,
		@NotNull UUID superieurIdentifiant,
		@NotNull @Min(2000) @Max(2100) Integer annee,
		@NotNull SemestreEvaluationRh semestre,
		@NotNull @Min(1) @Max(5) Integer qualiteTravail,
		@NotNull @Min(1) @Max(5) Integer rendement,
		@NotNull @Min(1) @Max(5) Integer ponctualite,
		@NotNull @Min(1) @Max(5) Integer espritEquipe,
		String pointsForts,
		String pointsAAmeliorer,
		String planActionRecommande
) {
}
