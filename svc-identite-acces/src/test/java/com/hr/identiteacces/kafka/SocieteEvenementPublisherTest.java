package com.hr.identiteacces.kafka;

import com.hr.commun.societe.SocieteKafka;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocieteEvenementPublisherTest {

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("D-SOC03.d : publie sur rh.societes après commit avec clé societe_id, en-tête societe_id et charge JSON snake_case")
	@SuppressWarnings("unchecked")
	void publieApresCommitAvecEnteteEtJsonSnakeCase() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(kafkaTemplate);

		Clock horloge = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
		SocieteEvenementPublisher publisher = new SocieteEvenementPublisher(provider, horloge);

		UUID societeId = UUID.randomUUID();
		TransactionSynchronizationManager.initSynchronization();

		publisher.publierApresCommit(SocieteEvenementMessage.SOCIETE_CREEE, societeId, "RECETTE2");

		// Avant afterCommit : rien n'est encore envoyé
		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

		// Déclenchement afterCommit
		for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
			sync.afterCommit();
		}

		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());

		ProducerRecord<String, String> record = captor.getValue();
		assertThat(record.topic()).isEqualTo(RhKafkaTopics.SOCIETES);
		assertThat(record.key()).isEqualTo(societeId.toString());
		assertThat(record.headers().lastHeader(SocieteKafka.ENTETE_SOCIETE_ID)).isNotNull();
		assertThat(new String(record.headers().lastHeader(SocieteKafka.ENTETE_SOCIETE_ID).value()))
				.isEqualTo(societeId.toString());
		assertThat(record.value())
				.contains("\"evenement_id\"")
				.contains("\"evenement\":\"SOCIETE_CREEE\"")
				.contains("\"societe_id\":\"" + societeId + "\"")
				.contains("\"code\":\"RECETTE2\"")
				.contains("\"emis_le\":\"2026-11-17T10:00:00Z\"");
	}

	@Test
	@DisplayName("D-SOC03.d : échec d'envoi Kafka journalisé en ERROR sans lever d'exception")
	@SuppressWarnings("unchecked")
	void echecEnvoi_neLevePasException() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		CompletableFuture<SendResult<String, String>> enErreur = new CompletableFuture<>();
		enErreur.completeExceptionally(new RuntimeException("Kafka indisponible"));
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(enErreur);

		ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(kafkaTemplate);

		SocieteEvenementPublisher publisher = new SocieteEvenementPublisher(provider, Clock.systemUTC());

		assertThatCode(() -> publisher.publierApresCommit(
				SocieteEvenementMessage.SOCIETE_SUSPENDUE, UUID.randomUUID(), "RECETTE2"))
				.doesNotThrowAnyException();
	}
}
