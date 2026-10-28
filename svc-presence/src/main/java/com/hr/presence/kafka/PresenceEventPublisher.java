package com.hr.presence.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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

	public PresenceEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
		this.kafkaTemplateProvider = kafkaTemplateProvider;
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
		String content = "PRESENCE_QR_EXPIRE_J30"
				+ "|eventId=" + eventId
				+ "|siteId=" + siteId
				+ "|libelle=" + libelle
				+ "|validUntil=" + validUntil
				+ "|joursRestants=" + joursRestants
				+ "|qrVersion=" + qrVersion
				+ "|Le QR du site « " + libelle + " » expire dans " + joursRestants + " jour(s).";
		NotificationMessage msg = new NotificationMessage(
				"WEBSOCKET",
				"RH",
				"QR de pointage bientôt expiré",
				content);
		try {
			publier(RhPresenceTopics.RH_NOTIFICATIONS, siteId + ":" + qrVersion, msg);
		} catch (Exception ex) {
			log.warn("Échec publication PRESENCE_QR_EXPIRE_J30 siteId={}: {}", siteId, ex.getMessage());
		}
	}

	/**
	 * Notification in-app (D-P04) ; [destinataire] = compte utilisateur, ou {@link #DESTINATAIRE_RH}.
	 */
	public void notifier(String destinataire, String sujet, String corps) {
		if (destinataire == null || destinataire.isBlank()) {
			return;
		}
		try {
			publier(RhPresenceTopics.RH_NOTIFICATIONS, destinataire,
					new NotificationMessage("WEBSOCKET", destinataire, sujet, corps));
		} catch (Exception ex) {
			log.warn("Échec notification présence → {} : {}", destinataire, ex.getMessage());
		}
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
