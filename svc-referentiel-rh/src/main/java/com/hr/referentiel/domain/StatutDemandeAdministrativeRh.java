package com.hr.referentiel.domain;

public enum StatutDemandeAdministrativeRh {
	SOUMISE("Soumise"),
	EN_VALIDATION_SUPERIEUR("En attente du responsable"),
	EN_VALIDATION_RRH("En attente de validation RH"),
	APPROUVEE("Approuvée"),
	REFUSEE("Refusée"),
	ANNULEE("Annulée");

	private final String libelle;

	StatutDemandeAdministrativeRh(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français du statut, pour les textes destinés à l'utilisateur. */
	public String getLibelle() {
		return libelle;
	}
}
