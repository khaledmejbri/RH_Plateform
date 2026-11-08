package com.hr.evaluation.kafka;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationRh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class EvaluationEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(EvaluationEventPublisher.class);

	/** Messages envoyés en JSON texte, format lu par svc-notification (comme svc-referentiel-rh). */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

	private final Clock clock;

	@Autowired
	public EvaluationEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
		this(kafkaTemplateProvider, Clock.systemUTC());
	}

	/** Horloge injectable pour les tests ({@code emis_le}). */
	EvaluationEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, Clock clock) {
		this.kafkaTemplateProvider = kafkaTemplateProvider;
		this.clock = clock;
	}

	public void publierAlerteSiNecessaire(EvaluationRh evaluation) {
		if (evaluation.getCouleurAlerte() == CouleurAlerteEvaluationRh.VERT) {
			return;
		}
		String message = evaluation.getCouleurAlerte() == CouleurAlerteEvaluationRh.ROUGE
				? "Plan d'action obligatoire et escalade DG."
				: "Alerte RH : deux critères sont sous le seuil.";
		publier(RhEvaluationTopics.EVALUATION_ALERTE, evaluation.getId().toString(),
				new EvaluationAlerteEvent(
						evaluation.getId(),
						evaluation.getCollaborateurIdentifiant(),
						evaluation.getSuperieurIdentifiant(),
						evaluation.getType(),
						evaluation.getAnnee(),
						evaluation.getCouleurAlerte(),
						message,
						Instant.now()));
	}

	public void publierFormationRecommandee(EvaluationRh evaluation, String formation) {
		publier(RhEvaluationTopics.FORMATION_RECOMMANDEE, evaluation.getId().toString(),
				new FormationRecommandeeEvent(
						evaluation.getId(),
						evaluation.getCollaborateurIdentifiant(),
						evaluation.getSuperieurIdentifiant(),
						evaluation.getAnnee(),
						formation,
						"EVALUATION_ANNUELLE",
						Instant.now()));
	}

	/**
	 * E5 — notif in-app ouverture campagne (topic {@code rh.notifications}, pas alerte couleur).
	 */
	public void publierEvaluationCampagneOuverte(Evaluation evaluation, String campagneNom) {
		String destinataire = adresse(evaluation.getCollaborateurCompteId(), evaluation.getCollaborateurIdentifiant());
		UUID campagneId = evaluation.getCampaign() != null ? evaluation.getCampaign().getId() : null;
		String nom = campagneNom != null ? campagneNom : "campagne";
		String content = "La campagne « " + nom + " » est active. Merci de compléter votre auto-évaluation (générale puis technique).";
		ObjetNotification objet = ObjetNotification.de(ObjetNotification.EVALUATION,
				"EVALUATION_CAMPAGNE_OUVERTE", evaluation.getId(), null);
		try {
			publier(RhEvaluationTopics.RH_NOTIFICATIONS, destinataire,
					message(destinataire, "Votre évaluation est ouverte", content, objet));
		} catch (Exception ex) {
			log.warn("Échec publication notif EVALUATION_CAMPAGNE_OUVERTE pour éval {}: {}",
					evaluation.getId(), ex.getMessage());
		}
	}

	/**
	 * Le collaborateur a validé son auto-évaluation : le responsable hiérarchique doit la noter (§ 12).
	 */
	public void publierValidationCollaborateur(Evaluation evaluation) {
		String destinataire = adresse(evaluation.getSuperieurCompteId(), evaluation.getSuperieurIdentifiant());
		String collaborateur = evaluation.getCollaborateurNom() != null
				? evaluation.getCollaborateurNom()
				: "Un collaborateur";
		String campagne = evaluation.getCampaign() != null ? evaluation.getCampaign().getNom() : "la campagne";
		String content = collaborateur + " a validé son auto-évaluation (« " + campagne + " »). À vous de la noter.";
		ObjetNotification objet = ObjetNotification.de(ObjetNotification.EVALUATION,
				"EVALUATION_A_NOTER", evaluation.getId(), null);
		try {
			publier(RhEvaluationTopics.RH_NOTIFICATIONS, destinataire,
					message(destinataire, "Évaluation à noter — " + collaborateur, content, objet));
		} catch (Exception ex) {
			log.warn("Échec publication notif EVALUATION_A_NOTER pour éval {}: {}",
					evaluation.getId(), ex.getMessage());
		}
	}

	/** Destinataire spécial : diffusion à tous les comptes RH (svc-notification). */
	public static final String DESTINATAIRE_RH = "RH";

	/**
	 * Notification in-app sur un objet (ticket 2F), ex. alerte d'écart ou campagne non lancée (SCRUM-42). L'objet
	 * porte l'identifiant d'événement : à réutiliser pour tous les destinataires du même événement.
	 */
	public void notifier(String destinataire, String sujet, String corps, ObjetNotification objet) {
		try {
			publier(RhEvaluationTopics.RH_NOTIFICATIONS, destinataire, message(destinataire, sujet, corps, objet));
		} catch (Exception ex) {
			log.warn("Échec notification évaluation → {} : {}", destinataire, ex.getMessage());
		}
	}

	private NotificationMessage message(String destinataire, String sujet, String corps, ObjetNotification objet) {
		return new NotificationMessage("WEBSOCKET", destinataire, sujet, corps,
				objet.notificationId().toString(), objet.evenement(), objet.type(),
				objet.id() != null ? objet.id().toString() : null, objet.numero(), clock.instant().toString());
	}

	/**
	 * svc-notification route sur le claim {@code identifiant_utilisateur} (compte, SCRUM-27) ;
	 * repli sur l'identifiant de fiche pour les évaluations créées sans snapshot du compte.
	 */
	static String adresse(UUID compteId, UUID ficheId) {
		return compteId != null ? compteId.toString() : ficheId.toString();
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
