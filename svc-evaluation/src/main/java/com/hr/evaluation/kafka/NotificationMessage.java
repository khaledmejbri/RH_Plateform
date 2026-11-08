package com.hr.evaluation.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message du topic {@code rh.notifications} lu par svc-notification. Les champs structurés du ticket 2F
 * ({@code notification_id}, {@code evenement}, {@code objet_*}, {@code emis_le}) sont omis quand ils sont nuls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationMessage(
		String type,
		String recipient,
		String subject,
		String content,
		@JsonProperty("notification_id") String notificationId,
		String evenement,
		@JsonProperty("objet_type") String objetType,
		@JsonProperty("objet_id") String objetId,
		@JsonProperty("objet_numero") String objetNumero,
		@JsonProperty("emis_le") String emisLe
) {
}
