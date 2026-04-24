package com.hr.referentiel.kafka;

public record NotificationMessage(
    String type,
    String recipient,
    String subject,
    String content
) {}
