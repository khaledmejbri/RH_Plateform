package com.hr.referentiel.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Message JSON publié sur {@code rh.notifications}. {@code numero} (D-L02) et les champs structurés du ticket 2F
 * ({@code notification_id}, {@code evenement}, {@code objet_type}, {@code objet_id}, {@code objet_numero},
 * {@code emis_le}) ne sont présents que lorsque la notification porte sur un objet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationMessage(
    String type,
    String recipient,
    String subject,
    String content,
    String numero,
    @JsonProperty("notification_id") String notificationId,
    String evenement,
    @JsonProperty("objet_type") String objetType,
    @JsonProperty("objet_id") String objetId,
    @JsonProperty("objet_numero") String objetNumero,
    @JsonProperty("emis_le") String emisLe
) {
}
