package com.hr.identiteacces.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.identiteacces.kafka.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Envoie le mot de passe initial par courriel via le microservice de notification.
 */
@Service
public class CollaborateurWelcomeMailService {

	private static final Logger log = LoggerFactory.getLogger(CollaborateurWelcomeMailService.class);

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final Executor mailExecutor;

	@Value("${app.mail.collaborateur-welcome-enabled:false}")
	private boolean welcomeEnabled;

	public CollaborateurWelcomeMailService(
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			Executor mailExecutor) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.mailExecutor = mailExecutor;
	}

	public void scheduleWelcomeEmail(String to, String prenom, String nom, String username, String initialPassword) {
		if (!welcomeEnabled) {
			log.debug("Courriel bienvenue collaborateur désactivé (app.mail.collaborateur-welcome-enabled=false).");
			return;
		}
		if (to == null || to.isBlank()) {
			log.warn("Pas d'adresse courriel : impossible d'envoyer le message de bienvenue.");
			return;
		}
		
		String p = prenom != null ? prenom : "";
		String n = nom != null ? nom : "";
		mailExecutor.execute(() -> sendToKafka(to.trim(), p, n, username, initialPassword));
	}

	private void sendToKafka(String to, String prenom, String nom, String username, String initialPassword) {
		try {
			String body = buildBody(prenom, nom, username, initialPassword);
			NotificationMessage notification = new NotificationMessage("EMAIL", to, "Bienvenue — accès application mobile RH", body);
			String payload = objectMapper.writeValueAsString(notification);

			kafkaTemplate.send("rh.notifications", payload);
			log.info("Message de bienvenue envoyé à Kafka pour notification à {}", to);
		} catch (Exception e) {
			log.error("Échec envoi Kafka pour message de bienvenue à {}", to, e);
		}
	}

	private static String buildBody(String prenom, String nom, String username, String initialPassword) {
		return salutation(prenom, nom)
				+ """
				Bienvenue sur la plateforme RH.

				Voici vos identifiants pour accéder à l'application mobile :

				  Nom d'utilisateur : %s
				  Mot de passe provisoire : %s

				Nous vous invitons à vous connecter et à modifier votre mot de passe lors de la première utilisation.

				Cordialement,
				L'équipe RH
				""".formatted(username, initialPassword);
	}

	private static String salutation(String prenom, String nom) {
		if ((prenom == null || prenom.isBlank()) && (nom == null || nom.isBlank())) {
			return "Bonjour,\n\n";
		}
		StringBuilder b = new StringBuilder("Bonjour");
		if (prenom != null && !prenom.isBlank()) {
			b.append(' ').append(prenom.trim());
		}
		if (nom != null && !nom.isBlank()) {
			b.append(' ').append(nom.trim());
		}
		b.append(",\n\n");
		return b.toString();
	}
}
