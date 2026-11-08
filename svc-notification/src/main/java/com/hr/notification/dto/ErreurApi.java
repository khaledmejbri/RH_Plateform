package com.hr.notification.dto;

/** Corps d'erreur : {@code code} stable pour les clients, {@code message} lisible. */
public record ErreurApi(String code, String message) {
}
