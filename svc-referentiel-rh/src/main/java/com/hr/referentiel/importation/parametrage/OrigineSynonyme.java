package com.hr.referentiel.importation.parametrage;

/** Origine d'un synonyme d'en-tête : fourni par le code (non supprimable) ou ajouté par la RH. */
public enum OrigineSynonyme {

	DEFAUT("Par défaut"),
	RH("Ajouté par la RH");

	private final String libelle;

	OrigineSynonyme(String libelle) {
		this.libelle = libelle;
	}

	public String libelle() {
		return libelle;
	}
}
