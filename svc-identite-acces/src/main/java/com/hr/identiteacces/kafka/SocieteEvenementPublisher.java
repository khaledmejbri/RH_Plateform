package com.hr.identiteacces.kafka;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteKafka;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publie les événements du cycle de vie des sociétés sur {@code rh.societes}
 * après validation de la transaction (D-SOC03.d).
 */
@Component
public class SocieteEvenementPublisher {

	private static final Logger log = LoggerFactory.getLogger(SocieteEvenementPublisher.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
	private final Clock clock;

	@Autowired
	public SocieteEvenementPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
		this(kafkaTemplateProvider, Clock.systemUTC());
	}

	public SocieteEvenementPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, Clock clock) {
		this.kafkaTemplateProvider = kafkaTemplateProvider;
		this.clock = clock;
	}

	public void publierApresCommit(String typeEvenement, UUID societeId, String code) {
		SocieteEvenementMessage message = new SocieteEvenementMessage(
				UUID.randomUUID(),
				typeEvenement,
				societeId,
				code,
				clock.instant()
		);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					envoyer(message);
				}
			});
			return;
		}
		envoyer(message);
	}

	void envoyer(SocieteEvenementMessage message) {
		KafkaTemplate<String, String> template = kafkaTemplateProvider.getIfAvailable();
		if (template == null) {
			log.warn("KafkaTemplate indisponible : événement {} pour société {} ({}) non publié",
					message.evenement(), message.code(), message.societeId());
			return;
		}
		try {
			String payload = JSON.writeValueAsString(message);
			SocieteContexte.executer(message.societeId(), () -> {
				ProducerRecord<String, String> record = new ProducerRecord<>(
						RhKafkaTopics.SOCIETES,
						message.societeId().toString(),
						payload
				);
				SocieteKafka.ajouterEntete(record);
				try {
					template.send(record).get(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Envoi Kafka interrompu", e);
				} catch (Exception e) {
					throw new IllegalStateException("Échec envoi Kafka", e);
				}
			});
		} catch (Exception e) {
			log.error("Échec de publication de l'événement {} sur {} pour la société {} ({})",
					message.evenement(), RhKafkaTopics.SOCIETES, message.code(), message.societeId(), e);
		}
	}
}
