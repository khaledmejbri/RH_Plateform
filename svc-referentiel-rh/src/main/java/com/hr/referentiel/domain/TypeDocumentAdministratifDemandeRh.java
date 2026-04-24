package com.hr.referentiel.domain;

/**
 * Délais par défaut (heures) — surchargeables via {@code referentiel.evenements.document-sla-heures-par-type.*}.
 */
public enum TypeDocumentAdministratifDemandeRh {
	ATTESTATION_TRAVAIL(48),
	ATTESTATION_SALAIRE(48),
	BULLETIN_PAIE(24),
	ATTESTATION_CNSS(72),
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
