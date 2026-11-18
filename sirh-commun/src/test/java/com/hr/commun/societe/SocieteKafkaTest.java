package com.hr.commun.societe;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocieteKafkaTest {

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("ajouterEntete et lireEntete propagent societe_id en texte UTF-8")
	void ajouterEtLireEntete() {
		UUID societeId = UUID.randomUUID();
		SocieteContexte.definir(societeId);

		ProducerRecord<String, String> producerRecord = new ProducerRecord<>("rh.societes", "cle", "{}");
		SocieteKafka.ajouterEntete(producerRecord);

		ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("rh.societes", 0, 0L, "cle", "{}");
		producerRecord.headers().forEach(h -> consumerRecord.headers().add(h));

		assertThat(SocieteKafka.lireEntete(consumerRecord)).contains(societeId);
	}

	@Test
	@DisplayName("lireEntete renvoie Optional.empty() si absent ou invalide ; ajouterEntete lève SocieteAbsenteException hors contexte")
	void casLimites() {
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>("rh.societes", "cle", "{}");
		assertThatThrownBy(() -> SocieteKafka.ajouterEntete(producerRecord))
				.isInstanceOf(SocieteAbsenteException.class);

		ConsumerRecord<String, String> sansEntete = new ConsumerRecord<>("rh.societes", 0, 0L, "cle", "{}");
		assertThat(SocieteKafka.lireEntete(sansEntete)).isEmpty();

		ConsumerRecord<String, String> invalide = new ConsumerRecord<>("rh.societes", 0, 0L, "cle", "{}");
		invalide.headers().add(SocieteKafka.ENTETE_SOCIETE_ID, "non-uuid".getBytes(StandardCharsets.UTF_8));
		assertThat(SocieteKafka.lireEntete(invalide)).isEmpty();
	}
}
