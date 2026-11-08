package com.hr.presence.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
		return new PresenceEventPublisher(provider,
				java.time.Clock.fixed(java.time.Instant.parse("2026-11-08T10:15:30Z"), java.time.ZoneOffset.UTC));
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

	@Test
	void notifierAvecObjet_publieLesChampsStructures() throws Exception {
		UUID demande = UUID.randomUUID();
		ObjetNotification objet = ObjetNotification.de(ObjetNotification.POINTAGE_MANUEL, "POINTAGE_MANUEL_VALIDE",
				demande, "PM-202611-0007");

		publisher().notifier("compte-1", "sujet", "corps", objet);

		org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(kafka).send(anyString(), anyString(), json.capture());
		com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.getValue());
		assertThat(n.get("objet_type").asText()).isEqualTo("POINTAGE_MANUEL");
		assertThat(n.get("objet_id").asText()).isEqualTo(demande.toString());
		assertThat(n.get("objet_numero").asText()).isEqualTo("PM-202611-0007");
		assertThat(n.get("evenement").asText()).isEqualTo("POINTAGE_MANUEL_VALIDE");
		assertThat(n.get("notification_id").asText()).isEqualTo(objet.notificationId().toString());
		assertThat(n.get("emis_le").asText()).isEqualTo("2026-11-08T10:15:30Z");
		assertThat(n.get("recipient").asText()).isEqualTo("compte-1");
	}

	@Test
	void notifierSansObjet_aUnIdentifiantEtUneDateMaisPasDObjet() throws Exception {
		publisher().notifier("compte-1", "sujet", "corps");

		org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(kafka).send(anyString(), anyString(), json.capture());
		com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.getValue());
		assertThat(n.has("objet_type")).isFalse();
		assertThat(n.has("objet_id")).isFalse();
		assertThat(n.get("notification_id").asText()).isNotBlank();
		assertThat(n.get("emis_le").asText()).isEqualTo("2026-11-08T10:15:30Z");
	}
}
