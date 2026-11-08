package com.hr.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purge quotidienne des notifications au-delà de la rétention (6 mois), heure de Tunis. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPurgeScheduler {

    private final NotificationStockageService stockage;

    @Scheduled(cron = "${notification.purge.cron:0 30 3 * * *}", zone = "${notification.purge.zone:Africa/Tunis}")
    public void purger() {
        try {
            stockage.purger();
        } catch (RuntimeException e) {
            log.error("Échec de la purge des notifications", e);
        }
    }
}
