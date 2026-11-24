package com.hr.presence.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class PresenceEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(PresenceEventPublisher.class);

	/** JSON texte, format lu par svc-notification (comme svc-referentiel-rh). */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Destinataire spécial : diffusion à tous les comptes RH (svc-notification). */
	public static final String DESTINATAIRE_RH = "RH";

	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

	private final Clock clock;

	@Autowired
	public PresenceEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
		this(kafkaTemplateProvider, Clock.systemUTC());
	}

	/** Horloge injectable pour les tests ({@code emis_le}). */
	PresenceEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, Clock clock) {
		this.kafkaTemplateProvider = kafkaTemplateProvider;
		this.clock = clock;
	}

	/**
	 * P3 Must — alerte J-30 à tous les comptes RH (destinataire spécial {@code RH}).
	 */
	public void publierQrExpireJ30(
			UUID eventId,
			UUID siteId,
			String libelle,
			Instant validUntil,
			long joursRestants,
			int qrVersion) {
		// Texte lisible seulement (D-L01) : l'événement et le site sont des champs structurés
		String content = "Le QR du site « " + libelle + " » expire dans " + joursRestants + " jour(s).";
		ObjetNotification objet = new ObjetNotification(null, "PRESENCE_QR_EXPIRE_J30", null, null, eventId);
		NotificationMessage msg = message("RH", "QR de pointage bientôt expiré", content, objet);
		try {
			publier(RhPresenceTopics.RH_NOTIFICATIONS, siteId + ":" + qrVersion, msg);
		} catch (Exception ex) {
			log.warn("Échec publication PRESENCE_QR_EXPIRE_J30 siteId={}: {}", siteId, ex.getMessage());
		}
	}

	/**
	 * Notification in-app (D-P04) sans page de détail (ex. vérification faciale) ; [destinataire] = compte
	 * utilisateur, ou {@link #DESTINATAIRE_RH}.
	 */
	public void notifier(String destinataire, String sujet, String corps) {
		notifier(destinataire, sujet, corps, ObjetNotification.sansObjet("PRESENCE_INFORMATION"));
	}

	/**
	 * Notification portant sur un objet (ticket 2F : pointage manuel, heures sup) sur le canal {@code WEBSOCKET}.
	 */
	public void notifier(String destinataire, String sujet, String corps, ObjetNotification objet) {
		notifierAvecCanal("WEBSOCKET", destinataire, sujet, corps, objet);
	}

	/**
	 * Notification portant sur un objet avec choix du canal ({@code WEBSOCKET} ou {@code BOTH}).
	 */
	public void notifierAvecCanal(String canal, String destinataire, String sujet, String corps, ObjetNotification objet) {
		if (destinataire == null || destinataire.isBlank()) {
			return;
		}
		String canalEffectif = "BOTH".equalsIgnoreCase(canal) ? "BOTH" : "WEBSOCKET";
		// Dans une transaction, l'envoi attend le commit : pas de notification pour une décision annulée.
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					envoyer(canalEffectif, destinataire, sujet, corps, objet);
				}
			});
			return;
		}
		envoyer(canalEffectif, destinataire, sujet, corps, objet);
	}

	private void envoyer(String canal, String destinataire, String sujet, String corps, ObjetNotification objet) {
		try {
			publier(RhPresenceTopics.RH_NOTIFICATIONS, destinataire, message(canal, destinataire, sujet, corps, objet));
		} catch (Exception ex) {
			log.warn("Échec notification présence → {} : {}", destinataire, ex.getMessage());
		}
	}

	private NotificationMessage message(String destinataire, String sujet, String corps, ObjetNotification objet) {
		return message("WEBSOCKET", destinataire, sujet, corps, objet);
	}

	private NotificationMessage message(String canal, String destinataire, String sujet, String corps, ObjetNotification objet) {
		return new NotificationMessage(canal, destinataire, sujet, corps,
				objet.notificationId().toString(), objet.evenement(), objet.type(),
				objet.id() != null ? objet.id().toString() : null, objet.numero(), clock.instant().toString());
	}

	private void publier(String topic, String key, Object payload) {
		KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
		if (kafkaTemplate == null) {
			log.warn("KafkaTemplate indisponible : message {} non publié (clé {})", topic, key);
			return;
		}
		kafkaTemplate.send(topic, key, JSON.writeValueAsString(payload));
	}
}
