package com.hr.identiteacces.web;

/**
 * Exception métier identité et plateforme — sur le modèle de {@code PresenceMetierException}.
 */
public class IdentiteMetierException extends RuntimeException {

	private final String code;
	private final int httpStatus;

	public IdentiteMetierException(String code, String message, int httpStatus) {
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

	public static IdentiteMetierException badRequest(String code, String message) {
		return new IdentiteMetierException(code, message, 400);
	}

	public static IdentiteMetierException unauthorized(String code, String message) {
		return new IdentiteMetierException(code, message, 401);
	}

	public static IdentiteMetierException forbidden(String code, String message) {
		return new IdentiteMetierException(code, message, 403);
	}

	public static IdentiteMetierException notFound(String code, String message) {
		return new IdentiteMetierException(code, message, 404);
	}

	public static IdentiteMetierException conflict(String code, String message) {
		return new IdentiteMetierException(code, message, 409);
	}

	public static IdentiteMetierException unprocessable(String code, String message) {
		return new IdentiteMetierException(code, message, 422);
	}
}
