package com.hr.referentiel.importation.parametrage;

/** Niveau d'exigence d'un champ d'import (D-I15). L'ordre de déclaration est celui de la force croissante. */
public enum ExigenceChamp {

	FACULTATIF("Facultatif"),
	CREATION("Obligatoire à la création"),
	TOUJOURS("Toujours obligatoire");

	private final String libelle;

	ExigenceChamp(String libelle) {
		this.libelle = libelle;
	}

	public String libelle() {
		return libelle;
	}

	/** Vrai si cette exigence est au moins aussi forte que {@code autre}. */
	public boolean auMoins(ExigenceChamp autre) {
		return compareTo(autre) >= 0;
	}

	/** Lecture tolérante : {@code null} si la valeur est absente ou inconnue. */
	public static ExigenceChamp lire(String code) {
		if (code == null) {
			return null;
		}
		for (ExigenceChamp e : values()) {
			if (e.name().equals(code)) {
				return e;
			}
		}
		return null;
	}
}
