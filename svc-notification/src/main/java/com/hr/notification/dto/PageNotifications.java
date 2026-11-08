package com.hr.notification.dto;

import java.util.List;

/** Page de notifications : {@code contenu}, {@code page}, {@code taille}, {@code total}. */
public record PageNotifications(List<NotificationDto> contenu, int page, int taille, long total) {
}
