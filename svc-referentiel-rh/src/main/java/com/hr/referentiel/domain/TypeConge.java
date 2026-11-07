package com.hr.referentiel.domain;

import java.util.Locale;

/**
 * Catalogue fermé des types de congé ({@code contenu.type_conge}) — M01 § 6.2.
 * PJ / certificat obligatoire uniquement pour {@link #MALADIE} et {@link #MATERNITE}.
 */
public enum TypeConge {

	ANNUEL("congé annuel"),
	MALADIE("congé maladie"),
	MATERNITE("congé maternité"),
	SANS_SOLDE("congé sans solde"),
	AUTRE("congé");

	private final String libelle;

	TypeConge(String libelle) {
		this.libelle = libelle;
	}

	/** Libellé français sans code, pour les textes rédigés par le serveur. */
	public String getLibelle() {
		return libelle;
	}

	public static final String VALEURS_ACCEPTEES =
			"ANNUEL, MALADIE, MATERNITE, SANS_SOLDE, AUTRE";

	public static final String MESSAGE_INVALIDE =
			"type_conge invalide : " + VALEURS_ACCEPTEES + ".";

	public static final String MESSAGE_CERTIFICAT_OBLIGATOIRE =
			"Un certificat / pièce jointe est obligatoire pour un congé maladie ou maternité.";

	/** {@code true} si une pièce jointe (certificat médical / maternité) est exigée. */
	public boolean exigePieceJointe() {
		return this == MALADIE || this == MATERNITE;
	}

	/**
	 * Parse une valeur catalogue. {@code null} / blank → {@link IllegalArgumentException}
	 * (le champ {@code type_conge} est déjà obligatoire en amont).
	 */
	public static TypeConge parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException(MESSAGE_INVALIDE);
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(MESSAGE_INVALIDE);
		}
	}
}
