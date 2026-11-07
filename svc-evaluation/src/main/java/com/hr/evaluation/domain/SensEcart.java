package com.hr.evaluation.domain;

/**
 * Sens d'un écart sur une compétence (D-E05). Les libellés et aides sont produits par le serveur.
 */
public enum SensEcart {
    RO_INFERIEUR(
            "Note du responsable plus basse : surestimation possible, à clarifier",
            "Vérifier avec le collaborateur et son responsable si le collaborateur surestime son niveau "
                    + "ou si l'appréciation du responsable doit être précisée."),
    RO_SUPERIEUR(
            "Note du responsable plus haute : incohérence, à clarifier",
            "Vérifier si le collaborateur sous-estime son travail ou si la note du responsable doit être justifiée.");

    /** Sens global d'une alerte dont les lignes en écart vont dans les deux sens (valeur calculée, non stockée). */
    public static final String DEUX_SENS = "DEUX_SENS";
    public static final String LIBELLE_DEUX_SENS = "Écarts dans les deux sens";
    public static final String AIDE_DEUX_SENS =
            "Les écarts vont dans les deux sens selon les compétences : examiner chaque ligne.";

    private final String libelleSens;
    private final String aideSens;

    SensEcart(String libelleSens, String aideSens) {
        this.libelleSens = libelleSens;
        this.aideSens = aideSens;
    }

    public String libelleSens() {
        return libelleSens;
    }

    public String aideSens() {
        return aideSens;
    }
}
