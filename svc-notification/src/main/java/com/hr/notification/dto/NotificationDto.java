package com.hr.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification telle que lue par l'API et poussée sur STOMP (même forme : un seul modèle côté client).
 * Les quatre champs {@code type}, {@code recipient}, {@code subject}, {@code content} reprennent l'ancien format
 * pour les clients non encore migrés ; ils seront retirés une version après la migration.
 */
public record NotificationDto(
        UUID id,
        String sujet,
        String contenu,
        String evenement,
        @JsonProperty("objet_type") String objetType,
        @JsonProperty("objet_id") UUID objetId,
        @JsonProperty("objet_numero") String objetNumero,
        @JsonProperty("emis_le") Instant emisLe,
        @JsonProperty("recu_le") Instant recuLe,
        boolean lu,
        // ancien format (déprécié)
        String type,
        String recipient,
        String subject,
        String content) {
}
