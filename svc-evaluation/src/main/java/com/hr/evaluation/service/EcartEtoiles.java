package com.hr.evaluation.service;

import com.hr.evaluation.domain.SensEcart;

import java.util.Collection;

/**
 * Calcul pur (sans état) de l'écart d'étoiles entre collaborateur et RO (D-E04, D-E05), partagé par la détection
 * d'alerte et l'affichage du détail d'évaluation (D-E09.c) : un seul calcul. Le niveau d'un écart ne se calcule plus
 * ici mais avec les seuils paramétrés ({@link NiveauxAlerte}, ADR lot 3 § 3).
 */
public final class EcartEtoiles {

    public static final int ETOILES_MIN = 1;
    public static final int ETOILES_MAX = 5;

    private EcartEtoiles() {
    }

    /** Vrai si la valeur est une note en étoiles valide (1 à 5). */
    public static boolean estEtoilesValide(Integer valeur) {
        return valeur != null && valeur >= ETOILES_MIN && valeur <= ETOILES_MAX;
    }

    /** |ro - collaborateur| (0 à 4). */
    public static int ecart(int collaborateur, int ro) {
        return Math.abs(ro - collaborateur);
    }

    /** {@code RO_INFERIEUR} si le RO note plus bas, {@code RO_SUPERIEUR} s'il note plus haut, {@code null} à égalité. */
    public static SensEcart sens(int collaborateur, int ro) {
        if (ro < collaborateur) {
            return SensEcart.RO_INFERIEUR;
        }
        if (ro > collaborateur) {
            return SensEcart.RO_SUPERIEUR;
        }
        return null;
    }

    /**
     * Sens global d'une alerte à partir des sens des lignes en écart : un seul sens → ce sens ; les deux →
     * {@link SensEcart#DEUX_SENS} ; aucun → {@code null}. Les {@code null} sont ignorés.
     */
    public static String sensGlobal(Collection<SensEcart> sensDesLignesEnEcart) {
        boolean inferieur = false;
        boolean superieur = false;
        for (SensEcart sens : sensDesLignesEnEcart) {
            if (sens == SensEcart.RO_INFERIEUR) {
                inferieur = true;
            } else if (sens == SensEcart.RO_SUPERIEUR) {
                superieur = true;
            }
        }
        if (inferieur && superieur) {
            return SensEcart.DEUX_SENS;
        }
        if (inferieur) {
            return SensEcart.RO_INFERIEUR.name();
        }
        if (superieur) {
            return SensEcart.RO_SUPERIEUR.name();
        }
        return null;
    }
}
