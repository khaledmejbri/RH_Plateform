package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** D-F06.c : réponse du pré-contrôle des demandes de document en double. */
public record CheckRestrictionDocumentResponse(
		@JsonProperty("can_request") boolean canRequest,
		@JsonProperty("message") String message) {
}
