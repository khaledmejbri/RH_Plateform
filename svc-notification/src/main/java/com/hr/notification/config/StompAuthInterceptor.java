package com.hr.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null) {
                authHeader = accessor.getFirstNativeHeader("authorization");
            }
            
            log.info("STOMP CONNECT received");
            log.info("Authorization header present: {}", authHeader != null);
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                log.info("JWT Token found (length: {})", token.length());
                
                // For now, we'll extract user info from JWT without full validation
                // In production, you should validate the JWT signature here
                try {
                    // Extract user ID from JWT payload
                    String userId = extractUserIdFromToken(token);
                    
                    if (userId != null) {
                        // Set the principal for user-specific messaging
                        accessor.setUser(new Principal() {
                            @Override
                            public String getName() {
                                return userId;
                            }
                        });
                        log.info("✅ User authenticated via STOMP: {}", userId);
                    } else {
                        log.warn("⚠️ Could not extract user ID from token");
                    }
                } catch (Exception e) {
                    log.error("❌ Error processing JWT token", e);
                }
            } else {
                log.warn("⚠️ No valid Authorization header found in STOMP CONNECT");
            }
        }

        return message;
    }

    /**
     * Extract user ID from JWT token without full validation
     * In production, use proper JWT validation with signature check
     */
    private String extractUserIdFromToken(String token) {
        try {
            // JWT has 3 parts: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT format");
                return null;
            }

            // Decode payload (base64url)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            
            // Simple JSON parsing to extract user ID
            // Look for common user ID fields
            if (payload.contains("\"identifiant_utilisateur\"")) {
                // Extract value after "identifiant_utilisateur":"
                int start = payload.indexOf("\"identifiant_utilisateur\":\"") + "\"identifiant_utilisateur\":\"".length();
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return payload.substring(start, end);
                }
            }
            
            if (payload.contains("\"sub\"")) {
                int start = payload.indexOf("\"sub\":\"") + "\"sub\":\"".length();
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return payload.substring(start, end);
                }
            }
            
            log.warn("Could not find user ID in JWT payload");
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting user ID from JWT", e);
            return null;
        }
    }
}
