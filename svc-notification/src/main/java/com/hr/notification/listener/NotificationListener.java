package com.hr.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.service.EmailService;
import com.hr.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final EmailService emailService;
    private final WebSocketNotificationService webSocketNotificationService;
    private final ObjectMapper objectMapper;
    private final org.springframework.web.client.RestTemplate restTemplate;

    @KafkaListener(topics = "notifications-topic", groupId = "notification-group")
    public void handleNotificationMessage(String messagePayload) {
        log.info("Received notification message: {}", messagePayload);
        try {
            NotificationMessage message = objectMapper.readValue(messagePayload, NotificationMessage.class);

            if ("EMAIL".equalsIgnoreCase(message.getType()) || "BOTH".equalsIgnoreCase(message.getType())) {
                emailService.sendTextEmail(message.getRecipient(), message.getSubject(), message.getContent());
            }

            if ("WEBSOCKET".equalsIgnoreCase(message.getType()) || "BOTH".equalsIgnoreCase(message.getType())) {
                if ("HR".equalsIgnoreCase(message.getRecipient())) {
                    log.info("HR recipient detected, fetching users with role RH from identity service");
                    try {
                        String url = "http://svc-identite-acces/internal/users/by-role?role=RH";
                        com.hr.notification.dto.UserIdentityResponse[] users = 
                            restTemplate.getForObject(url, com.hr.notification.dto.UserIdentityResponse[].class);
                        
                        if (users != null) {
                            for (com.hr.notification.dto.UserIdentityResponse user : users) {
                                log.info("Sending notification to HR user: {}", user.getId());
                                webSocketNotificationService.sendToUser(user.getId().toString(), message);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to fetch users for role RH", e);
                    }
                } else {
                    webSocketNotificationService.sendToUser(message.getRecipient(), message);
                }
            }

        } catch (Exception e) {
            log.error("Error processing notification message", e);
        }
    }
}
