package com.hr.evaluation.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Génération du code d'une valeur paramétrée et normalisation du libellé (ADR lot 3 § 5, D-R02.a / D-R02.b).
 * Le code est produit par le serveur à la création et ne change jamais.
 */
public final class CodeParametre {

    public static final int LONGUEUR_MAX = 40;
    public static final int LONGUEUR_MIN = 2;
    private static final String PREFIXE_COURT = "VAL_";

    private CodeParametre() {
    }

    /** Minuscules, sans accents, espaces multiples réduits : sert à l'unicité du libellé. */
    public static String normaliser(String libelle) {
        if (libelle == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sansAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /**
     * Majuscules, accents retirés, tout caractère hors {@code [A-Z0-9]} devient {@code _}, tirets bas fusionnés et
     * retirés aux bords, 40 caractères au plus, 2 au moins (sinon préfixe {@code VAL_}). Si le code est pris,
     * suffixe {@code _2}, {@code _3}…
     */
    public static String depuisLibelle(String libelle, Predicate<String> dejaPris) {
        String base = Normalizer.normalize(libelle == null ? "" : libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.length() < LONGUEUR_MIN) {
            base = PREFIXE_COURT + base;
        }
        base = tronquer(base, LONGUEUR_MAX);
        String code = base;
        int n = 2;
        while (dejaPris.test(code)) {
            String suffixe = "_" + n++;
            code = tronquer(base, LONGUEUR_MAX - suffixe.length()) + suffixe;
        }
        return code;
    }

    private static String tronquer(String texte, int max) {
        String t = texte.length() > max ? texte.substring(0, max) : texte;
        return t.replaceAll("_$", "");
    }

    /** DEV_BACKEND devient « Dev backend » (libellé d'une valeur retrouvée sans libellé). */
    public static String libelleDepuisCode(String code) {
        String texte = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return texte.isEmpty() ? code : texte.substring(0, 1).toUpperCase(Locale.ROOT) + texte.substring(1);
    }
}
