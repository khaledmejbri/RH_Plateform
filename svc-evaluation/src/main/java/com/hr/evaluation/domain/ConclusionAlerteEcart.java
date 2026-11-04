package com.hr.evaluation.domain;

/** Conclusion saisie par la RH à la clôture d'une alerte d'écart (D-E08.c). */
public enum ConclusionAlerteEcart {
    MANQUE_EFFORT("Manque d'effort confirmé"),
    SURESTIMATION_COLLABORATEUR("Le collaborateur a surestimé son niveau"),
    SUIVI_INSUFFISANT_RO("Le responsable ne suivait pas assez le travail"),
    SOUS_ESTIMATION_COLLABORATEUR("Le collaborateur a sous-estimé son travail"),
    NOTE_GONFLEE_RO("Le responsable a surévalué la note"),
    ECART_JUSTIFIE("Écart expliqué, aucune action"),
    AUTRE("Autre (préciser dans le commentaire)");

    private final String libelle;

    ConclusionAlerteEcart(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
