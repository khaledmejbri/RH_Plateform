package com.hr.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message du topic {@code rh.notifications}. Les champs structurés (SCRUM-81 / 2F) sont facultatifs :
 * un producteur non migré envoie encore le seul quadruplet type / recipient / subject / content.
 * {@code objet_id} et {@code emis_le} restent des chaînes : une valeur mal formée ne doit pas faire perdre le message.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
// Les producteurs peuvent ajouter des champs sans casser la lecture.
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationMessage {
    private String type; // e.g., "EMAIL", "WEBSOCKET", "BOTH"
    private String recipient; // email address, compte (UUID) ou "RH"
    private String subject; // optional
    private String content;
    @JsonProperty("notification_id")
    private String notificationId;
    private String evenement;
    @JsonProperty("objet_type")
    private String objetType;
    @JsonProperty("objet_id")
    private String objetId;
    @JsonProperty("objet_numero")
    private String objetNumero;
    @JsonProperty("emis_le")
    private String emisLe;
}
