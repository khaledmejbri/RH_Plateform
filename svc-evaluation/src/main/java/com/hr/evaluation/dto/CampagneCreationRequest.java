package com.hr.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création d'une campagne d'évaluation. Le créateur n'est pas dans le corps : il est lu dans le JWT (SCRUM-26).
 */
public record CampagneCreationRequest(
		@NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String nom,
		@Size(max = 2000) String description,
		@NotBlank(message = "Le type est obligatoire (ANNUELLE ou SEMESTRIELLE)") String type,
		@NotNull(message = "L'année est obligatoire") @Min(2000) @Max(2100) Integer annee,
		@NotNull(message = "Le mois de début est obligatoire (6 ou 12)") Integer moisDebut,
		@NotNull(message = "Le mois de fin est obligatoire (6 ou 12)") Integer moisFin) {
}
