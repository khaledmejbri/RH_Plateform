package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/**
 * Tri de la liste des alertes (D-E08.e), appliqué avant la pagination : non closes avant {@code TRAITEE} ;
 * niveau le plus grave d'abord (comparaison des <b>seuils</b> courants des codes, ADR lot 3 § 3) ; date de détection
 * croissante ; identifiant croissant.
 */
public final class AlerteEcartComparateur implements Comparator<AlerteEcart> {

    private final Comparator<AlerteEcart> ordre;

    /** @param seuilDuNiveau seuil courant d'un code de niveau ({@code ALIGNE} = 0) */
    public AlerteEcartComparateur(ToIntFunction<String> seuilDuNiveau) {
        this.ordre = Comparator
                .comparing((AlerteEcart a) -> a.getStatut() == StatutAlerteEcart.TRAITEE)
                .thenComparing(Comparator.comparingInt((AlerteEcart a) -> seuilDuNiveau.applyAsInt(a.getNiveauCourant()))
                        .reversed())
                .thenComparing(AlerteEcart::getDetecteeLe, Comparator.nullsLast(Comparator.naturalOrder()))
                // Ordre textuel (= ordre binaire non signé de PostgreSQL) : UUID.compareTo compare des longs signés
                .thenComparing(a -> a.getId() != null ? a.getId().toString() : null,
                        Comparator.nullsLast(Comparator.<String>naturalOrder()));
    }

    @Override
    public int compare(AlerteEcart a, AlerteEcart b) {
        return ordre.compare(a, b);
    }
}
