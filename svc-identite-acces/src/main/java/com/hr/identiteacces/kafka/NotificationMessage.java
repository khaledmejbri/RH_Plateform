package com.hr.identiteacces.kafka;

public record NotificationMessage(
    String type,
    String recipient,
    String subject,
    String content
) {}
