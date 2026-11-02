package com.hr.presence.domain;

/** État du comptage d'un jour (décision client n° 3) : pointage orphelin une fois la fenêtre fermée = INCOMPLET. */
public enum EtatJourHeuresSup {
	COMPLET,
	INCOMPLET,
	EN_COURS
}
