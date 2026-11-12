package com.hr.referentiel.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
    private static final java.time.format.DateTimeFormatter FORMAT_JOUR =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CollaborateurRepository collaborateurRepository;

    private final Clock clock;

    @Autowired
    public RhNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            CollaborateurRepository collaborateurRepository) {
        this(kafkaTemplate, objectMapper, collaborateurRepository, Clock.systemUTC());
    }

    /** Horloge injectable pour les tests ({@code emis_le}). */
    RhNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            CollaborateurRepository collaborateurRepository,
            Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.collaborateurRepository = collaborateurRepository;
        this.clock = clock;
    }

    // ─── Demande Administrative ─────────────────────────────────────────────

    /**
     * Étape 1 : demande soumise → notifie le valideur retenu (snapshot {@code valideur_attendu}, règle D-H03 :
     * manager du nœud, ou premier ancêtre managé si le demandeur est ce manager). Sans valideur → RH / ADMIN.
     */
    public void notifierDemandeRecue(Collaborateur demandeur, String typeDemande, UUID demandeId,
            String numero, Collaborateur valideur) {
        UniteOrganisation unite = demandeur.getUnite();
        if (unite == null) {
            notifierTousLesRh(
                    "Nouvelle demande sans unité assignée",
                    "Le collaborateur " + nomComplet(demandeur)
                            + " a soumis une demande " + typeDemande
                            + " mais n'est rattaché à aucune unité.",
                    demandeur, objet("DEMANDE", "DEMANDE_RECUE", demandeId, numero));
            return;
        }

        if (valideur != null) {
            envoyer(valideur,
                    "Demande à valider" + sujetDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande" + complementDemande(typeDemande)
                            + ". Votre validation est requise.", objet("DEMANDE", "DEMANDE_RECUE", demandeId, numero));
        } else {
            notifierTousLesRh(
                    "Demande à valider" + sujetDemande(typeDemande),
                    nomComplet(demandeur) + " a soumis une demande" + complementDemande(typeDemande)
                            + " (aucun responsable pour la valider : approbation directe par la RH).",
                    demandeur, objet("DEMANDE", "DEMANDE_RECUE", demandeId, numero));
        }
    }

    /**
     * Étape 2a : RO a validé → notifie tous les RH actifs pour approbation finale.
     */
    public void notifierValidationRo(Collaborateur demandeur, String typeDemande, Collaborateur ro, UUID demandeId,
            String numero) {
        notifierTousLesRh(
                "Demande validée par le responsable" + sujetDemande(typeDemande),
                "La demande" + complementDemande(typeDemande) + " de " + nomComplet(demandeur)
                        + " a été validée par " + nomComplet(ro)
                        + ". Votre approbation finale est requise.",
                demandeur, objet("DEMANDE", "DEMANDE_VALIDEE_RO", demandeId, numero));
    }

    /**
     * Étape 2b : RO a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRo(Collaborateur demandeur, String typeDemande, String motif, UUID demandeId,
            String numero) {
        envoyer(demandeur,
                "Demande refusée par votre responsable",
                "Votre demande" + complementDemande(typeDemande)
                        + " a été refusée. Motif : " + (motif != null ? motif : "Non précisé"), objet("DEMANDE", "DEMANDE_REFUSEE_RO", demandeId, numero));
    }

    /**
     * Étape 3a : RRH a approuvé → notifie le demandeur.
     */
    public void notifierApprobationRrh(Collaborateur demandeur, String typeDemande, UUID demandeId, String numero) {
        envoyer(demandeur,
                "Demande approuvée ✓",
                "Bonne nouvelle ! Votre demande" + complementDemande(typeDemande)
                        + " a été approuvée par les Ressources Humaines.", objet("DEMANDE", "DEMANDE_APPROUVEE", demandeId, numero));
    }

    /**
     * Étape 3b : RRH a refusé → notifie le demandeur avec le motif.
     */
    public void notifierRefusRrh(Collaborateur demandeur, String typeDemande, String motif, UUID demandeId,
            String numero) {
        envoyer(demandeur,
                "Demande refusée par le RH",
                "Votre demande" + complementDemande(typeDemande)
                        + " a été refusée par les Ressources Humaines."
                        + " Motif : " + (motif != null ? motif : "Non précisé"), objet("DEMANDE", "DEMANDE_REFUSEE_RH", demandeId, numero));
    }

    /**
     * Annulation par le demandeur → notifie le manager du nœud pour information.
     */
    public void notifierAnnulationDemandeur(Collaborateur demandeur, String typeDemande, Collaborateur valideur,
            UUID demandeId, String numero) {
        // SCRUM-28 : prévient le valideur retenu (snapshot), pas le manager recalculé du nœud
        if (valideur == null || valideur.getId().equals(demandeur.getId())) return;
        envoyer(valideur,
                "Demande annulée par le collaborateur",
                nomComplet(demandeur) + " a annulé sa demande" + complementDemande(typeDemande) + ".", objet("DEMANDE", "DEMANDE_ANNULEE", demandeId, numero));
    }

    // ─── Documents Administratifs ───────────────────────────────────────────

    /**
     * Demande document soumise → notifie tous les RH actifs (pas "RH" générique).
     */
    public void notifierDemandeDocumentRecue(Collaborateur demandeur, String typeDocument, UUID demandeId,
            String numero) {
        notifierTousLesRh(
                "Nouvelle demande de document",
                nomComplet(demandeur) + " a demandé un document : "
                        + libelleDocument(typeDocument) + ".", null, objet("DOCUMENT", "DOCUMENT_DEMANDE_RECUE", demandeId, numero));
    }

    /**
     * Document disponible → notifie le demandeur.
     */
    public void notifierDocumentDisponible(Collaborateur demandeur, String typeDocument, String urlDocument,
            UUID demandeId, String numero) {
        envoyer(demandeur,
                "Votre document est prêt",
                "Votre " + libelleDocument(typeDocument)
                        + " est disponible au téléchargement.", objet("DOCUMENT", "DOCUMENT_DISPONIBLE", demandeId, numero));
    }

    /**
     * Document rejeté → notifie le demandeur avec motif.
     */
    public void notifierDocumentRejete(Collaborateur demandeur, String typeDocument, String motif, UUID demandeId,
            String numero) {
        envoyer(demandeur,
                "Demande de document refusée",
                "Votre demande de " + libelleDocument(typeDocument)
                        + " n'a pas pu être traitée. Motif : " + (motif != null ? motif : "Non précisé"), objet("DOCUMENT", "DOCUMENT_REJETE", demandeId, numero));
    }

    /**
     * Dérogation FIFO → notifie le supérieur hiérarchique (RO/DRH) du RH ayant effectué la dérogation.
     * Si aucun supérieur actif n'est trouvé, la notification est envoyée à tous les RH.
     */
    public void notifierDerogationFifo(Collaborateur rhActeur, String typeDocument,
                                       String justification, Collaborateur demandeurDocument, UUID demandeId, String numero) {
        Collaborateur superieur = rhActeur.getSuperieur();
        String sujet = "Dérogation à l'ordre d'arrivée — Document " + libelleDocument(typeDocument);
        String corps = nomComplet(rhActeur)
                + " a traité la demande de " + libelleDocument(typeDocument)
                + " de " + nomComplet(demandeurDocument)
                + " hors de l'ordre d'arrivée. Justification : " + justification;

        if (superieur != null && "ACTIF".equalsIgnoreCase(superieur.getStatut())) {
            envoyer(superieur, sujet, corps, objet("DOCUMENT", "DOCUMENT_DEROGATION_FIFO", demandeId, numero));
        } else {
            log.warn("Aucun supérieur actif pour le RH {} — notification dérogation à l'ordre d'arrivée envoyée à tous les RH",
                    rhActeur.getId());
            notifierTousLesRh(sujet, corps, null, objet("DOCUMENT", "DOCUMENT_DEROGATION_FIFO", demandeId, numero));
        }
    }

    // ─── Plaintes ───────────────────────────────────────────────────────────

    /**
     * Plainte INTERNE soumise → notifie tous les RH.
     */
    public void notifierPlainteInterne(Collaborateur auteur, UUID plainteId, String numeroTicket) {
        notifierTousLesRh(
                "Nouvelle plainte interne — " + numeroTicket,
                nomComplet(auteur) + " a déposé une plainte interne."
                        + " Veuillez la traiter.",
                null, objet("PLAINTE", "PLAINTE_INTERNE_RECUE", plainteId, numeroTicket));
    }

    /**
     * Plainte EXTERNE soumise → notifie simultanément RH + Services Techniques + Direction E&S.
     * Les 3 destinataires sont identifiés par leur profil_acces.
     */
    public void notifierPlainteExterne(Collaborateur auteur, UUID plainteId, String numeroTicket) {
        String sujet = "Nouvelle plainte externe — " + numeroTicket;
        String corps = nomComplet(auteur) + " a déposé une plainte externe."
                + " Votre validation de responsable est requise.";

        Collaborateur superieur = auteur.getSuperieur();
        if (superieur != null && "ACTIF".equalsIgnoreCase(superieur.getStatut())) {
            envoyer(superieur, sujet, corps, objet("PLAINTE", "PLAINTE_EXTERNE_RECUE", plainteId, numeroTicket));
            return;
        }

        notifierTousLesRh(sujet, corps + " Aucun supérieur direct actif n'a été trouvé.", null,
                objet("PLAINTE", "PLAINTE_EXTERNE_RECUE", plainteId, numeroTicket));
    }

    public void notifierValidationRoPlainteExterne(Collaborateur auteur, UUID plainteId, String numeroTicket,
            Collaborateur ro) {
        notifierTousLesRh(
                "Plainte externe validée par le responsable — " + numeroTicket,
                "La plainte externe de " + nomComplet(auteur)
                        + " a été validée par " + nomComplet(ro)
                        + ". Traitement RH requis.",
                null, objet("PLAINTE", "PLAINTE_VALIDEE_RO", plainteId, numeroTicket));
    }

    /**
     * Changement de statut d'une plainte → notifie l'auteur.
     */
    public void notifierChangementStatutPlainte(Collaborateur auteur, UUID plainteId, String numeroTicket,
            String ancienStatut, String nouveauStatut, String commentaire) {
        String msg = "Votre plainte est passée de "
                + libelleStatutPlainte(ancienStatut) + " à " + libelleStatutPlainte(nouveauStatut) + ".";
        if (commentaire != null && !commentaire.isBlank()) {
            msg += " Message RH : " + commentaire;
        }
        envoyer(auteur, "Mise à jour de votre plainte " + numeroTicket, msg,
                objet("PLAINTE", "PLAINTE_STATUT_CHANGE", plainteId, numeroTicket));
    }

    public void notifierDemandeFormationSoumise(Collaborateur demandeur, String typeFormation, UUID formationId,
            String numero) {
        notifierTousLesRh(
                "Nouvelle demande de formation",
                nomComplet(demandeur) + " a soumis une demande de "
                        + libelleTypeFormation(typeFormation) + ". Intégration au plan annuel requise.", null, objet("FORMATION", "FORMATION_SOUMISE", formationId, numero));
    }

    public void notifierDemandeFormationIntegree(Collaborateur demandeur, String typeFormation, UUID formationId,
            String numero) {
        envoyer(demandeur,
                "Formation intégrée au plan",
                "Votre demande de " + libelleTypeFormation(typeFormation)
                        + " a été intégrée au plan annuel de formation.", objet("FORMATION", "FORMATION_INTEGREE", formationId, numero));
    }

    public void notifierDemandeFormationRefusee(Collaborateur demandeur, String typeFormation, String motif,
            UUID formationId, String numero) {
        envoyer(demandeur,
                "Demande de formation refusée",
                "Votre demande de " + libelleTypeFormation(typeFormation)
                        + " a été refusée. Motif : " + (motif != null ? motif : "Non précisé"), objet("FORMATION", "FORMATION_REFUSEE", formationId, numero));
    }

    public void notifierDemandeFormationAnnulee(Collaborateur demandeur, String typeFormation, UUID formationId,
            String numero) {
        notifierTousLesRh(
                "Demande de formation annulée",
                nomComplet(demandeur) + " a annulé sa demande de " + libelleTypeFormation(typeFormation) + ".", null, objet("FORMATION", "FORMATION_ANNULEE", formationId, numero));
    }

    /**
     * Notification envoyée à un collaborateur invité à une formation par un RO.
     */
    public void notifierCollaborateurInviteFormation(Collaborateur collaborateur,
            String typeFormation, String organisme,
            java.time.LocalDate dateDebut, java.time.LocalDate dateFin, UUID formationId, String numero) {
        String periode = "";
        if (dateDebut != null) {
            periode = " du " + FORMAT_JOUR.format(dateDebut);
            if (dateFin != null) {
                periode += " au " + FORMAT_JOUR.format(dateFin);
            }
        }
        envoyer(collaborateur,
                "Invitation à une formation",
                "Vous êtes invité(e) à la " + libelleTypeFormation(typeFormation)
                        + " organisée par " + organisme + periode
                        + ". Votre responsable vous a sélectionné pour cette formation.", objet("FORMATION", "FORMATION_INVITATION", formationId, numero));
    }

    /**
     * Objet porté par une notification (2F) : type de la liste fermée, UUID de l'objet, numéro lisible (D-L02) et
     * code d'événement. Un seul {@code notificationId} par événement, partagé par tous ses destinataires.
     */
    record Objet(String type, String evenement, UUID id, String numero, UUID notificationId) {
    }

    private static Objet objet(String type, String evenement, UUID id, String numero) {
        return new Objet(type, evenement, id, numero, UUID.randomUUID());
    }

    /** Notifie le back-office qui traite l'étape RRH : profils RH et ADMIN actifs (§ 4.2, SCRUM-29). */
    private void notifierTousLesRh(String sujet, String corps, Collaborateur exclu, Objet objet) {
        List<Collaborateur> rhActifs = collaborateurRepository.findAllBackofficeEcritureActifs().stream()
                .filter(c -> exclu == null || !c.getId().equals(exclu.getId()))
                .toList();
        if (rhActifs.isEmpty()) {
            log.warn("Aucun collaborateur RH / ADMIN actif trouvé pour notification : {}", sujet);
            // Fallback : envoyer au groupe générique "RH" que svc-notification résoudra
            envoyer("RH", sujet, corps, objet);
        } else {
            for (Collaborateur rh : rhActifs) {
                envoyer(rh, sujet, corps, objet);
            }
        }
    }

    /**
     * SCRUM-27 : svc-notification route la session STOMP sur le claim JWT {@code identifiant_utilisateur}
     * (= compte). On adresse donc le compte lié à la fiche ; repli sur l'identifiant de fiche sans compte.
     */
    private void envoyer(Collaborateur destinataire, String sujet, String corps, Objet objet) {
        envoyer(adresse(destinataire), sujet, corps, objet);
    }

    static String adresse(Collaborateur c) {
        return c.getCompteUtilisateurId() != null ? c.getCompteUtilisateurId().toString() : c.getId().toString();
    }

    /**
     * D-L02 : le numéro lisible est repris en fin de texte (« [DEM-202611-0003] ») et exposé dans
     * {@code objet_numero} ; 2F : {@code objet_type}, {@code objet_id}, {@code evenement}, {@code notification_id}
     * et {@code emis_le} sont des champs structurés (plus de préfixe dans le texte).
     */
    private void envoyer(String recipientId, String sujet, String corps, Objet objet) {
        try {
            String numero = objet.numero();
                        NotificationMessage msg = new NotificationMessage("WEBSOCKET", recipientId, sujet, corps, numero,
                    objet.notificationId().toString(), objet.evenement(), objet.type(),
                    objet.id() != null ? objet.id().toString() : null, numero, clock.instant().toString());
            kafkaTemplate.send(TOPIC, recipientId, objectMapper.writeValueAsString(msg));
            log.debug("Notification envoyée → {} : {}", recipientId, sujet);
        } catch (Exception e) {
            log.error("Erreur envoi notification Kafka → {} : {}", recipientId, e.getMessage());
        }
    }

    private static String nomComplet(Collaborateur c) {
        return c.getPrenom() + " " + c.getNom();
    }

    /** « (espace)de congé » ou chaîne vide si le type est inconnu : la phrase reste propre. */
    private static String complementDemande(String type) {
        String l = libelleDemande(type);
        return l.isEmpty() ? "" : " " + l;
    }

    /** « (espace)— de congé » ou chaîne vide si le type est inconnu. */
    private static String sujetDemande(String type) {
        String l = libelleDemande(type);
        return l.isEmpty() ? "" : " — " + l;
    }

    /**
     * Groupe nominal complet, toujours en français : « formation technique » pour un code (« TECHNIQUE »),
     * « formation « Excel » » pour un intitulé libre, « formation » sans type.
     */
    static String libelleTypeFormation(String type) {
        if (type == null || type.isBlank()) {
            return "formation";
        }
        String t = type.trim();
        if (!t.matches("[A-Z0-9_]+")) {
            return "formation « " + t + " »";
        }
        return "formation " + switch (t) {
            case "TECHNIQUE" -> "technique";
            case "COMPORTEMENTALE", "COMPORTEMENTAL" -> "comportementale";
            case "MANAGERIALE", "MANAGERIAL" -> "managériale";
            case "REGLEMENTAIRE" -> "réglementaire";
            case "LINGUISTIQUE" -> "linguistique";
            case "CERTIFIANTE" -> "certifiante";
            default -> t.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String libelleDemande(String type) {
        return switch (type) {
            case "CONGE"             -> "de congé";
            case "AUTORISATION_SORTIE" -> "d'autorisation de sortie";
            case "ORDRE_MISSION"     -> "d'ordre de mission";
            default                  -> "";
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
            // Libellé courant déjà rédigé par l'appelant (lot 3) : transmis tel quel, sans changement de casse
            default                          -> type;
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
