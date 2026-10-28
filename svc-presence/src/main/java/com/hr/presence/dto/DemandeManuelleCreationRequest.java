package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de pointage manuel après un rejet (D-P04).
 */
public record DemandeManuelleCreationRequest(
		@NotNull
		@JsonProperty("pointage_rejete_id") UUID pointageRejeteId,

		/** ENTREE / SORTIE ; défaut = type du pointage rejeté. */
		@JsonProperty("type") String type,

		/** Défaut = heure du rejet. */
		@JsonProperty("heure_declaree") Instant heureDeclaree,

		@NotBlank
		@Size(min = 10, max = 500)
		@JsonProperty("justification") String justification
) {
}
