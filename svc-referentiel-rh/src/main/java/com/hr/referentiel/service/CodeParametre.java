package com.hr.referentiel.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Génération du code d'une valeur depuis son libellé (D-R02.a) : majuscules sans accents, tout caractère hors
 * {@code [A-Z0-9]} remplacé par {@code _}, {@code _} consécutifs fusionnés, bords retirés, 40 caractères au plus,
 * 2 au moins (sinon préfixe {@code VAL_}). Si le code est pris, suffixe {@code _2}, {@code _3}…
 */
final class CodeParametre {

	static final int LONGUEUR_MAX = 40;

	private CodeParametre() {
	}

	static String generer(String libelle, Predicate<String> dejaPris) {
		String base = base(libelle);
		if (!dejaPris.test(base)) {
			return base;
		}
		for (int n = 2; n < 10_000; n++) {
			String suffixe = "_" + n;
			String tronque = coupe(base, LONGUEUR_MAX - suffixe.length());
			String candidat = tronque + suffixe;
			if (!dejaPris.test(candidat)) {
				return candidat;
			}
		}
		throw new IllegalStateException("Impossible de générer un code unique pour : " + libelle);
	}

	static String base(String libelle) {
		String s = Normalizer.normalize(libelle == null ? "" : libelle, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (s.length() < 2) {
			s = s.isEmpty() ? "VAL" : "VAL_" + s;
		}
		return coupe(s, LONGUEUR_MAX);
	}

	private static String coupe(String s, int max) {
		String t = s.length() > max ? s.substring(0, max) : s;
		return t.replaceAll("_+$", "");
	}
}
