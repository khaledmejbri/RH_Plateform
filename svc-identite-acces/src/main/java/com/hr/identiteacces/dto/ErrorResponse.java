package com.hr.identiteacces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("erreur") String erreur,
		@JsonProperty("details") String details,
		@JsonProperty("timestamp") Instant timestamp,
		@JsonProperty("path") String path,
		@JsonProperty("suggestions") List<String> suggestions,
		@JsonProperty("http_status") int httpStatus
) {
	public static ErrorResponse of(String code, String message, int httpStatus) {
		return of(code, message, httpStatus, null);
	}

	public static ErrorResponse of(String code, String message, int httpStatus, String path) {
		return new ErrorResponse(code, message, message, message, Instant.now(), path, List.of(), httpStatus);
	}

	public static ErrorResponse of(String code, String message, String details, List<String> suggestions, int httpStatus) {
		return of(code, message, details, suggestions, httpStatus, null);
	}

	public static ErrorResponse of(
			String code, String message, String details, List<String> suggestions, int httpStatus, String path) {
		return new ErrorResponse(code, message, message, details, Instant.now(), path, suggestions, httpStatus);
	}
}
