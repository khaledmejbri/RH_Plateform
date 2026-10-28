package com.hr.presence.domain;

public enum PointageStatut {
	VALIDE,
	/** Pointage créé par la validation d'une demande manuelle par le responsable (D-P04). */
	VALIDE_MANUEL,
	REJETE_QR_INVALIDE,
	REJETE_QR_EXPIRE,
	REJETE_QR_REVOQUE,
	REJETE_HORS_ZONE,
	REJETE_SITE_INACTIF,
	REJETE_EMPLACEMENT_ABSENT,
	REJETE_PRECISION_GPS,
	/** Position fournie par une application de faux GPS (D-P01). */
	REJETE_POSITION_SIMULEE
}
