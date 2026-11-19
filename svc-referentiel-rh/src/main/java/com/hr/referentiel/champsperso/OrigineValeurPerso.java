package com.hr.referentiel.champsperso;

import java.time.Instant;

import com.hr.referentiel.service.FormatJours;

/** Origine d'un changement de valeur (journal des valeurs, annexe A bis.1). */
public enum OrigineValeurPerso {

	SAISIE("Saisie sur la fiche"),
	IMPORT("Import"),
	CREATION("Création de la fiche");

	private final String libelle;

	OrigineValeurPerso(String libelle) {
		this.libelle = libelle;
	}

	public String libelle() {
		return libelle;
	}

	/** « Import du jj/mm/aaaa » (heure de Tunis) pour un import, libellé fixe sinon. */
	public String libelle(Instant horodatage) {
		if (this == IMPORT && horodatage != null) {
			return "Import du " + FormatJours.JOUR.format(horodatage.atZone(FormatJours.TUNIS));
		}
		return libelle;
	}

	public static OrigineValeurPerso lire(String code) {
		for (OrigineValeurPerso o : values()) {
			if (o.name().equals(code)) {
				return o;
			}
		}
		return SAISIE;
	}
}
