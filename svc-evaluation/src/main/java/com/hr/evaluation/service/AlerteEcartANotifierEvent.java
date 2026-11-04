package com.hr.evaluation.service;

import com.hr.evaluation.domain.NiveauEcart;

import java.util.Set;
import java.util.UUID;

/**
 * Événement interne publié dans la transaction de détection ou de recalcul et consommé après le commit
 * ({@code AlerteEcartNotificationListener}). Il porte tout le nécessaire : aucune relecture après commit.
 *
 * @param comptesExclus comptes du collaborateur et du RO de l'évaluation, jamais notifiés (D-E07.e)
 */
public record AlerteEcartANotifierEvent(
        UUID alerteId,
        NiveauEcart niveau,
        String collaborateurNom,
        String campagneNom,
        int nbCompetencesEnEcart,
        int ecartMax,
        Set<UUID> comptesExclus) {
}
