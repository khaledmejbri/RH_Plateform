package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de pointage manuel après un rejet (D-P04). Validée dans le service, après l'identité (D-P21.c : 403 avant 400).
 */
public record DemandeManuelleCreationRequest(
		@JsonProperty("pointage_rejete_id") UUID pointageRejeteId,

		/** Toléré seulement s'il est identique à celui du refus (D-P23, sinon 400 CHAMP_NON_MODIFIABLE). */
		@JsonProperty("type") String type,

		/** Toléré seulement s'il est identique à l'heure du refus, à la seconde (D-P23). */
		@JsonProperty("heure_declaree") Instant heureDeclaree,

		@JsonProperty("justification") String justification
) {
}
