package com.hr.presence.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.presence.client.PresenceWorkflowMoteurClient;
import com.hr.presence.client.PresenceWorkflowMoteurClient.RegleActivePointage;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.PointageType;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Évalue de façon asynchrone (post-commit sur Virtual Threads Java 21) la règle workflow {@code POINTAGE}
 * et envoie le cas échéant une notification temps réel + persistée au responsable D-H03 du collaborateur (R-07 à R-09).
 */
@Service
public class NotificationPointageRoService {

	private static final Logger log = LoggerFactory.getLogger(NotificationPointageRoService.class);
	private static final String EVENEMENT = "POINTAGE_EQUIPE_EFFECTUE";
	private static final String PORTEE_SOCIETE = "TOUTE_LA_SOCIETE";
	private static final String PORTEE_SITES = "SITES_SELECTIONNES";
	private static final DateTimeFormatter HEURE_TUNIS = DateTimeFormatter.ofPattern("HH:mm");
	private static final int CONCURRENCE_MAX = 64;

	private final PresenceWorkflowMoteurClient workflowClient;
	private final ReferentielClient referentielClient;
	private final PresenceEventPublisher eventPublisher;
	private final ExecutorService virtualExecutor;
	private final Semaphore concurrence = new Semaphore(CONCURRENCE_MAX);

	public NotificationPointageRoService(
			PresenceWorkflowMoteurClient workflowClient,
			ReferentielClient referentielClient,
			PresenceEventPublisher eventPublisher,
			@Qualifier("pointageNotificationVirtualExecutor") ExecutorService virtualExecutor) {
		this.workflowClient = workflowClient;
		this.referentielClient = referentielClient;
		this.eventPublisher = eventPublisher;
		this.virtualExecutor = virtualExecutor;
	}

	public void traiterAvecSociete(PointageValideCreeEvent event) {
		if (event == null || event.pointageId() == null) {
			return;
		}
		if (event.societeId() != null) {
			SocieteContexte.executer(event.societeId(), () -> traiter(event));
		} else {
			traiter(event);
		}
	}

	public void traiter(PointageValideCreeEvent event) {
		if (event == null || event.pointageId() == null || event.collaborateurId() == null) {
			return;
		}
		UUID ficheUuid = parseUuid(event.collaborateurId());
		if (ficheUuid == null) {
			return;
		}

		boolean acquis = false;
		try {
			concurrence.acquire();
			acquis = true;

			CompletableFuture<Optional<RegleActivePointage>> regleFuture = CompletableFuture
					.supplyAsync(avecSociete(event.societeId(), workflowClient::regleActivePointage), virtualExecutor)
					.orTimeout(2500, TimeUnit.MILLISECONDS);

			CompletableFuture<Optional<CollaborateurResolu>> collabFuture = CompletableFuture
					.supplyAsync(avecSociete(event.societeId(), () -> referentielClient.resoudreCollaborateurCourt(ficheUuid)), virtualExecutor)
					.orTimeout(2500, TimeUnit.MILLISECONDS);

			Optional<RegleActivePointage> regleOpt = regleFuture.join();
			if (regleOpt.isEmpty() || !regleApplicable(regleOpt.get(), event)) {
				collabFuture.cancel(true);
				return;
			}
			RegleActivePointage regle = regleOpt.get();

			Optional<CollaborateurResolu> collabOpt = collabFuture.join();
			if (collabOpt.isEmpty()) {
				return;
			}
			CollaborateurResolu collab = collabOpt.get();

			UUID destinataire = collab.valideurCompteId() != null ? collab.valideurCompteId() : collab.valideurId();
			if (destinataire == null || estAutoNotification(destinataire, ficheUuid, collab)) {
				return;
			}

			UUID notificationId = UUID.nameUUIDFromBytes(
					(event.pointageId() + ":" + destinataire + ":" + EVENEMENT)
							.getBytes(StandardCharsets.UTF_8));

			String nomCollab = collab.nom() != null && !collab.nom().isBlank()
					? collab.nom().trim()
					: "Collaborateur";
			String sujet = "Pointage — " + nomCollab;
			String action = event.type() == PointageType.SORTIE ? "sa sortie" : "son entrée";
			String heure = event.serverTs() != null
					? HEURE_TUNIS.format(event.serverTs().atZone(ClockConfig.ZONE_TUNIS))
					: "--:--";
			String libelleSite = event.siteLibelle() != null && !event.siteLibelle().isBlank()
					? event.siteLibelle()
					: "Site";
			String corps = nomCollab + " a pointé " + action + " à " + heure + " sur le site « " + libelleSite + " ».";

			String canal = "BOTH".equalsIgnoreCase(regle.canal()) ? "BOTH" : "WEBSOCKET";
			ObjetNotification objet = new ObjetNotification(
					ObjetNotification.COLLABORATEUR,
					EVENEMENT,
					ficheUuid,
					collab.matricule(),
					notificationId);

			eventPublisher.notifierAvecCanal(canal, destinataire.toString(), sujet, corps, objet);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Notification pointage RO interrompue pointageId={} collab={}",
					event.pointageId(), event.collaborateurId());
		} catch (Throwable ex) {
			log.warn("Notification pointage RO non émise pointageId={} collab={} : {}",
					event.pointageId(), event.collaborateurId(), ex.getMessage());
		} finally {
			if (acquis) {
				concurrence.release();
			}
		}
	}

	private static boolean regleApplicable(RegleActivePointage regle, PointageValideCreeEvent event) {
		if (!regle.actifEffectif() || !regle.notifierRo()) {
			return false;
		}
		if (event.type() == null || regle.typesPointage() == null
				|| !regle.typesPointage().contains(event.type().name())) {
			return false;
		}
		if (PORTEE_SOCIETE.equals(regle.porteeSites())) {
			return true;
		}
		if (PORTEE_SITES.equals(regle.porteeSites())) {
			return event.siteId() != null
					&& regle.siteIds() != null
					&& regle.siteIds().contains(event.siteId());
		}
		return false;
	}

	private static boolean estAutoNotification(UUID destinataire, UUID ficheUuid, CollaborateurResolu collab) {
		return destinataire.equals(ficheUuid)
				|| (collab.compteId() != null && destinataire.equals(collab.compteId()))
				|| (collab.valideurId() != null && collab.valideurId().equals(ficheUuid));
	}

	private static <T> Supplier<T> avecSociete(UUID societeId, Supplier<T> supplier) {
		return () -> societeId != null
				? SocieteContexte.executer(societeId, supplier)
				: supplier.get();
	}

	private static UUID parseUuid(String raw) {
		try {
			return UUID.fromString(raw.trim());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
