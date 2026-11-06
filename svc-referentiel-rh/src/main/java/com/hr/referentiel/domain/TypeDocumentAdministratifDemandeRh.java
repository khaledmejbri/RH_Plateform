package com.hr.referentiel.domain;

/**
 * Délais SLA par défaut (heures) — surchargeables via {@code referentiel.evenements.document-sla-heures-par-type.*}.
 * CDC v2 §M02 : ajout FEUILLE_POINTAGE_MENSUELLE (< 1h, automatique).
 */
public enum TypeDocumentAdministratifDemandeRh {
	ATTESTATION_TRAVAIL(24, "attestation de travail"),
	ATTESTATION_SALAIRE(24, "attestation de salaire"),
	BULLETIN_PAIE(48, "bulletin de paie"),
	ATTESTATION_CNSS(48, "attestation CNSS"),
	FEUILLE_POINTAGE_MENSUELLE(1, "feuille de pointage mensuelle"),
	DOCUMENT_INTERNE(96, "document interne"),
	AUTRE(120, "autre document");

	private final int delaiSlaHeuresDefaut;
	private final String libelle;

	TypeDocumentAdministratifDemandeRh(int delaiSlaHeuresDefaut, String libelle) {
		this.delaiSlaHeuresDefaut = delaiSlaHeuresDefaut;
		this.libelle = libelle;
	}

	/** Libellé français pour les messages (D-F06.b). */
	public String getLibelle() {
		return libelle;
	}

	public int getDelaiSlaHeuresDefaut() {
		return delaiSlaHeuresDefaut;
	}
}
