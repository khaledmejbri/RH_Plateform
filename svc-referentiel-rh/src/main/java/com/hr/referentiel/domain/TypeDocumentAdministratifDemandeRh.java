package com.hr.referentiel.domain;

/**
 * Délais SLA par défaut (heures) — surchargeables via {@code referentiel.evenements.document-sla-heures-par-type.*}.
 * CDC v2 §M02 : ajout FEUILLE_POINTAGE_MENSUELLE (< 1h, automatique).
 */
public enum TypeDocumentAdministratifDemandeRh {
	ATTESTATION_TRAVAIL(24),
	ATTESTATION_SALAIRE(24),
	BULLETIN_PAIE(48),
	ATTESTATION_CNSS(48),
	FEUILLE_POINTAGE_MENSUELLE(1),
	DOCUMENT_INTERNE(96),
	AUTRE(120);

	private final int delaiSlaHeuresDefaut;

	TypeDocumentAdministratifDemandeRh(int delaiSlaHeuresDefaut) {
		this.delaiSlaHeuresDefaut = delaiSlaHeuresDefaut;
	}

	public int getDelaiSlaHeuresDefaut() {
		return delaiSlaHeuresDefaut;
	}
}
