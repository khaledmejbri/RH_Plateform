package com.hr.evaluation.domain;

/** Statut de traitement d'une alerte d'écart par la RH (D-E08.a) : OUVERTE, EN_COURS puis TRAITEE. */
public enum StatutAlerteEcart {
    OUVERTE("Ouverte"),
    EN_COURS("En cours"),
    TRAITEE("Traitée");

    private final String libelle;

    StatutAlerteEcart(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé français (D-L01.b). */
    public String libelle() {
        return libelle;
    }
}
