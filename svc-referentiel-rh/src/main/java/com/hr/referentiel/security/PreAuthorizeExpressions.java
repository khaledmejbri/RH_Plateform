package com.hr.referentiel.security;

/**
 * Droits de la SPA RH / back-office.
 * <ul>
 *   <li>{@link #BACKOFFICE_LECTURE} : RH, DIRECTION, ADMIN — GET / consultation</li>
 *   <li>{@link #BACKOFFICE_ECRITURE} : RH, ADMIN — mutations (DIRECTION exclu)</li>
 *   <li>{@link #PLAINTES_LECTURE} : RH, ADMIN — plaintes en back-office (D-N01.a, Q13 : DIRECTION exclu, 403)</li>
 * </ul>
 * {@link #BACKOFFICE_RH} est conservé comme alias de lecture pour rétrocompatibilité.
 */
public final class PreAuthorizeExpressions {

	public static final String BACKOFFICE_LECTURE = "hasAnyRole('RH','DIRECTION','ADMIN')";

	public static final String BACKOFFICE_ECRITURE = "hasAnyRole('RH','ADMIN')";

	/** D-N01.a : les plaintes sont réservées aux RH et ADMIN, partout (liste, détail, suivi). */
	public static final String PLAINTES_LECTURE = "hasAnyRole('RH','ADMIN')";

	/** @deprecated préférer {@link #BACKOFFICE_LECTURE} ou {@link #BACKOFFICE_ECRITURE} */
	@Deprecated
	public static final String BACKOFFICE_RH = BACKOFFICE_LECTURE;

	private PreAuthorizeExpressions() {
	}
}
