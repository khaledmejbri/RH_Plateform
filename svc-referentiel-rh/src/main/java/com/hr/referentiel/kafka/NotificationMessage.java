package com.hr.referentiel.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Message JSON publié sur {@code rh.notifications}. {@code numero} (D-L02) n'est présent que pour les
 * notifications qui portent sur une demande, un document ou une formation.
 */
public record NotificationMessage(
    String type,
    String recipient,
    String subject,
    String content,
    @JsonInclude(JsonInclude.Include.NON_NULL) String numero
) {
    public NotificationMessage(String type, String recipient, String subject, String content) {
        this(type, recipient, subject, content, null);
    }
}
