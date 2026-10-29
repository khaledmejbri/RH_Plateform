package com.hr.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création d'une campagne d'évaluation. Le créateur n'est pas dans le corps : il est lu dans le JWT (SCRUM-26).
 * Calendrier (SCRUM-41) : mois de lancement libre, jour de commencement facultatif (le 20 par défaut),
 * durée facultative (paramètre société par défaut). {@code moisFin} n'est plus utilisé : la fin est calculée.
 */
public record CampagneCreationRequest(
		@NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String nom,
		@Size(max = 2000) String description,
		@NotBlank(message = "Le type est obligatoire (ANNUELLE, SEMESTRIELLE ou TRIMESTRIELLE)") String type,
		@NotNull(message = "L'année est obligatoire") @Min(2000) @Max(2100) Integer annee,
		@NotNull(message = "Le mois de lancement est obligatoire (1 à 12)")
		@Min(value = 1, message = "Le mois de lancement doit être compris entre 1 et 12")
		@Max(value = 12, message = "Le mois de lancement doit être compris entre 1 et 12") Integer moisDebut,
		@Min(value = 1, message = "Le jour de commencement doit être compris entre 1 et 31")
		@Max(value = 31, message = "Le jour de commencement doit être compris entre 1 et 31") Integer jourDebut,
		@Min(value = 1, message = "La durée doit être d'au moins 1 jour")
		@Max(value = 120, message = "La durée ne peut pas dépasser 120 jours") Integer dureeJours,
		Integer moisFin) {
}
