package com.hr.referentiel.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Centralize toutes les notifications RH vers Kafka topic "rh.notifications".
 *
 * Hiérarchie de notification selon CDC v2 §M01 :
 *
 *  Demande soumise          → RO de l'unité du demandeur
 *  RO valide                → tous les RH actifs (RRH)
 *  RO refuse                → demandeur
 *  RRH approuve             → demandeur
 *  RRH refuse               → demandeur
 *  Demandeur annule         → RO de son unité (pour info)
 *
 * Le recipient dans NotificationMessage est l'UUID du collaborateur cible.
 * Le service de notification (svc-notification) utilise cet UUID pour
 * résoudre le device token FCM et envoyer le push.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class RhNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RhNotificationPublisher.class);
    private static final String TOPIC = "rh.notifications";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CollaborateurRepository collaborateurRepository;

    public RhNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            CollaborateurRepository collaborateurRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.collaborateurRepository = collaborateurRepository;
    }

    // ─── Demande Administrative ─────────────────────────────────────────────

    /**
     * Étape 1 : demande soumise → notifie le valideur retenu (snapshot {@code valideur_attendu}, règle D-H03 :
     * manager du nœud, ou premier ancêtre managé si le demandeur est ce manager). Sans valideur → RH / ADMIN.
     */
    public void notifierDemandeRecue(Collaborateur demandeur, String typeDemande, String identifiantDemande,
            Collaborateur valideur) {
        UniteOrganisation unite = demandeur.getUnite();
        if (unite == null) {
            notifierTousLesRh(
                    "Nouvelle demande sans unité assignée",
                    "Le collaborateur " + nomComplet(demandeur)
                            + " a soumis une demande " + typeDemande
                            + " (ref. " + identifiantDemande + ") mais n'est rattaché à aucune unité.",
                    demandeur);
            return;
        }

        if (valideur != null) {
            envoyer(valideur,
                    "Demande à valider — " + libelleDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande " + libelleDemande(typeDemande)
                            + ". Votre validation est requise.");
        } else {
            notifierTousLesRh(
                    "Demande à valider — " + libelleDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande " + libelleDemande(typeDemande)
                            + " (aucun responsable pour la valider : approbation RRH directe).",
                    demandeur);
        }
    }

    /**
     * Étape 2a : RO a validé → notifie tous les RH actifs pour approbation finale.
     */
    public void notifierValidationRo(Collaborateur demandeur, String typeDemande, Collaborateur ro) {
        notifierTousLesRh(
                "Demande validée par le RO — " + libelleDemande(typeDemande),
                "La demande " + libelleDemande(typeDemande) + " de " + nomComplet(demandeur)
                        + " a été validée par " + nomComplet(ro)
                        + ". Votre approbation finale est requise.",
                demandeur);
    }

    /**
     * Étape 2b : RO a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRo(Collaborateur demandeur, String typeDemande, String motif) {
        envoyer(demandeur,
                "Demande refusée par votre responsable",
                "Votre demande " + libelleDemande(typeDemande)
                        + " a été refusée. Motif : " + (motif != null ? motif : "Non précisé"));
    }

    /**
     * Étape 3a : RRH a approuvé → notifie le demandeur.
     */
    public void notifierApprobationRrh(Collaborateur demandeur, String typeDemande) {
        envoyer(demandeur,
                "Demande approuvée ✓",
                "Bonne nouvelle ! Votre demande " + libelleDemande(typeDemande)
                        + " a été approuvée par les Ressources Humaines.");
    }

    /**
     * Étape 3b : RRH a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRrh(Collaborateur demandeur, String typeDemande, String motif) {
        envoyer(demandeur,
                "Demande refusée par le RH",
                "Votre demande " + libelleDemande(typeDemande)
                        + " a été refusée par les Ressources Humaines."
                        + " Motif : " + (motif != null ? motif : "Non précisé"));
    }

    /**
     * Annulation par le demandeur → notifie le manager du nœud pour information.
     */
    public void notifierAnnulationDemandeur(Collaborateur demandeur, String typeDemande, Collaborateur valideur) {
        // SCRUM-28 : prévient le valideur retenu (snapshot), pas le manager recalculé du nœud
        if (valideur == null || valideur.getId().equals(demandeur.getId())) return;
        envoyer(valideur,
                "Demande annulée par le collaborateur",
                nomComplet(demandeur) + " a annulé sa demande " + libelleDemande(typeDemande) + ".");
    }

    // ─── Documents Administratifs ───────────────────────────────────────────

    /**
     * Demande document soumise → notifie tous les RH actifs (pas "RH" générique).
     */
    public void notifierDemandeDocumentRecue(Collaborateur demandeur, String typeDocument) {
        notifierTousLesRh(
                "Nouvelle demande de document",
                nomComplet(demandeur) + " a demandé un document : "
                        + libelleDocument(typeDocument) + ".");
    }

    /**
     * Document disponible → notifie le demandeur.
     */
    public void notifierDocumentDisponible(Collaborateur demandeur, String typeDocument, String urlDocument) {
        envoyer(demandeur,
                "Votre document est prêt",
                "Votre " + libelleDocument(typeDocument)
                        + " est disponible et prêt à être téléchargé.");
    }

    /**
     * Document rejeté → notifie le demandeur avec motif.
     */
    public void notifierDocumentRejete(Collaborateur demandeur, String typeDocument, String motif) {
        envoyer(demandeur,
                "Demande de document refusée",
                "Votre demande de " + libelleDocument(typeDocument)
                        + " n'a pas pu être traitée. Motif : " + (motif != null ? motif : "Non précisé"));
    }

    /**
     * Dérogation FIFO → notifie le supérieur hiérarchique (RO/DRH) du RH ayant effectué la dérogation.
     * Si aucun supérieur actif n'est trouvé, la notification est envoyée à tous les RH.
     */
    public void notifierDerogationFifo(Collaborateur rhActeur, String typeDocument,
                                       String justification, Collaborateur demandeurDocument) {
        Collaborateur superieur = rhActeur.getSuperieur();
        String sujet = "Dérogation FIFO — Document " + libelleDocument(typeDocument);
        String corps = nomComplet(rhActeur)
                + " a traité la demande de " + libelleDocument(typeDocument)
                + " de " + nomComplet(demandeurDocument)
                + " hors de l'ordre FIFO. Justification : " + justification;

        if (superieur != null && "ACTIF".equalsIgnoreCase(superieur.getStatut())) {
            envoyer(superieur, sujet, corps);
        } else {
            log.warn("Aucun supérieur actif pour le RH {} — notification dérogation FIFO envoyée à tous les RH",
                    rhActeur.getId());
            notifierTousLesRh(sujet, corps);
        }
    }

    // ─── Plaintes ───────────────────────────────────────────────────────────

    /**
     * Plainte INTERNE soumise → notifie tous les RH.
     */
    public void notifierPlainteInterne(Collaborateur auteur, String numeroTicket) {
        notifierTousLesRh(
                "Nouvelle plainte interne — " + numeroTicket,
                nomComplet(auteur) + " a déposé une plainte interne [" + numeroTicket + "]."
                        + " Veuillez la traiter.");
    }

    /**
     * Plainte EXTERNE soumise → notifie simultanément RH + Services Techniques + Direction E&S.
     * Les 3 destinataires sont identifiés par leur profil_acces.
     */
    public void notifierPlainteExterne(Collaborateur auteur, String numeroTicket) {
        String sujet = "Nouvelle plainte externe — " + numeroTicket;
        String corps = nomComplet(auteur) + " a déposé une plainte externe [" + numeroTicket + "]."
                + " Votre validation RO est requise.";

        Collaborateur superieur = auteur.getSuperieur();
        if (superieur != null && "ACTIF".equalsIgnoreCase(superieur.getStatut())) {
            envoyer(superieur, sujet, corps);
            return;
        }

        notifierTousLesRh(sujet, corps + " Aucun superieur direct actif n'a ete trouve.");
    }

    public void notifierValidationRoPlainteExterne(Collaborateur auteur, String numeroTicket, Collaborateur ro) {
        notifierTousLesRh(
                "Plainte externe validee par le RO - " + numeroTicket,
                "La plainte externe [" + numeroTicket + "] de " + nomComplet(auteur)
                        + " a ete validee par " + nomComplet(ro)
                        + ". Traitement RH requis.");
    }

    /**
     * Changement de statut d'une plainte → notifie l'auteur.
     */
    public void notifierChangementStatutPlainte(Collaborateur auteur, String numeroTicket,
            String ancienStatut, String nouveauStatut, String commentaire) {
        String msg = "Votre plainte [" + numeroTicket + "] est passée de "
                + libelleStatutPlainte(ancienStatut) + " à " + libelleStatutPlainte(nouveauStatut) + ".";
        if (commentaire != null && !commentaire.isBlank()) {
            msg += " Message RH : " + commentaire;
        }
        envoyer(auteur, "Mise à jour de votre plainte [" + numeroTicket + "]", msg);
    }

    public void notifierDemandeFormationSoumise(Collaborateur demandeur, String typeFormation) {
        notifierTousLesRh(
                "Nouvelle demande de formation",
                nomComplet(demandeur) + " a soumis une demande de formation : "
                        + typeFormation + ". Integration au plan annuel requise.");
    }

    public void notifierDemandeFormationIntegree(Collaborateur demandeur, String typeFormation) {
        envoyer(demandeur,
                "Formation integree au plan",
                "Votre demande de formation " + typeFormation
                        + " a ete integree au plan annuel de formation.");
    }

    public void notifierDemandeFormationRefusee(Collaborateur demandeur, String typeFormation, String motif) {
        envoyer(demandeur,
                "Demande de formation refusee",
                "Votre demande de formation " + typeFormation
                        + " a ete refusee. Motif : " + (motif != null ? motif : "Non precise"));
    }

    public void notifierDemandeFormationAnnulee(Collaborateur demandeur, String typeFormation) {
        notifierTousLesRh(
                "Demande de formation annulee",
                nomComplet(demandeur) + " a annule sa demande de formation : " + typeFormation + ".");
    }

    /**
     * Notification envoyée à un collaborateur invité à une formation par un RO.
     */
    public void notifierCollaborateurInviteFormation(Collaborateur collaborateur,
            String typeFormation, String organisme,
            java.time.LocalDate dateDebut, java.time.LocalDate dateFin) {
        String periode = "";
        if (dateDebut != null) {
            periode = " du " + dateDebut.toString();
            if (dateFin != null) {
                periode += " au " + dateFin.toString();
            }
        }
        envoyer(collaborateur,
                "Invitation à une formation",
                "Vous êtes invité(e) à participer à la formation : " + typeFormation
                        + " organisée par " + organisme + periode
                        + ". Votre responsable vous a sélectionné pour cette formation.");
    }

    /** Notifie le back-office qui traite l'étape RRH : profils RH et ADMIN actifs (§ 4.2, SCRUM-29). */
    private void notifierTousLesRh(String sujet, String corps) {
        notifierTousLesRh(sujet, corps, null);
    }

    /** Idem, sans le collaborateur {@code exclu} : personne n'est notifié pour traiter sa propre demande (D-H03). */
    private void notifierTousLesRh(String sujet, String corps, Collaborateur exclu) {
        List<Collaborateur> rhActifs = collaborateurRepository.findAllBackofficeEcritureActifs().stream()
                .filter(c -> exclu == null || !c.getId().equals(exclu.getId()))
                .toList();
        if (rhActifs.isEmpty()) {
            log.warn("Aucun collaborateur RH / ADMIN actif trouvé pour notification : {}", sujet);
            // Fallback : envoyer au groupe générique "RH" que svc-notification résoudra
            envoyer("RH", sujet, corps);
        } else {
            for (Collaborateur rh : rhActifs) {
                envoyer(rh, sujet, corps);
            }
        }
    }

    /** Notifie tous les collaborateurs d'un profil donné. */
    private void notifierParProfil(String profil, String sujet, String corps) {
        // Pour les profils non-RH (SERVICES_TECHNIQUES, DIRECTION_ENV_SOCIAL),
        // envoyer au groupe — svc-notification résout les membres du groupe
        envoyer("GROUP:" + profil, sujet, corps);
    }

    /**
     * SCRUM-27 : svc-notification route la session STOMP sur le claim JWT {@code identifiant_utilisateur}
     * (= compte). On adresse donc le compte lié à la fiche ; repli sur l'identifiant de fiche sans compte.
     */
    private void envoyer(Collaborateur destinataire, String sujet, String corps) {
        envoyer(adresse(destinataire), sujet, corps);
    }

    static String adresse(Collaborateur c) {
        return c.getCompteUtilisateurId() != null ? c.getCompteUtilisateurId().toString() : c.getId().toString();
    }

    private void envoyer(String recipientId, String sujet, String corps) {
        try {
            NotificationMessage msg = new NotificationMessage("WEBSOCKET", recipientId, sujet, corps);
            kafkaTemplate.send(TOPIC, recipientId, objectMapper.writeValueAsString(msg));
            log.debug("Notification envoyée → {} : {}", recipientId, sujet);
        } catch (Exception e) {
            log.error("Erreur envoi notification Kafka → {} : {}", recipientId, e.getMessage());
        }
    }

    private static String nomComplet(Collaborateur c) {
        return c.getPrenom() + " " + c.getNom();
    }

    private static String libelleDemande(String type) {
        return switch (type) {
            case "CONGE"             -> "de congé";
            case "AUTORISATION_SORTIE" -> "d'autorisation de sortie";
            case "ORDRE_MISSION"     -> "d'ordre de mission";
            default                  -> "(" + type + ")";
        };
    }

    private static String libelleDocument(String type) {
        return switch (type) {
            case "ATTESTATION_TRAVAIL"      -> "attestation de travail";
            case "ATTESTATION_SALAIRE"      -> "attestation de salaire";
            case "BULLETIN_PAIE"            -> "bulletin de paie";
            case "ATTESTATION_CNSS"         -> "attestation CNSS";
            case "FEUILLE_POINTAGE_MENSUELLE" -> "feuille de pointage mensuelle";
            case "DOCUMENT_INTERNE"         -> "document interne";
            default                          -> type.toLowerCase().replace('_', ' ');
        };
    }

    private static String libelleStatutPlainte(String statut) {
        return switch (statut) {
            case "NOUVEAU"        -> "Nouveau";
            case "EN_ANALYSE"     -> "En analyse";
            case "EN_TRAITEMENT"  -> "En traitement";
            case "RESOLU"         -> "Résolu";
            case "FERME"          -> "Fermé";
            default               -> statut;
        };
    }
}
