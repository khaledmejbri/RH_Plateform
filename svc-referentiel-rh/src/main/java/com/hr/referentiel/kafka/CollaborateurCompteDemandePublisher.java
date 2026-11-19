package com.hr.referentiel.kafka;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteKafka;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Publie la demande de compte sur Kafka hors du thread de la requête HTTP (après commit transactionnel).
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class CollaborateurCompteDemandePublisher {

	private static final Logger log = LoggerFactory.getLogger(CollaborateurCompteDemandePublisher.class);

	private final KafkaTemplate<String, CollaborateurCompteDemandeEvent> kafkaTemplate;
	private final Executor executor;

	public CollaborateurCompteDemandePublisher(
			KafkaTemplate<String, CollaborateurCompteDemandeEvent> kafkaTemplate,
			@Qualifier("collaborateurCompteKafkaExecutor") Executor executor) {
		this.kafkaTemplate = kafkaTemplate;
		this.executor = executor;
	}

	public void publishAsyncAfterCommit(String key, CollaborateurCompteDemandeEvent event) {
		UUID societeId = SocieteContexte.courante();
		executor.execute(() -> {
			try {
				ProducerRecord<String, CollaborateurCompteDemandeEvent> record =
						new ProducerRecord<>(RhKafkaTopics.COLLABORATEUR_COMPTE_DEMANDE, key, event);
				SocieteKafka.ajouterEntete(record, societeId);
				kafkaTemplate.send(record)
						.whenComplete((result, ex) -> {
							if (ex != null) {
								log.error("Echec envoi Kafka collaborateur-compte.demande cle={}", key, ex);
							} else {
								log.info("Demande compte Kafka envoyee collaborateur={} societe={}", key, societeId);
							}
						});
			} catch (Exception e) {
				log.error("Erreur publication Kafka demande compte cle={}", key, e);
			}
		});
	}
}
