package com.hr.referentiel.domain;

/**
 * File d'attente : premier arrivé, premier servi pour les demandes en EN_ATTENTE_FILE.
 */
public enum StatutDocumentAdministratifDemandeRh {
	EN_ATTENTE_FILE,
	EN_TRAITEMENT_RH,
	DISPONIBLE,
	REJETEE
}
