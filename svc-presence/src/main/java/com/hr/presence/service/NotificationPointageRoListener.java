package com.hr.presence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;

/**
 * Listener post-commit soumettant l'évaluation des notifications de pointage sur Virtual Threads Java 21.
 */
@Component
public class NotificationPointageRoListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationPointageRoListener.class);
	private final NotificationPointageRoService notificationService;
	private final ExecutorService virtualExecutor;

	public NotificationPointageRoListener(
			NotificationPointageRoService notificationService,
			@Qualifier("pointageNotificationVirtualExecutor") ExecutorService virtualExecutor) {
		this.notificationService = notificationService;
		this.virtualExecutor = virtualExecutor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPointageValideCree(PointageValideCreeEvent event) {
		virtualExecutor.execute(() -> {
			try {
				notificationService.traiterAvecSociete(event);
			} catch (Throwable t) {
				log.warn("Notification pointage RO non traitée pointageId={} : {}",
						event != null ? event.pointageId() : null, t.getMessage());
			}
		});
	}
}

