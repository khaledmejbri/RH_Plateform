package com.hr.evaluation.dto;

import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la liste des alertes d'écart (JSON camelCase). {@code niveau} = niveau courant ; {@code sensGlobal} =
 * RO_INFERIEUR, RO_SUPERIEUR, DEUX_SENS ou null, calculé sur les compétences encore en écart.
 */
public record AlerteEcartResume(
        UUID identifiant,
        UUID evaluationId,
        UUID campagneId,
        String campagneNom,
        String collaborateurNom,
        String collaborateurMatricule,
        UUID collaborateurUniteId,
        String collaborateurUniteLibelle,
        String superieurNom,
        NiveauEcart niveau,
        NiveauEcart niveauDetection,
        StatutAlerteEcart statut,
        String sensGlobal,
        int nbCompetencesEnEcart,
        int ecartMax,
        Instant detecteeLe,
        boolean notesModifieesApresDetection) {
}
