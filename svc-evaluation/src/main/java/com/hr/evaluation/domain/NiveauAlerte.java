package com.hr.evaluation.domain;

/**
 * Niveau d'alerte d'écart lu dans le paramétrage (D-R04) : code immuable, libellé et couleur d'habillage, seuil en
 * étoiles d'écart (1 à 4). {@link #ALIGNE} est une constante, hors de la table : « pas d'écart ».
 */
public record NiveauAlerte(String code, String libelle, String couleur, int seuil, boolean actif) {

    /** Niveau d'une compétence sans écart : jamais stocké dans la table des niveaux. */
    public static final String ALIGNE = "ALIGNE";

    /** Libellé de {@link #ALIGNE}. */
    public static final String LIBELLE_ALIGNE = "Aligné";

    public static boolean estAlerte(String code) {
        return code != null && !ALIGNE.equals(code);
    }
}
