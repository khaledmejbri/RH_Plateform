package com.hr.evaluation.service;

import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartResume;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartHistorique;
import com.hr.evaluation.entity.AlerteEcartLigne;

import java.util.List;

/**
 * Entités d'alerte vers DTO. Doit être appelé dans une transaction (collections LAZY). Les libellés D-E05 sont
 * produits ici, côté serveur.
 */
final class AlerteEcartMapper {

    private AlerteEcartMapper() {
    }

    private static List<AlerteEcartLigne> lignesEnEcart(AlerteEcart a) {
        return a.getLignes().stream().filter(l -> l.getNiveauActuel().estAlerte()).toList();
    }

    static String sensGlobal(AlerteEcart a) {
        return EcartEtoiles.sensGlobal(lignesEnEcart(a).stream().map(AlerteEcartLigne::getSensActuel).toList());
    }

    /** Écart max des compétences en écart ; à défaut (toutes revenues à l'alignement), des lignes existantes. */
    static int ecartMax(AlerteEcart a) {
        List<AlerteEcartLigne> enEcart = lignesEnEcart(a);
        List<AlerteEcartLigne> base = enEcart.isEmpty() ? a.getLignes() : enEcart;
        return base.stream().mapToInt(AlerteEcartLigne::getEcartActuel).max().orElse(0);
    }

    /** D-L01.a : l'écran ne dépend jamais d'un nom absent, et n'affiche jamais d'identifiant à la place. */
    static String collaborateurNom(AlerteEcart a) {
        return a.getCollaborateurNom() != null && !a.getCollaborateurNom().isBlank()
                ? a.getCollaborateurNom() : "Collaborateur introuvable";
    }

    static String campagneNom(AlerteEcart a) {
        return a.getCampagneNom() != null && !a.getCampagneNom().isBlank()
                ? a.getCampagneNom() : "Campagne introuvable";
    }

    static AlerteEcartResume resume(AlerteEcart a) {
        return new AlerteEcartResume(
                a.getId(), a.getEvaluationId(), a.getCampagneIdentifiant(), campagneNom(a),
                collaborateurNom(a), a.getCollaborateurMatricule(),
                a.getCollaborateurUniteId(), a.getCollaborateurUniteLibelle(), a.getSuperieurNom(),
                a.getNiveauCourant(), a.getNiveauDetection(), a.getStatut(), sensGlobal(a),
                lignesEnEcart(a).size(), ecartMax(a), a.getDetecteeLe(), a.isNotesModifieesApresDetection());
    }

    static AlerteEcartDetail detail(AlerteEcart a, boolean peutTraiter, String motifNonTraitable) {
        String sens = sensGlobal(a);
        String libelleSens = null;
        String aideSens = null;
        if (SensEcart.DEUX_SENS.equals(sens)) {
            libelleSens = SensEcart.LIBELLE_DEUX_SENS;
            aideSens = SensEcart.AIDE_DEUX_SENS;
        } else if (sens != null) {
            libelleSens = SensEcart.valueOf(sens).libelleSens();
            aideSens = SensEcart.valueOf(sens).aideSens();
        }
        List<AlerteEcartDetail.Ligne> lignes = a.getLignes().stream().map(AlerteEcartMapper::ligne).toList();
        List<AlerteEcartDetail.Evenement> historique = a.getHistorique().stream()
                .map(AlerteEcartMapper::evenement).toList();
        return new AlerteEcartDetail(
                a.getId(), a.getEvaluationId(), a.getCampagneIdentifiant(), campagneNom(a),
                collaborateurNom(a), a.getCollaborateurMatricule(),
                a.getCollaborateurUniteId(), a.getCollaborateurUniteLibelle(), a.getSuperieurNom(),
                a.getNiveauCourant(), a.getNiveauDetection(), a.getStatut(), sens,
                lignesEnEcart(a).size(), ecartMax(a), a.getDetecteeLe(), a.isNotesModifieesApresDetection(),
                libelleSens, aideSens, lignes,
                a.getConclusion(), a.getConclusion() != null ? a.getConclusion().libelle() : null,
                historique, peutTraiter, motifNonTraitable);
    }

    private static AlerteEcartDetail.Ligne ligne(AlerteEcartLigne l) {
        SensEcart sens = l.getSensActuel() != null ? l.getSensActuel() : l.getSensDetection();
        return new AlerteEcartDetail.Ligne(
                l.getQuestionIdentifiant(), l.getCompetenceLibelle(), l.getOrdre(),
                l.getEtoilesCollaborateurDetection(), l.getEtoilesRoDetection(), l.getEcartDetection(),
                l.getNiveauDetection(), l.getSensDetection(),
                l.getEtoilesCollaborateurActuelles(), l.getEtoilesRoActuelles(), l.getEcartActuel(),
                l.getNiveauActuel(), l.getSensActuel(),
                sens != null ? sens.libelleSens() : null, sens != null ? sens.aideSens() : null);
    }

    static final String LIBELLE_CREATION = "Création";

    private static AlerteEcartDetail.Evenement evenement(AlerteEcartHistorique h) {
        boolean systeme = h.getAuteurCompteId() == null;
        return new AlerteEcartDetail.Evenement(
                h.getId(), h.getTypeEvenement(), h.getTypeEvenement().libelle(),
                systeme ? AlerteEcartHistorique.LIBELLE_AUTEUR_SYSTEME : h.getAuteurLibelle(),
                systeme, h.getCreeLe(),
                h.getStatutAvant(), h.getStatutAvant() != null ? h.getStatutAvant().libelle() : LIBELLE_CREATION,
                h.getStatutApres(), h.getStatutApres() != null ? h.getStatutApres().libelle() : null,
                h.getNiveauAvant(), h.getNiveauApres(),
                h.getConclusion(), h.getConclusion() != null ? h.getConclusion().libelle() : null,
                h.getCommentaire());
    }
}
