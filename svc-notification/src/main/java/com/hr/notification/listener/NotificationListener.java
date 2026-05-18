package com.hr.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.service.EmailService;
import com.hr.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

    @Value("${app.internal-api.token}")
    private String internalApiToken;

    @KafkaListener(topics = "rh.notifications", groupId = "notification-group")
    public void handleNotificationMessage(String messagePayload) {
        log.info("Received notification message: {}", messagePayload);
        try {
            NotificationMessage message = objectMapper.readValue(messagePayload, NotificationMessage.class);

            if ("EMAIL".equalsIgnoreCase(message.getType()) || "BOTH".equalsIgnoreCase(message.getType())) {
                emailService.sendTextEmail(message.getRecipient(), message.getSubject(), message.getContent());
            }

            if ("WEBSOCKET".equalsIgnoreCase(message.getType()) || "BOTH".equalsIgnoreCase(message.getType())) {
                if ("RH".equalsIgnoreCase(message.getRecipient())) {
                    log.info("HR recipient detected, fetching users with role RH from identity service");
                    try {
                        String url = "http://svc-identite-acces/internal/users/by-role?role=RH";
                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-Internal-Token", internalApiToken);
                        HttpEntity<Void> request = new HttpEntity<>(headers);
                        ResponseEntity<com.hr.notification.dto.UserIdentityResponse[]> response =
                                restTemplate.exchange(url, HttpMethod.GET, request,
                                        com.hr.notification.dto.UserIdentityResponse[].class);
                        com.hr.notification.dto.UserIdentityResponse[] users = response.getBody();
                        
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
