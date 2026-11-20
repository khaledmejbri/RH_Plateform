package com.hr.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.definition.ErreurDefinition;

import java.time.Instant;
import java.util.List;

/**
 * Format d'erreur standard du dépôt (§ 9.7), enrichi de {@code erreurs} pour {@code DEFINITION_INVALIDE}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("details") String details,
		@JsonProperty("timestamp") Instant timestamp,
		@JsonProperty("path") String path,
		@JsonProperty("http_status") int httpStatus,
		@JsonProperty("erreurs") List<ErreurDefinition> erreurs
) {
	public static ErrorResponse of(String code, String message, String details, String path, int httpStatus) {
		return new ErrorResponse(code, message, details, Instant.now(), path, httpStatus, null);
	}

	public static ErrorResponse definitionInvalide(String message, String path, List<ErreurDefinition> erreurs) {
		return new ErrorResponse("DEFINITION_INVALIDE", message, message, Instant.now(), path, 400, erreurs);
	}
}
