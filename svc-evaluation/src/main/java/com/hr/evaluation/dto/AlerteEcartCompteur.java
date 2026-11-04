package com.hr.evaluation.dto;

/** Compteur : alertes non closes (statut différent de TRAITEE), dont urgentes. */
public record AlerteEcartCompteur(long nonCloses, long urgentesNonCloses) {
}
