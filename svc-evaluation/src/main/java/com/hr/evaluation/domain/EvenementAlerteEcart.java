package com.hr.evaluation.domain;

/** Type d'événement de l'historique d'une alerte d'écart (non modifiable, conservé 5 ans, C-L-02). */
public enum EvenementAlerteEcart {
    CREATION,
    RECALCUL,
    PRISE_EN_CHARGE,
    CLOTURE,
    REOUVERTURE_AUTOMATIQUE
}
