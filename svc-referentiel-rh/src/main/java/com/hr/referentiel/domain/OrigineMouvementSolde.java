package com.hr.referentiel.domain;

/** Origine d'un ajustement ou d'une acquisition du solde de congé. */
public enum OrigineMouvementSolde {
	SAISIE_MANUELLE("Saisie manuelle"),
	IMPORT("Import"),
	SYSTEME("Système (automatique)");

	private final String libelle;

	OrigineMouvementSolde(String libelle) {
		this.libelle = libelle;
	}

	public String getLibelle() {
		return libelle;
	}
}
