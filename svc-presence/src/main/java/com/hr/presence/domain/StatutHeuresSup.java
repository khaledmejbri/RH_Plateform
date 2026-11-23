package com.hr.presence.domain;

/** Statut d'une demande d'heures supplémentaires (D-P13). */
public enum StatutHeuresSup {
	EN_ATTENTE,
	/** Pilotée par le moteur de workflow (W6) : en cours de validation. */
	EN_COURS,
	VALIDEE,
	REFUSEE,
	ANNULEE,
	/** Encore en attente à l'ouverture de la fenêtre du premier jour (D-P13.d). */
	EXPIREE;

	/** Les lignes « jour » (garde-fou D-P13.f) n'existent que dans ces statuts. */
	public boolean actif() {
		return this == EN_ATTENTE || this == EN_COURS || this == VALIDEE;
	}
}
