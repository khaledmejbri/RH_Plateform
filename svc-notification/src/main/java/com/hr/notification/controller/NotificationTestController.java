package com.hr.notification.controller;

import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationTestController {

    private final WebSocketNotificationService webSocketNotificationService;

    /**
     * Test endpoint to send a notification to a specific user
     * Usage: POST /api/notifications/test/send
     * Body: { "userId": "user123", "subject": "Test", "content": "Hello" }
     */
    @PostMapping("/test/send")
    public ResponseEntity<String> sendTestNotification(@RequestBody TestNotificationRequest request) {
        log.info("🧪 Test notification requested for user: {}", request.getUserId());
        
        NotificationMessage message = NotificationMessage.builder()
                .type("WEBSOCKET")
                .recipient(request.getUserId())
                .subject(request.getSubject() != null ? request.getSubject() : "Test Notification")
                .content(request.getContent() != null ? request.getContent() : "This is a test message")
                .build();
        
        try {
            webSocketNotificationService.sendToUser(request.getUserId(), message);
            return ResponseEntity.ok("✅ Notification sent to user: " + request.getUserId());
        } catch (Exception e) {
            log.error("❌ Failed to send test notification", e);
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    /**
     * Send notification to all RH users
     */
    @PostMapping("/test/broadcast-rh")
    public ResponseEntity<String> broadcastToRH(@RequestBody(required = false) TestNotificationRequest request) {
        log.info("🧪 Broadcasting notification to all RH users");
        
        String subject = (request != null && request.getSubject() != null) 
                ? request.getSubject() 
                : "RH Broadcast";
        String content = (request != null && request.getContent() != null) 
                ? request.getContent() 
                : "This is a broadcast message to all RH users";
        
        NotificationMessage message = NotificationMessage.builder()
                .type("WEBSOCKET")
                .recipient("RH")  // This will trigger fetching all RH users
                .subject(subject)
                .content(content)
                .build();
        
        try {
            // Manually trigger the RH broadcast logic
            webSocketNotificationService.sendToTopic("RH", message);
            return ResponseEntity.ok("✅ Broadcast sent to RH topic");
        } catch (Exception e) {
            log.error("❌ Failed to broadcast", e);
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestNotificationRequest {
        private String userId;
        private String subject;
        private String content;
    }
}
