package com.hr.referentiel.service;

import java.text.Normalizer;
import java.util.Locale;

/** Libellé normalisé (D-R02.b) : minuscules, sans accents, espaces multiples réduits. Sert à l'unicité. */
public final class NormalisationLibelle {

	private NormalisationLibelle() {
	}

	public static String normaliser(String libelle) {
		if (libelle == null) {
			return "";
		}
		String sansAccents = Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return sansAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}
}
