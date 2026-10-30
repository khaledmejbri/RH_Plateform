package com.hr.presence.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Les notifications partent après le commit ; sans transaction, l'envoi reste immédiat. */
class PresenceEventPublisherTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);

	@AfterEach
	void nettoyer() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private PresenceEventPublisher publisher() {
		when(provider.getIfAvailable()).thenReturn(kafka);
		return new PresenceEventPublisher(provider);
	}

	@Test
	void sansTransaction_envoiImmediat() {
		publisher().notifier("compte-1", "sujet", "corps");

		verify(kafka).send(anyString(), anyString(), anyString());
	}

	@Test
	void dansUneTransaction_envoiSeulementApresLeCommit() {
		TransactionSynchronizationManager.initSynchronization();
		publisher().notifier("compte-1", "sujet", "corps");
		verify(kafka, never()).send(anyString(), anyString(), anyString());

		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

		verify(kafka).send(anyString(), anyString(), anyString());
	}

	@Test
	void transactionAnnulee_aucuneNotification() {
		TransactionSynchronizationManager.initSynchronization();
		publisher().notifier("compte-1", "sujet", "corps");

		TransactionSynchronizationManager.getSynchronizations()
				.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verify(kafka, never()).send(anyString(), anyString(), anyString());
	}
}
