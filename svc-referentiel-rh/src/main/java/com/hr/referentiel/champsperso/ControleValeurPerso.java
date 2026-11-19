package com.hr.referentiel.champsperso;

import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Contrôle pur d'une valeur de champ personnalisé (ADR partie 2, P2-6), partagé par la fiche, la création, l'ajustement
 * manuel et l'import. Aucun accès à la base : la définition est fournie (instantané ou lecture sous verrou).
 */
public final class ControleValeurPerso {

	public static final int TEXTE_MAX = 500;
	private static final DateTimeFormatter JJMMAAAA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
	private static final Pattern NOMBRE = Pattern.compile("^[+-]?\\d+([.,]\\d+)?$");
	private static final Pattern SERIE_EXCEL = Pattern.compile("^\\d{1,6}(\\.0+)?$");
	private static final BigDecimal BORNE = new BigDecimal("999999999");

	/** Saisie sur la fiche (code de liste exact) ou import (libellé ou code, sans casse ni accents, date Excel). */
	public enum Mode {
		SAISIE, IMPORT
	}

	/** Problème : code d'erreur de l'annexe A bis et message complet « nom : … ». */
	public record Probleme(String code, String message) {
	}

	/** Valeur contrôlée, ou problème ; ni l'un ni l'autre = cellule vide. */
	public record Resultat(ValeurTypee valeur, Probleme probleme) {

		public boolean vide() {
			return valeur == null && probleme == null;
		}
	}

	private ControleValeurPerso() {
	}

	/** @param nom libellé du champ (fiche, ajustement) ou en-tête réglé figé (import) cité dans le message */
	public static Resultat lire(DefinitionPerso def, String brut, Mode mode, String nom) {
		String t = brut == null ? "" : rogner(brut);
		if (t.isEmpty()) {
			return new Resultat(null, null);
		}
		return switch (def.typeValeur()) {
			case TEXTE -> texte(def, t, nom);
			case NOMBRE -> nombre(def, t, mode, nom);
			case DATE -> date(def, t, mode, nom);
			case LISTE -> liste(def, t, mode, nom);
		};
	}

	private static String rogner(String s) {
		return s.replace(' ', ' ').replace(' ', ' ').strip();
	}

	private static Resultat echec(String code, String nom, String detail) {
		return new Resultat(null, new Probleme(code, nom + " : " + detail));
	}

	private static Resultat texte(DefinitionPerso def, String t, String nom) {
		for (int i = 0; i < t.length(); i++) {
			if (Character.isISOControl(t.charAt(i))) {
				return echec("VALEUR_INVALIDE", nom, "caractères de contrôle interdits.");
			}
		}
		int max = def.regles().longueurMax() == null ? TEXTE_MAX : def.regles().longueurMax();
		if (t.length() > max) {
			return echec("VALEUR_TROP_LONGUE", nom, "maximum " + max + " caractères.");
		}
		return new Resultat(ValeurTypee.texte(t), null);
	}

	private static Resultat nombre(DefinitionPerso def, String t, Mode mode, String nom) {
		t = t.replaceAll("\\s+", ""); // espaces retirés (« 4 5 » = 45) ; aucun séparateur de milliers point ou virgule
		if (!NOMBRE.matcher(t).matches()) {
			return echec("FORMAT_INVALIDE", nom, "« " + court(t) + " » n'est pas un nombre (utilisez 12,5 ou 12.5).");
		}
		BigDecimal n = new BigDecimal(t.replace(',', '.'));
		if (mode == Mode.IMPORT) {
			n = n.setScale(Math.max(0, Math.min(n.scale(), 9)), RoundingMode.HALF_UP); // artefact flottant d'Excel
		}
		int decimales = def.regles().decimales() == null ? 0 : def.regles().decimales();
		if (Math.max(0, n.stripTrailingZeros().scale()) > decimales) {
			return echec("VALEUR_INVALIDE", nom, decimales == 0 ? "0 décimale autorisée."
					: decimales + (decimales > 1 ? " décimales autorisées." : " décimale autorisée."));
		}
		BigDecimal min = def.regles().minimum();
		BigDecimal max = def.regles().maximum();
		if (n.abs().compareTo(BORNE) > 0 || (min != null && n.compareTo(min) < 0) || (max != null && n.compareTo(max) > 0)) {
			return echec("VALEUR_HORS_BORNES", nom, "valeur hors des bornes autorisées"
					+ (min != null || max != null ? " (" + (min == null ? "" : "minimum " + plat(min))
							+ (min != null && max != null ? ", " : "") + (max == null ? "" : "maximum " + plat(max)) + ")." : "."));
		}
		return new Resultat(ValeurTypee.nombre(n), null);
	}

	private static String plat(BigDecimal b) {
		return b.stripTrailingZeros().toPlainString().replace('.', ',');
	}

	private static Resultat date(DefinitionPerso def, String t, Mode mode, String nom) {
		LocalDate d = lireDate(t, mode);
		if (d == null) {
			return echec("DATE_INVALIDE", nom, "« " + court(t) + " » n'est pas une date valide (jj/mm/aaaa).");
		}
		LocalDate min = def.regles().dateMin();
		LocalDate max = def.regles().dateMax();
		if (d.isBefore(LocalDate.of(1900, 1, 1)) || d.isAfter(LocalDate.of(2100, 12, 31))
				|| (min != null && d.isBefore(min)) || (max != null && d.isAfter(max))) {
			return echec("VALEUR_HORS_BORNES", nom, "date hors des bornes autorisées.");
		}
		return new Resultat(ValeurTypee.date(d), null);
	}

	/** {@code jj/mm/aaaa} ou {@code aaaa-mm-jj} strict ; en import, numéro de série Excel en plus. */
	static LocalDate lireDate(String t, Mode mode) {
		try {
			if (t.matches("\\d{2}/\\d{2}/\\d{4}")) {
				return LocalDate.parse(t, JJMMAAAA);
			}
			if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
				return LocalDate.parse(t);
			}
			if (mode == Mode.IMPORT && SERIE_EXCEL.matcher(t).matches()) {
				long serie = Long.parseLong(t.replaceAll("\\.0+$", ""));
				return serie < 1 ? null : LocalDate.of(1899, 12, 30).plusDays(serie);
			}
		} catch (DateTimeParseException | ArithmeticException e) {
			return null;
		}
		return null;
	}

	private static Resultat liste(DefinitionPerso def, String t, Mode mode, String nom) {
		ValeurListe trouvee = null;
		for (ValeurListe v : def.valeursListe()) {
			boolean egal = mode == Mode.SAISIE ? v.code().equals(t)
					: ReconnaissanceEnTetes.normaliserComplet(v.libelle()).equals(ReconnaissanceEnTetes.normaliserComplet(t))
							|| ReconnaissanceEnTetes.normaliserComplet(v.code()).equals(ReconnaissanceEnTetes.normaliserComplet(t));
			if (egal) {
				trouvee = v;
				break;
			}
		}
		if (trouvee == null) {
			return echec("VALEUR_INCONNUE", nom, "valeur « " + court(t) + " » inconnue.");
		}
		if (!trouvee.actif()) {
			return echec("VALEUR_INCONNUE", nom, "valeur « " + court(t) + " » désactivée.");
		}
		return new Resultat(ValeurTypee.liste(trouvee.code()), null);
	}

	private static String court(String v) {
		return v.length() > 60 ? v.substring(0, 60) + "…" : v;
	}
}
