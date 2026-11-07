package com.hr.referentiel.domain;

public enum StatutPlainteRh {
	NOUVEAU("Nouveau"),
	EN_ANALYSE("En analyse"),
	EN_TRAITEMENT("En traitement"),
	RESOLU("Résolu"),
	FERME("Fermé");

	private final String libelle;

	StatutPlainteRh(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français du statut, pour les textes destinés à l'utilisateur. */
	public String getLibelle() {
		return libelle;
	}
}
