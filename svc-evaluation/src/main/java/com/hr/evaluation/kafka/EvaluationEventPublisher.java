package com.hr.evaluation.kafka;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.entity.EvaluationRh;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EvaluationEventPublisher {

	private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

	public EvaluationEventPublisher(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider) {
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

	private void publier(String topic, String key, Object payload) {
		KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
		if (kafkaTemplate != null) {
			kafkaTemplate.send(topic, key, payload);
		}
	}
}
