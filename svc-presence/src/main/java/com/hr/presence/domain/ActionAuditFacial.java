package com.hr.presence.domain;

/** Actions tracées dans l'audit facial (ajout seul, conservé emploi + 5 ans — C-L-02 / C-L-07). */
public enum ActionAuditFacial {
	CONSENTEMENT_PUBLIE,
	REGLAGE_MODIFIE,
	CADRE_LEGAL_CONFIRME,
	CONSENTEMENT_ACCEPTE,
	CONSENTEMENT_REFUSE,
	ENROLEMENT,
	RETRAIT,
	APPAREIL_INCOMPATIBLE,
	DEMANDE_REENROLEMENT,
	DEBLOCAGE_RH,
	BLOCAGE_ECHECS
}
