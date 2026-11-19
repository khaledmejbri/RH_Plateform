package com.hr.referentiel.champsperso;

/** Type d'un champ personnalisé (D-I28 à D-I30). Stocké en varchar via {@link TypeValeurPersoConverter} (aucun CHECK). */
public enum TypeValeurPerso {

	TEXTE("Texte"),
	NOMBRE("Nombre"),
	DATE("Date"),
	LISTE("Liste de valeurs");

	private final String libelle;

	TypeValeurPerso(String libelle) {
		this.libelle = libelle;
	}

	public String libelle() {
		return libelle;
	}

	/** Lecture tolérante : {@code null} si absent ou inconnu. */
	public static TypeValeurPerso lire(String code) {
		if (code == null) {
			return null;
		}
		for (TypeValeurPerso t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		return null;
	}
}
