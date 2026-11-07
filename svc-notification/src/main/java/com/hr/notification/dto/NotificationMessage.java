package com.hr.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
// Les producteurs peuvent ajouter des champs (ex. numero lisible) sans casser la lecture.
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationMessage {
    private String type; // e.g., "EMAIL", "WEBSOCKET", "BOTH"
    private String recipient; // email address or user ID
    private String subject; // optional
    private String content;
}
