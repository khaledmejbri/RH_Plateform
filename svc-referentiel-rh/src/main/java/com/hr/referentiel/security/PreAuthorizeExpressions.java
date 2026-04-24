package com.hr.referentiel.security;

/**
 * Droits de la SPA RH / back-office : accès métier (pas le rôle collaborateur {@code USER} seul).
 */
public final class PreAuthorizeExpressions {

	public static final String BACKOFFICE_RH = "hasAnyRole('RH','DIRECTION','ADMIN')";

	private PreAuthorizeExpressions() {
	}
}
