package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.ReglesRequest;
import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurListeRequest;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.web.ReferentielMetierException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contrôles purs (sans base) des définitions : corps, libellés, règles par type (ADR partie 2, P2-5). */
public final class ChampPersoValidation {

	public static final int LIBELLE_MIN = 2;
	public static final int LIBELLE_MAX = 80;
	public static final int FINALITE_MIN = 5;
	public static final int FINALITE_MAX = 200;
	public static final int VALEURS_MAX = 100;
	public static final int CHAMPS_MAX = 30;
	static final BigDecimal BORNE_NOMBRE = new BigDecimal("999999999");
	static final LocalDate DATE_PLANCHER = LocalDate.of(1900, 1, 1);
	static final LocalDate DATE_PLAFOND = LocalDate.of(2100, 12, 31);

	private ChampPersoValidation() {
	}

	public static ReferentielMetierException erreur(int statut, String code, String message, String champ) {
		return new ReferentielMetierException(code, message, statut, champ == null ? Map.of() : Map.of("champ", champ));
	}

	public static boolean commenceParCaractereDeFormule(String t) {
		if (t == null || t.isEmpty()) {
			return false;
		}
		char c = t.charAt(0);
		return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
	}

	/** 400 CORPS_INVALIDE : type hors liste. */
	public static TypeValeurPerso type(String brut, String champ) {
		TypeValeurPerso t = TypeValeurPerso.lire(brut);
		if (t == null) {
			throw erreur(400, "CORPS_INVALIDE", "Le type de valeur est obligatoire (TEXTE, NOMBRE, DATE ou LISTE).", champ);
		}
		return t;
	}

	/** 400 CORPS_INVALIDE : finalité rognée de 5 à 200 caractères. */
	public static String finalite(String brut, String champ) {
		String f = brut == null ? "" : brut.strip();
		if (f.length() < FINALITE_MIN || f.length() > FINALITE_MAX) {
			throw erreur(400, "CORPS_INVALIDE", "La finalité doit comporter de " + FINALITE_MIN + " à " + FINALITE_MAX
					+ " caractères.", champ);
		}
		return f;
	}

	/** Libellé de champ rogné : 2 à 80 caractères, forme complète d'au moins 2, pas de caractère de formule. */
	public static String libelle(String brut, String champ) {
		String t = brut == null ? "" : brut.strip();
		if (t.isEmpty()) {
			throw erreur(400, "CORPS_INVALIDE", "Le libellé est obligatoire.", champ);
		}
		if (t.length() < LIBELLE_MIN || t.length() > LIBELLE_MAX
				|| ReconnaissanceEnTetes.normaliserComplet(t).length() < LIBELLE_MIN) {
			throw erreur(400, "LIBELLE_INVALIDE", "Le libellé doit comporter de " + LIBELLE_MIN + " à " + LIBELLE_MAX
					+ " caractères.", champ);
		}
		if (commenceParCaractereDeFormule(t)) {
			throw erreur(400, "LIBELLE_INVALIDE", "Le libellé ne peut pas commencer par = + - @.", champ);
		}
		return t;
	}

	/** Libellé de valeur de liste : 1 à 80 caractères, pas de caractère de formule. */
	public static String libelleValeur(String brut, String champ) {
		String t = brut == null ? "" : brut.strip();
		if (t.isEmpty() || t.length() > LIBELLE_MAX || commenceParCaractereDeFormule(t)) {
			throw erreur(400, "LIBELLE_INVALIDE", "Le libellé d'une valeur doit comporter de 1 à " + LIBELLE_MAX
					+ " caractères et ne peut pas commencer par = + - @.", champ);
		}
		return t;
	}

	/** Libellés de valeurs d'une liste : 1 à 100, formes distinctes (400 LIBELLE_EN_DOUBLE). */
	public static List<String> valeursListe(List<ValeurListeRequest> brutes, String champ) {
		if (brutes == null || brutes.isEmpty() || brutes.size() > VALEURS_MAX) {
			throw erreur(400, "REGLE_INVALIDE", "Une liste comporte de 1 à " + VALEURS_MAX + " valeurs.", champ);
		}
		List<String> libelles = brutes.stream().map(v -> libelleValeur(v == null ? null : v.libelle(), champ)).toList();
		Set<String> formes = new HashSet<>();
		for (String l : libelles) {
			if (!formes.add(ReconnaissanceEnTetes.normaliserComplet(l))) {
				throw erreur(400, "LIBELLE_EN_DOUBLE", "La valeur « " + l + " » figure deux fois dans la liste.", champ);
			}
		}
		return libelles;
	}

	/** Règles du type, normalisées ; 400 REGLE_INVALIDE si une règle d'un autre type est fournie ou hors limites. */
	public static ReglesPerso regles(TypeValeurPerso type, ReglesRequest r, String champ) {
		ReglesRequest q = r == null ? new ReglesRequest(null, null, null, null, null, null) : r;
		boolean texte = q.longueurMax() != null;
		boolean nombre = q.decimales() != null || q.minimum() != null || q.maximum() != null;
		boolean date = q.dateMin() != null || q.dateMax() != null;
		switch (type) {
			case TEXTE -> {
				refuser(nombre || date, "TEXTE", champ);
				if (texte && (q.longueurMax() < 1 || q.longueurMax() > 500)) {
					throw erreur(400, "REGLE_INVALIDE", "La longueur maximale va de 1 à 500 caractères.", champ);
				}
				return new ReglesPerso(q.longueurMax(), null, null, null, null, null);
			}
			case NOMBRE -> {
				refuser(texte || date, "NOMBRE", champ);
				int dec = q.decimales() == null ? 0 : q.decimales();
				if (dec < 0 || dec > 4) {
					throw erreur(400, "REGLE_INVALIDE", "Le nombre de décimales va de 0 à 4.", champ);
				}
				for (BigDecimal b : new BigDecimal[] {q.minimum(), q.maximum()}) {
					if (b != null && b.abs().compareTo(BORNE_NOMBRE) > 0) {
						throw erreur(400, "REGLE_INVALIDE", "Les bornes vont de -999 999 999 à 999 999 999.", champ);
					}
				}
				if (q.minimum() != null && q.maximum() != null && q.minimum().compareTo(q.maximum()) > 0) {
					throw erreur(400, "REGLE_INVALIDE", "Le minimum dépasse le maximum.", champ);
				}
				return new ReglesPerso(null, dec, q.minimum(), q.maximum(), null, null);
			}
			case DATE -> {
				refuser(texte || nombre, "DATE", champ);
				for (LocalDate d : new LocalDate[] {q.dateMin(), q.dateMax()}) {
					if (d != null && (d.isBefore(DATE_PLANCHER) || d.isAfter(DATE_PLAFOND))) {
						throw erreur(400, "REGLE_INVALIDE", "Les dates vont du 01/01/1900 au 31/12/2100.", champ);
					}
				}
				if (q.dateMin() != null && q.dateMax() != null && q.dateMin().isAfter(q.dateMax())) {
					throw erreur(400, "REGLE_INVALIDE", "La date minimale dépasse la date maximale.", champ);
				}
				return new ReglesPerso(null, null, null, null, q.dateMin(), q.dateMax());
			}
			default -> {
				refuser(texte || nombre || date, "LISTE", champ);
				return ReglesPerso.AUCUNE;
			}
		}
	}

	private static void refuser(boolean autreType, String type, String champ) {
		if (autreType) {
			throw erreur(400, "REGLE_INVALIDE", "Une règle d'un autre type est fournie pour un champ de type " + type + ".", champ);
		}
	}
}
