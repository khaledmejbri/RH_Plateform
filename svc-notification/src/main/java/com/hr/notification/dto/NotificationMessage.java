package com.hr.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationMessage {
    private String type; // e.g., "EMAIL", "WEBSOCKET", "BOTH"
    private String recipient; // email address or user ID
    private String subject; // optional
    private String content;
}
