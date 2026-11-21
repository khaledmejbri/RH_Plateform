package com.hr.workflow.config;

/**
 * Expressions {@code @PreAuthorize} du moteur de workflow (spec § 7).
 */
public final class WorkflowSecurityExpressions {

	private WorkflowSecurityExpressions() {
	}

	public static final String BACKOFFICE_LECTURE = "hasAnyRole('RH','DIRECTION','ADMIN')";
	public static final String BACKOFFICE_ECRITURE = "hasAnyRole('RH','ADMIN')";
	public static final String UTILISATEUR = "hasRole('USER')";
	public static final String PLATEFORME = "hasRole('PLATEFORME')";
}
