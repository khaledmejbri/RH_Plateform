package com.hr.presence.domain;

/** Statut d'enrôlement facial d'un compte (D-P07.4). Aucune donnée biométrique côté serveur. */
public enum StatutEnrolement {
	NON_ENROLE,
	ENROLE,
	REFUSE,
	REVOQUE,
	/** Demande de ré-enrôlement en attente de déblocage RH (D-P07.c). */
	A_REENROLER
}
