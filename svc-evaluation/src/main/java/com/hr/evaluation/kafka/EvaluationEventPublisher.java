package com.hr.evaluation.kafka;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationRh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class EvaluationEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(EvaluationEventPublisher.class);

	/** Messages envoyés en JSON texte, format lu par svc-notification (comme svc-referentiel-rh). */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

	public EvaluationEventPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
		this.kafkaTemplateProvider = kafkaTemplateProvider;
	}

	public void publierAlerteSiNecessaire(EvaluationRh evaluation) {
		if (evaluation.getCouleurAlerte() == CouleurAlerteEvaluationRh.VERT) {
			return;
		}
		String message = evaluation.getCouleurAlerte() == CouleurAlerteEvaluationRh.ROUGE
				? "Plan d'action obligatoire et escalade DG."
				: "Alerte RH: deux criteres sont sous le seuil.";
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
		String content = "EVALUATION_CAMPAGNE_OUVERTE|campagne=" + campagneId
				+ "|evaluation=" + evaluation.getId()
				+ "|La campagne « " + nom + " » est active. Merci de compléter votre auto-évaluation (générale puis technique).";
		NotificationMessage msg = new NotificationMessage(
				"WEBSOCKET",
				destinataire,
				"Votre évaluation est ouverte",
				content);
		try {
			publier(RhEvaluationTopics.RH_NOTIFICATIONS, destinataire, msg);
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
		String content = "EVALUATION_A_NOTER|evaluation=" + evaluation.getId()
				+ "|" + collaborateur + " a validé son auto-évaluation (« " + campagne + " »). À vous de la noter.";
		NotificationMessage msg = new NotificationMessage(
				"WEBSOCKET",
				destinataire,
				"Évaluation à noter — " + collaborateur,
				content);
		try {
			publier(RhEvaluationTopics.RH_NOTIFICATIONS, destinataire, msg);
		} catch (Exception ex) {
			log.warn("Échec publication notif EVALUATION_A_NOTER pour éval {}: {}",
					evaluation.getId(), ex.getMessage());
		}
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
