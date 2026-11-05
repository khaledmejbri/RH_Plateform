package com.hr.evaluation.domain;

/**
 * Niveau d'un écart d'étoiles entre l'auto-évaluation du collaborateur et la note du RO (D-E04.b).
 * Enum ordonné : {@code ALIGNE < ORANGE < ROUGE < URGENTE} (l'ordre de déclaration sert aux comparaisons).
 */
public enum NiveauEcart {
    ALIGNE("Aligné"),
    ORANGE("Alerte"),
    ROUGE("Critique"),
    URGENTE("Urgente");

    private final String libelle;

    NiveauEcart(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai pour un niveau qui constitue une alerte (au moins {@link #ORANGE}). */
    public boolean estAlerte() {
        return compareTo(ORANGE) >= 0;
    }

    public static NiveauEcart max(NiveauEcart a, NiveauEcart b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
