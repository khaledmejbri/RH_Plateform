package com.hr.presence.web;

public final class PresenceSecurityExpressions {

	public static final String BACKOFFICE_LECTURE = "hasAnyRole('RH','DIRECTION','ADMIN')";

	public static final String BACKOFFICE_ECRITURE = "hasAnyRole('RH','ADMIN')";

	public static final String MOBILE_USER = "hasRole('USER')";

	/** Demandes d'heures supplémentaires (matrice § 4.2) ; le périmètre D-H03 est vérifié en service. */
	public static final String DEMANDEUR_HEURES_SUP = "hasAnyRole('RO','RESPONSABLE','RH','ADMIN')";

	private PresenceSecurityExpressions() {
	}
}
