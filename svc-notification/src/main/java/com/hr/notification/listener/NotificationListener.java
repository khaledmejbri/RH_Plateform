package com.hr.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.service.AnnuaireComptesClient;
import com.hr.notification.service.EmailService;
import com.hr.notification.service.NotificationStockageService;
import com.hr.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reçoit {@code rh.notifications} : e-mail éventuel, puis, pour WEBSOCKET / BOTH, conservation par compte
 * destinataire AVANT l'envoi temps réel (D-NOT01). Un message rejoué n'est ni dupliqué ni renvoyé.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final EmailService emailService;
    private final WebSocketNotificationService webSocketNotificationService;
    private final NotificationStockageService stockage;
    private final AnnuaireComptesClient annuaire;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "rh.notifications", groupId = "notification-group")
    public void handleNotificationMessage(String messagePayload) {
        NotificationMessage message;
        try {
            message = objectMapper.readValue(messagePayload, NotificationMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Message de notification illisible : ignoré", e);
            return;
        }
        traiter(message);
    }

    void traiter(NotificationMessage message) {
        String type = message.getType();
        if ("EMAIL".equalsIgnoreCase(type) || "BOTH".equalsIgnoreCase(type)) {
            try {
                emailService.sendTextEmail(message.getRecipient(), message.getSubject(), message.getContent());
            } catch (RuntimeException e) {
                log.error("Échec de l'envoi d'e-mail de notification", e);
            }
        }
        if (!"WEBSOCKET".equalsIgnoreCase(type) && !"BOTH".equalsIgnoreCase(type)) {
            return; // EMAIL seul : non conservé
        }
        for (UUID compte : destinataires(message)) {
            conserverEtPousser(message, compte);
        }
    }

    private List<UUID> destinataires(NotificationMessage message) {
        String recipient = message.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            log.warn("Notification sans destinataire : ignorée");
            return List.of();
        }
        if ("RH".equalsIgnoreCase(recipient.trim())) {
            // Une erreur de résolution est propagée : Kafka rejoue le message (idempotent)
            return annuaire.comptesRh();
        }
        try {
            return List.of(UUID.fromString(recipient.trim()));
        } catch (IllegalArgumentException e) {
            // Ancien format (adresse ou nom) : pas de compte, donc rien à conserver
            log.warn("Destinataire qui n'est pas un compte : notification non conservée");
            return List.of();
        }
    }

    private void conserverEtPousser(NotificationMessage message, UUID compte) {
        try {
            stockage.enregistrer(message, compte)
                    .ifPresent(dto -> webSocketNotificationService.sendToUser(compte.toString(), dto));
        } catch (DataIntegrityViolationException e) {
            // Rejeu concurrent du même événement : l'autre traitement a déjà conservé et poussé
            log.info("Notification déjà conservée pour le compte {} (rejeu concurrent)", compte);
        }
    }
}
