package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;

import java.util.Comparator;

/**
 * Tri de la liste des alertes (D-E08.e), appliqué avant la pagination : non closes avant {@code TRAITEE} ;
 * {@code URGENTE > ROUGE > ORANGE > ALIGNE} ; date de détection croissante ; identifiant croissant.
 */
public final class AlerteEcartComparateur implements Comparator<AlerteEcart> {

    public static final AlerteEcartComparateur INSTANCE = new AlerteEcartComparateur();

    private static final Comparator<AlerteEcart> ORDRE = Comparator
            .comparing((AlerteEcart a) -> a.getStatut() == StatutAlerteEcart.TRAITEE)
            .thenComparing(AlerteEcart::getNiveauCourant, Comparator.reverseOrder())
            .thenComparing(AlerteEcart::getDetecteeLe, Comparator.nullsLast(Comparator.naturalOrder()))
            // Ordre textuel (= ordre binaire non signé de PostgreSQL) : UUID.compareTo compare des longs signés
            .thenComparing(a -> a.getId() != null ? a.getId().toString() : null,
                    Comparator.nullsLast(Comparator.<String>naturalOrder()));

    @Override
    public int compare(AlerteEcart a, AlerteEcart b) {
        return ORDRE.compare(a, b);
    }
}
