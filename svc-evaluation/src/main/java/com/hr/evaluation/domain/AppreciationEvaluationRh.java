package com.hr.evaluation.domain;

public enum AppreciationEvaluationRh {
	INSUFFISANT("Insuffisant"),
	A_AMELIORER("À améliorer"),
	SATISFAISANT("Satisfaisant"),
	POSITIF("Positif"),
	EXCELLENT("Excellent");

	private final String libelle;

	AppreciationEvaluationRh(String libelle) {
		this.libelle = libelle;
	}

	/** Libellé français à afficher (D-L01) ; le code reste dans {@link #name()}. */
	public String libelle() {
		return libelle;
	}
}
