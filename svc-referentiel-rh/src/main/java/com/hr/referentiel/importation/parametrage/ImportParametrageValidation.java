package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.web.ReferentielMetierException;

import java.util.Map;

/**
 * Contrôles purs (sans base) du paramétrage des champs d'import : texte d'un en-tête (D-I20) et d'un synonyme (D-I21).
 * La forme normalisée est toujours calculée par {@link ReconnaissanceEnTetes#normaliser(String)}, la même fonction que
 * la reconnaissance des colonnes.
 */
final class ImportParametrageValidation {

	static final int TEXTE_MIN = 2;
	static final int TEXTE_MAX = 80;
	static final int FORME_MIN = 2;
	static final int SYNONYMES_MAX = 30;

	private ImportParametrageValidation() {
	}

	/** Erreur portant le code du champ visé dans le corps de la réponse. */
	static ReferentielMetierException erreur(int statut, String code, String message, String champ) {
		return new ReferentielMetierException(code, message, statut, champ == null ? Map.of() : Map.of("champ", champ));
	}

	/** En-tête rogné valide ; 400 {@code LIBELLE_INVALIDE} sinon. Le contrôle du premier caractère porte sur le texte rogné. */
	static String enTete(String brut, String champ) {
		return enTete(brut, champ, false);
	}

	/** Champ personnalisé : forme complète (chiffres gardés) pour la longueur minimale. */
	static String enTete(String brut, String champ, boolean personnalise) {
		String t = brut == null ? "" : brut.strip();
		if (t.length() < TEXTE_MIN || t.length() > TEXTE_MAX) {
			throw erreur(400, "LIBELLE_INVALIDE",
					"L'en-tête doit comporter de " + TEXTE_MIN + " à " + TEXTE_MAX + " caractères.", champ);
		}
		if (ReconnaissanceEnTetes.forme(personnalise, t).length() < FORME_MIN) {
			throw erreur(400, "LIBELLE_INVALIDE",
					"L'en-tête doit contenir au moins " + FORME_MIN + " lettres ou chiffres reconnaissables.", champ);
		}
		if (commenceParCaractereDeFormule(t)) {
			throw erreur(400, "LIBELLE_INVALIDE", "L'en-tête ne peut pas commencer par = + - @.", champ);
		}
		return t;
	}

	/** Synonyme rogné valide ; 400 {@code SYNONYME_INVALIDE} sinon. Le texte n'est jamais écrit dans un fichier. */
	static String synonyme(String brut, String champ) {
		return synonyme(brut, champ, false);
	}

	static String synonyme(String brut, String champ, boolean personnalise) {
		String t = brut == null ? "" : brut.strip();
		if (t.length() < TEXTE_MIN || t.length() > TEXTE_MAX) {
			throw erreur(400, "SYNONYME_INVALIDE",
					"Le synonyme doit comporter de " + TEXTE_MIN + " à " + TEXTE_MAX + " caractères.", champ);
		}
		if (ReconnaissanceEnTetes.forme(personnalise, t).length() < FORME_MIN) {
			throw erreur(400, "SYNONYME_INVALIDE",
					"Le synonyme doit contenir au moins " + FORME_MIN + " lettres ou chiffres reconnaissables.", champ);
		}
		return t;
	}

	static boolean commenceParCaractereDeFormule(String texte) {
		if (texte == null || texte.isEmpty()) {
			return false;
		}
		char c = texte.charAt(0);
		return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
	}
}
