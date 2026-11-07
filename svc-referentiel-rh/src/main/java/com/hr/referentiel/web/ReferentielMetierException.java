package com.hr.referentiel.web;

/**
 * Exception métier avec code stable API (404 / 409 / 422).
 */
public class ReferentielMetierException extends RuntimeException {

	private final String code;
	private final int httpStatus;

	public ReferentielMetierException(String code, String message, int httpStatus) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
	}

	public String getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
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
}
