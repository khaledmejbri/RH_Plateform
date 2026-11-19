package com.hr.referentiel.champsperso;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Génération des codes (P2-2) : majuscules ASCII, tout caractère hors A-Z0-9 devient « _ », « _ » multiples réduits,
 * bords retirés, tronqué à 40 ; s'il est pris, base tronquée pour loger {@code _2}, {@code _3}… Un code n'est jamais
 * réattribué : l'appelant fournit aussi les codes cités au journal.
 */
public final class CodesChampPerso {

	public static final int LONGUEUR_MAX = 40;
	public static final String PREFIXE = "PERSO_";

	private CodesChampPerso() {
	}

	public static String champ(String libelle, Set<String> pris) {
		return unique(PREFIXE, base(libelle, "CHAMP"), pris);
	}

	public static String valeur(String libelle, Set<String> pris) {
		return unique("", base(libelle, "VALEUR"), pris);
	}

	static String base(String libelle, String repli) {
		String s = Normalizer.normalize(libelle == null ? "" : libelle, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		s = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		return s.isEmpty() ? repli : s;
	}

	private static String unique(String prefixe, String base, Set<String> pris) {
		String code = tronquer(prefixe + base);
		int n = 2;
		while (pris.contains(code)) {
			String suffixe = "_" + n++;
			String corps = (prefixe + base);
			int max = LONGUEUR_MAX - suffixe.length();
			code = (corps.length() > max ? corps.substring(0, max) : corps).replaceAll("_+$", "") + suffixe;
		}
		return code;
	}

	private static String tronquer(String code) {
		return code.length() > LONGUEUR_MAX ? code.substring(0, LONGUEUR_MAX).replaceAll("_+$", "") : code;
	}
}
