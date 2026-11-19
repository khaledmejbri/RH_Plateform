package com.hr.referentiel.domain;

/**
 * Base de décompte des jours de congé annuel par société (§ 4.2, D-SOC10).
 */
public enum BaseDecompteConge {
	/** Seuls les jours travaillés configurés pour la société (hors jours fériés actifs). */
	JOURS_TRAVAILLES,
	/** Lundi à samedi (hors jours fériés actifs), indépendamment des jours travaillés. */
	JOURS_OUVRABLES
}
