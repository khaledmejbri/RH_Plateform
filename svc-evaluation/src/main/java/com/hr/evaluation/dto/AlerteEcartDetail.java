package com.hr.evaluation.dto;

import com.hr.evaluation.domain.ConclusionAlerteEcart;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Détail d'une alerte : champs du résumé (à plat) + libellés D-E05 produits par le serveur, lignes, conclusion,
 * historique chronologique et droit de traitement. Aucune entité JPA n'est exposée.
 *
 * @param peutTraiter       rôle RH / ADMIN et appelant non concerné (indépendant du statut)
 * @param motifNonTraitable {@code LECTURE_SEULE}, {@code CONCERNE} ou null
 */
public record AlerteEcartDetail(
        UUID identifiant,
        UUID evaluationId,
        UUID campagneId,
        String campagneNom,
        UUID collaborateurId,
        String collaborateurNom,
        String collaborateurMatricule,
        UUID collaborateurUniteId,
        String collaborateurUniteLibelle,
        UUID superieurId,
        String superieurNom,
        NiveauEcart niveau,
        NiveauEcart niveauDetection,
        StatutAlerteEcart statut,
        String sensGlobal,
        int nbCompetencesEnEcart,
        int ecartMax,
        Instant detecteeLe,
        boolean notesModifieesApresDetection,
        String libelleSensGlobal,
        String aideSensGlobal,
        List<Ligne> lignes,
        ConclusionAlerteEcart conclusion,
        String conclusionLibelle,
        List<Evenement> historique,
        boolean peutTraiter,
        String motifNonTraitable) {

    public static final String MOTIF_LECTURE_SEULE = "LECTURE_SEULE";
    public static final String MOTIF_CONCERNE = "CONCERNE";

    public record Ligne(
            UUID questionId,
            String competence,
            Integer ordre,
            int etoilesCollaborateurDetection,
            int etoilesRoDetection,
            int ecartDetection,
            NiveauEcart niveauDetection,
            SensEcart sensDetection,
            int etoilesCollaborateurActuelles,
            int etoilesRoActuelles,
            int ecartActuel,
            NiveauEcart niveauActuel,
            SensEcart sensActuel,
            String libelleSens,
            String aideSens) {
    }

    /**
     * Événement d'historique. {@code auteurLibelle} est lisible (« Système (automatique) » pour un événement
     * automatique, le code stocké reste SYSTEME) ; {@code statutAvantLibelle} vaut « Création » quand il n'y a
     * pas de statut avant.
     */
    public record Evenement(
            UUID identifiant,
            EvenementAlerteEcart typeEvenement,
            String typeEvenementLibelle,
            String auteurLibelle,
            boolean auteurSysteme,
            Instant le,
            StatutAlerteEcart statutAvant,
            String statutAvantLibelle,
            StatutAlerteEcart statutApres,
            String statutApresLibelle,
            NiveauEcart niveauAvant,
            NiveauEcart niveauApres,
            ConclusionAlerteEcart conclusion,
            String conclusionLibelle,
            String commentaire) {
    }
}
