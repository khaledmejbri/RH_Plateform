package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("details") String details,
		@JsonProperty("timestamp") Instant timestamp,
		@JsonProperty("path") String path,
		@JsonProperty("suggestions") List<String> suggestions,
		@JsonProperty("http_status") int httpStatus,
		/** Fin du blocage de pointage (409 POINTAGE_BLOQUE_VISAGE, D-P10) ; nul pour toute autre erreur. */
		@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua
) {
	public static ErrorResponse of(String code, String message, int httpStatus) {
		return new ErrorResponse(code, message, null, Instant.now(), null, null, httpStatus, null);
	}

	public static ErrorResponse of(String code, String message, String details, int httpStatus) {
		return new ErrorResponse(code, message, details, Instant.now(), null, null, httpStatus, null);
	}

	public static ErrorResponse of(String code, String message, String details, List<String> suggestions, int httpStatus) {
		return new ErrorResponse(code, message, details, Instant.now(), null, suggestions, httpStatus, null);
	}

	public static ErrorResponse bloque(String code, String message, Instant bloqueJusqua, int httpStatus) {
		return new ErrorResponse(code, message, null, Instant.now(), null, null, httpStatus, bloqueJusqua);
	}
}
