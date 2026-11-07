package com.hr.presence.domain;

/** État du comptage d'un jour (décision client n° 3) : pointage orphelin une fois la fenêtre fermée = INCOMPLET. */
public enum EtatJourHeuresSup {
	COMPLET("Complet"),
	INCOMPLET("Incomplet"),
	EN_COURS("En cours");

	private final String libelle;

	EtatJourHeuresSup(String libelle) {
		this.libelle = libelle;
	}

	/** Libellé français (D-L01.b), utilisé par les exports. */
	public String libelle() {
		return libelle;
	}
}
