package com.hr.presence.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aligné sur {@code svc-notification} (recipient {@code RH} = broadcast rôle RH). Les champs structurés du
 * ticket 2F sont omis quand ils sont nuls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationMessage(
		@JsonProperty("type") String type,
		@JsonProperty("recipient") String recipient,
		@JsonProperty("subject") String subject,
		@JsonProperty("content") String content,
		@JsonProperty("notification_id") String notificationId,
		@JsonProperty("evenement") String evenement,
		@JsonProperty("objet_type") String objetType,
		@JsonProperty("objet_id") String objetId,
		@JsonProperty("objet_numero") String objetNumero,
		@JsonProperty("emis_le") String emisLe
) {
}
