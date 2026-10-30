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
	REJETE_POSITION_SIMULEE,
	/** Score facial sous le seuil (D-P08) : compte dans les échecs consécutifs (D-P10.a). */
	REJETE_VISAGE_NON_RECONNU,
	/** Contrôle du vivant échoué (D-P08) : compte dans les échecs consécutifs (D-P10.a). */
	REJETE_VIVACITE,
	/** Preuve faciale absente, autre appareil, web ou enrôlement à terminer (D-P08.c) : neutre pour le compteur. */
	REJETE_VERIFICATION_FACIALE_ABSENTE
}
