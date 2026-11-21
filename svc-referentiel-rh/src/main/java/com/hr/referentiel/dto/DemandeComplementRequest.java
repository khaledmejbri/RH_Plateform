package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corps de {@code POST /api/rh/v1/demandes-administratives/{id}/complement} (D-W5-12).
 */
public record DemandeComplementRequest(
		@NotBlank(message = "Le commentaire est obligatoire.")
		@Size(min = 5, max = 1000, message = "Le commentaire doit contenir entre 5 et 1000 caractères.")
		@JsonProperty("commentaire")
		String commentaire,
		@JsonProperty("pieces_jointes")
		List<Object> piecesJointes
) {
}
