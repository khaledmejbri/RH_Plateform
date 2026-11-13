package com.hr.referentiel.domain;

/** Période d'un jour de congé (D-S01.a) : journée entière, matin ou après-midi. */
public enum PeriodeJour {
	JOURNEE("Journée entière"),
	MATIN("Matin"),
	APRES_MIDI("Après-midi");

	private final String libelle;

	PeriodeJour(String libelle) {
		this.libelle = libelle;
	}

	public String getLibelle() {
		return libelle;
	}
}
