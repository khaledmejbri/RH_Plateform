package com.hr.referentiel.web;

import java.util.Map;

/**
 * Exception métier avec code stable API (400 / 404 / 405 / 409 / 422). {@code details} : champs supplémentaires
 * recopiés dans le corps de la réponse (ex. {@code utilisations}).
 */
public class ReferentielMetierException extends RuntimeException {

	private final String code;
	private final int httpStatus;
	private final Map<String, Object> details;

	public ReferentielMetierException(String code, String message, int httpStatus) {
		this(code, message, httpStatus, Map.of());
	}

	public ReferentielMetierException(String code, String message, int httpStatus, Map<String, Object> details) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
		this.details = details != null ? details : Map.of();
	}

	public String getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public static ReferentielMetierException badRequest(String code, String message) {
		return new ReferentielMetierException(code, message, 400);
	}

	public static ReferentielMetierException unprocessable(String code, String message) {
		return new ReferentielMetierException(code, message, 422);
	}

	public static ReferentielMetierException notFound(String code, String message) {
		return new ReferentielMetierException(code, message, 404);
	}

	public static ReferentielMetierException conflict(String code, String message) {
		return new ReferentielMetierException(code, message, 409);
	}

	public static ReferentielMetierException methodNotAllowed(String code, String message) {
		return new ReferentielMetierException(code, message, 405);
	}
}
