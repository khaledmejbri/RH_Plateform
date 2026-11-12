package com.hr.evaluation.service;

import java.util.Set;
import java.util.UUID;

/**
 * Événement interne publié dans la transaction de détection ou de recalcul et consommé après le commit
 * ({@code AlerteEcartNotificationListener}). Il porte tout le nécessaire : aucune relecture après commit.
 *
 * @param niveauLibelle libellé <b>courant</b> du niveau au moment de la détection (titre de la notification)
 * @param comptesExclus comptes du collaborateur et du RO de l'évaluation, jamais notifiés (D-E07.e)
 */
public record AlerteEcartANotifierEvent(
        UUID alerteId,
        String niveau,
        String niveauLibelle,
        String collaborateurNom,
        String campagneNom,
        int nbCompetencesEnEcart,
        int ecartMax,
        Set<UUID> comptesExclus) {
}
