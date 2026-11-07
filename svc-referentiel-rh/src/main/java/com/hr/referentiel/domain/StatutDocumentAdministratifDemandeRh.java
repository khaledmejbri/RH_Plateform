package com.hr.referentiel.domain;

/**
 * File d'attente : premier arrivé, premier servi pour les demandes en EN_ATTENTE_FILE.
 */
public enum StatutDocumentAdministratifDemandeRh {
	EN_ATTENTE_FILE("En file d'attente"),
	EN_TRAITEMENT_RH("En traitement par la RH"),
	DISPONIBLE("Disponible"),
	REJETEE("Refusée");

	private final String libelle;

	StatutDocumentAdministratifDemandeRh(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français du statut, pour les textes destinés à l'utilisateur. */
	public String getLibelle() {
		return libelle;
	}
}
