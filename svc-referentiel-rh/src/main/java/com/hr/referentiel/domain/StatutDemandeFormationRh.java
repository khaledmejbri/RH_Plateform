package com.hr.referentiel.domain;

public enum StatutDemandeFormationRh {
	EN_VALIDATION_RRH("En attente de validation RH"),
	INTEGREE_PLAN("Intégrée au plan"),
	REFUSEE("Refusée"),
	ANNULEE("Annulée");

	private final String libelle;

	StatutDemandeFormationRh(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français du statut, pour les textes destinés à l'utilisateur. */
	public String getLibelle() {
		return libelle;
	}
}
