package com.hr.workflow.domain;

public enum StatutInstance {
	EN_COURS,
	BLOQUEE,
	APPROUVEE,
	REFUSEE,
	ANNULEE,
	EXPIREE,
	ERREUR;

	public boolean estTerminale() {
		return this == APPROUVEE || this == REFUSEE || this == ANNULEE || this == EXPIREE || this == ERREUR;
	}
}
