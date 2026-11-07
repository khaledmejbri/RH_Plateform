package com.hr.referentiel.domain;

public enum ActionWorkflowFormation {
	CREATION_DEMANDE("Demande créée"),
	SOUMISE_A_RH("Soumise à la RH"),
	VALIDATION_RRH("Validée par la RH"),
	REFUS_RRH("Refusée par la RH"),
	INTEGRATION_PLAN("Intégrée au plan de formation"),
	ANNULATION_DEMANDEUR("Annulée par le demandeur"),
	NOTIFICATION_ENVOYEE("Invitation envoyée"),
	EVALUATION_CHAUDE_ENVOYEE("Évaluation à chaud envoyée"),
	EVALUATION_CHAUDE_RECUE("Évaluation à chaud reçue"),
	EVALUATION_FROIDE_ENVOYEE("Évaluation à froid envoyée"),
	EVALUATION_FROIDE_RECUE("Évaluation à froid reçue"),
	FORMATION_TERMINEE("Formation terminée");

	private final String libelle;

	ActionWorkflowFormation(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français exposé à la place du code d'étape ({@code etape_libelle}). */
	public String getLibelle() {
		return libelle;
	}
}
