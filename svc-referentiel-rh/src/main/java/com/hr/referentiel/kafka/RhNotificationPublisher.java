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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Centralize toutes les notifications RH vers Kafka topic "notifications-topic".
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
     * Étape 1 : demande soumise → notifie le RO de l'unité du demandeur.
     * Si pas de RO → notifie directement les RH.
     */
    public void notifierDemandeRecue(Collaborateur demandeur, String typeDemande, String identifiantDemande) {
        UniteOrganisation unite = demandeur.getUnite();
        if (unite == null) {
            notifierTousLesRh(
                    "Nouvelle demande sans unité assignée",
                    "Le collaborateur " + nomComplet(demandeur)
                            + " a soumis une demande " + typeDemande
                            + " (ref. " + identifiantDemande + ") mais n'est rattaché à aucune unité.");
            return;
        }

        Optional<Collaborateur> ro = collaborateurRepository.findRoByUniteId(unite.getId());
        if (ro.isPresent()) {
            envoyer(ro.get().getId().toString(),
                    "Demande à valider — " + libelleDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande " + libelleDemande(typeDemande)
                            + ". Votre validation est requise.");
        } else {
            // Pas de RO dans cette unité → notifier RH directement
            notifierTousLesRh(
                    "Demande à valider — " + libelleDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande " + libelleDemande(typeDemande)
                            + " (aucun RO dans l'unité " + unite.getLibelle() + ").");
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
                        + ". Votre approbation finale est requise.");
    }

    /**
     * Étape 2b : RO a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRo(Collaborateur demandeur, String typeDemande, String motif) {
        envoyer(demandeur.getId().toString(),
                "Demande refusée par votre responsable",
                "Votre demande " + libelleDemande(typeDemande)
                        + " a été refusée. Motif : " + (motif != null ? motif : "Non précisé"));
    }

    /**
     * Étape 3a : RRH a approuvé → notifie le demandeur.
     */
    public void notifierApprobationRrh(Collaborateur demandeur, String typeDemande) {
        envoyer(demandeur.getId().toString(),
                "Demande approuvée ✓",
                "Bonne nouvelle ! Votre demande " + libelleDemande(typeDemande)
                        + " a été approuvée par les Ressources Humaines.");
    }

    /**
     * Étape 3b : RRH a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRrh(Collaborateur demandeur, String typeDemande, String motif) {
        envoyer(demandeur.getId().toString(),
                "Demande refusée par le RH",
                "Votre demande " + libelleDemande(typeDemande)
                        + " a été refusée par les Ressources Humaines."
                        + " Motif : " + (motif != null ? motif : "Non précisé"));
    }

    /**
     * Annulation par le demandeur → notifie le RO pour information.
     */
    public void notifierAnnulationDemandeur(Collaborateur demandeur, String typeDemande) {
        UniteOrganisation unite = demandeur.getUnite();
        if (unite == null) return;
        collaborateurRepository.findRoByUniteId(unite.getId()).ifPresent(ro ->
                envoyer(ro.getId().toString(),
                        "Demande annulée par le collaborateur",
                        nomComplet(demandeur) + " a annulé sa demande " + libelleDemande(typeDemande) + ".")
        );
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
        envoyer(demandeur.getId().toString(),
                "Votre document est prêt",
                "Votre " + libelleDocument(typeDocument)
                        + " est disponible et prêt à être téléchargé.");
    }

    /**
     * Document rejeté → notifie le demandeur avec motif.
     */
    public void notifierDocumentRejete(Collaborateur demandeur, String typeDocument, String motif) {
        envoyer(demandeur.getId().toString(),
                "Demande de document refusée",
                "Votre demande de " + libelleDocument(typeDocument)
                        + " n'a pas pu être traitée. Motif : " + (motif != null ? motif : "Non précisé"));
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
                + " Traitement requis par votre service.";

        notifierTousLesRh(sujet, corps);
        notifierParProfil("SERVICES_TECHNIQUES", sujet, corps);
        notifierParProfil("DIRECTION_ENV_SOCIAL", sujet, corps);
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
        envoyer(auteur.getId().toString(), "Mise à jour de votre plainte [" + numeroTicket + "]", msg);
    }

    // ─── Helpers privés ─────────────────────────────────────────────────────

    /** Notifie tous les collaborateurs avec profil_acces = RH. */
    private void notifierTousLesRh(String sujet, String corps) {
        List<Collaborateur> rhActifs = collaborateurRepository.findAllRhActifs();
        if (rhActifs.isEmpty()) {
            log.warn("Aucun collaborateur RH actif trouvé pour notification : {}", sujet);
            // Fallback : envoyer au groupe générique "RH" que svc-notification résoudra
            envoyer("GROUP:RH", sujet, corps);
        } else {
            for (Collaborateur rh : rhActifs) {
                envoyer(rh.getId().toString(), sujet, corps);
            }
        }
    }

    /** Notifie tous les collaborateurs d'un profil donné. */
    private void notifierParProfil(String profil, String sujet, String corps) {
        // Pour les profils non-RH (SERVICES_TECHNIQUES, DIRECTION_ENV_SOCIAL),
        // envoyer au groupe — svc-notification résout les membres du groupe
        envoyer("GROUP:" + profil, sujet, corps);
    }

    private void envoyer(String recipientId, String sujet, String corps) {
        try {
            NotificationMessage msg = new NotificationMessage("PUSH", recipientId, sujet, corps);
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
