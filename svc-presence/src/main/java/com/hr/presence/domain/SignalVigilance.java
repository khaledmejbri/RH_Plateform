package com.hr.presence.domain;

/**
 * Signal de vigilance d'une demande de pointage manuel (D-P24.a). Calculé à la création et figé ; stocké sous forme de
 * codes séparés par des virgules (pas de colonne enum, donc pas de contrainte CHECK à maintenir).
 */
public enum SignalVigilance {
	DISTANCE_SUP_2X_RAYON,
	PRECISION_SUP_RAYON,
	SITE_QR_PAPIER,
	MANUELS_MOIS_SEUIL,
	REFUS_FACIAL,
	DEMANDE_TARDIVE
}
