package com.hr.referentiel.domain;

public enum ActionWorkflowAdministratif {
	CREATION_DEMANDE("Demande créée"),
	SOUMISE_A_RO("Soumise au responsable"),
	VALIDATION_RO("Validée par le responsable"),
	REFUS_RO("Refusée par le responsable"),
	VALIDATION_RRH("Validée par la RH"),
	REFUS_RRH("Refusée par la RH"),
	APPROBATION_RRH("Approuvée par la RH"),
	ANNULATION_DEMANDEUR("Annulée par le demandeur"),
	ANNULATION_APRES_APPROBATION("Annulée après approbation"),
	CLOTUREE("Clôturée");

	private final String libelle;

	ActionWorkflowAdministratif(String libelle) {
		this.libelle = libelle;
	}

	/** D-L01.b : libellé français exposé à la place du code d'étape ({@code etape_libelle}). */
	public String getLibelle() {
		return libelle;
	}
}
