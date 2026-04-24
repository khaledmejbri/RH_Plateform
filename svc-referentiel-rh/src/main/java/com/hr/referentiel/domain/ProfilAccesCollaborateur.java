package com.hr.referentiel.domain;

/**
 * Profil d'accès applicatif pour un collaborateur (CDC : collaborateur standard vs responsable).
 */
public enum ProfilAccesCollaborateur {

	/** Accès application collaborateur (rôle USER). */
	COLLABORATEUR,

	/** Collaborateur avec responsabilités hiérarchiques (USER + RESPONSABLE). */
	RESPONSABLE
}
