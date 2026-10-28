package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Appelant (résolu depuis le JWT) et son valideur de pointage manuel selon D-H03 (D-P04).
 * Valideur absent = validation par les RH.
 */
public record ValideurPointageResponse(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("collaborateur_compte_id") UUID collaborateurCompteId,
		@JsonProperty("collaborateur_nom") String collaborateurNom,
		@JsonProperty("valideur_id") UUID valideurId,
		@JsonProperty("valideur_compte_id") UUID valideurCompteId,
		@JsonProperty("valideur_nom") String valideurNom
) {
}
