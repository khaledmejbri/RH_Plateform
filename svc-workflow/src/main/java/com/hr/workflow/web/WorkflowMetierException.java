package com.hr.workflow.web;

import com.hr.workflow.definition.ErreurDefinition;

import java.util.List;

/**
 * Exception métier du moteur de workflow (codes HTTP 400 / 403 / 404 / 409 / 422 / 503).
 */
public class WorkflowMetierException extends RuntimeException {

	private final String code;
	private final int httpStatus;
	private final List<ErreurDefinition> erreurs;

	public WorkflowMetierException(String code, String message, int httpStatus) {
		this(code, message, httpStatus, null);
	}

	public WorkflowMetierException(String code, String message, int httpStatus, List<ErreurDefinition> erreurs) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
		this.erreurs = erreurs;
	}

	public String getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public List<ErreurDefinition> getErreurs() {
		return erreurs;
	}

	public static WorkflowMetierException badRequest(String code, String message) {
		return new WorkflowMetierException(code, message, 400);
	}

	public static WorkflowMetierException definitionInvalide(List<ErreurDefinition> erreurs) {
		return new WorkflowMetierException("DEFINITION_INVALIDE", "La définition du workflow contient des erreurs.", 400, erreurs);
	}

	public static WorkflowMetierException forbidden(String code, String message) {
		return new WorkflowMetierException(code, message, 403);
	}

	public static WorkflowMetierException notFound(String code, String message) {
		return new WorkflowMetierException(code, message, 404);
	}

	public static WorkflowMetierException conflict(String code, String message) {
		return new WorkflowMetierException(code, message, 409);
	}

	public static WorkflowMetierException unprocessable(String code, String message) {
		return new WorkflowMetierException(code, message, 422);
	}

	public static WorkflowMetierException indisponible(String code, String message) {
		return new WorkflowMetierException(code, message, 503);
	}
}
