package com.hr.evaluation.domain;

/** Type d'événement de l'historique d'une alerte d'écart (non modifiable, conservé 5 ans, C-L-02). */
public enum EvenementAlerteEcart {
    CREATION("Création"),
    RECALCUL("Recalcul"),
    PRISE_EN_CHARGE("Prise en charge"),
    CLOTURE("Clôture"),
    REOUVERTURE_AUTOMATIQUE("Réouverture automatique");

    private final String libelle;

    EvenementAlerteEcart(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé français (D-L01.b). */
    public String libelle() {
        return libelle;
    }
}
