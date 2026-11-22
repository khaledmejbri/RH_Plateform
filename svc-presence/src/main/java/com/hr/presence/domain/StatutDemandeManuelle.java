package com.hr.presence.domain;

/** Cycle d'une demande de pointage manuel (D-P04). */
public enum StatutDemandeManuelle {
	EN_ATTENTE,
	/** Pilotée par le moteur de workflow (W6) : en cours de validation. */
	EN_COURS,
	VALIDEE,
	REFUSEE,
	/** Non traitée avant l'échéance (D-P23.b) : stocké, la contrainte CHECK est recréée au démarrage. */
	EXPIREE
}
