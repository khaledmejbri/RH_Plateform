package com.hr.referentiel.domain;

/** Type d'un mouvement du solde de congé annuel (référentiel de comportement {@code TYPE_MOUVEMENT_SOLDE}). */
public enum TypeMouvementSolde {
	ACQUISITION("Acquisition"),
	CONGE_PRIS("Congé pris"),
	RECREDIT_ANNULATION("Recrédit d'annulation"),
	AJUSTEMENT("Ajustement");

	private final String libelleParDefaut;

	TypeMouvementSolde(String libelleParDefaut) {
		this.libelleParDefaut = libelleParDefaut;
	}

	public String getLibelleParDefaut() {
		return libelleParDefaut;
	}
}
