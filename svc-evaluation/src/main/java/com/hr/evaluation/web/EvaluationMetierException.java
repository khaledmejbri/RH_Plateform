package com.hr.evaluation.web;

import java.util.Map;

/**
 * Exception métier avec code API stable (E1–E5).
 */
public class EvaluationMetierException extends RuntimeException {

	private final String code;
	private final int httpStatus;
	private final Map<String, Object> donnees;

	public EvaluationMetierException(String code, String message, int httpStatus) {
		this(code, message, httpStatus, null);
	}

	public EvaluationMetierException(String code, String message, int httpStatus, Map<String, Object> donnees) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
		this.donnees = donnees;
	}

	/** Données structurées renvoyées dans le champ {@code donnees} de l'erreur (peut être nul). */
	public Map<String, Object> getDonnees() {
		return donnees;
	}

	public String getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public static EvaluationMetierException unprocessable(String code, String message) {
		return new EvaluationMetierException(code, message, 422);
	}

	public static EvaluationMetierException conflict(String code, String message) {
		return new EvaluationMetierException(code, message, 409);
	}

	public static EvaluationMetierException badRequest(String code, String message) {
		return new EvaluationMetierException(code, message, 400);
	}

	public static EvaluationMetierException unprocessable(String code, String message, Map<String, Object> donnees) {
		return new EvaluationMetierException(code, message, 422, donnees);
	}

	public static EvaluationMetierException conflict(String code, String message, Map<String, Object> donnees) {
		return new EvaluationMetierException(code, message, 409, donnees);
	}

	public static EvaluationMetierException notFound(String code, String message) {
		return new EvaluationMetierException(code, message, 404);
	}

	public static EvaluationMetierException methodNotAllowed(String code, String message) {
		return new EvaluationMetierException(code, message, 405);
	}

	public static EvaluationMetierException unavailable(String code, String message) {
		return new EvaluationMetierException(code, message, 503);
	}

	public static EvaluationMetierException forbidden(String code, String message) {
		return new EvaluationMetierException(code, message, 403);
	}
}
