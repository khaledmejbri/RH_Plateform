package com.hr.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Pousse la charge utile sur {@code /user/{compte}/queue/notifications} (le contenu n'est pas journalisé). */
    public void sendToUser(String userId, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload);
            log.debug("Notification temps réel envoyée au compte {}", userId);
        } catch (MessagingException e) {
            // La notification est déjà conservée : le destinataire la retrouvera à sa prochaine connexion
            log.error("Échec de l'envoi temps réel au compte {}", userId, e);
        }
    }
}
