package com.hr.notification.service;

import com.hr.notification.dto.NotificationMessage;
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

    public void sendToUser(String userId, NotificationMessage message) {
        log.info("📨 Sending websocket notification to user: {}", userId);
        log.info("   - Message subject: {}", message.getSubject());
        log.info("   - Message content: {}", message.getContent());
        
        // Using /topic instead of /user to allow easy testing without JWT STOMP interceptors
        String destination = "/topic/" + userId;
        log.info("   - Destination: {}", destination);
        
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("✅ Notification sent successfully to {}", destination);
        } catch (MessagingException e) {
            log.error("❌ Failed to send notification to {}", destination, e);
            throw new RuntimeException(e);
        }
    }

    public void sendToTopic(String topic, NotificationMessage message) {
        log.info("Sending websocket notification to topic {}", topic);
        messagingTemplate.convertAndSend("/topic/" + topic, message);
    }
}
