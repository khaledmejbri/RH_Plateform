package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.entity.WfOutbox;
import com.hr.workflow.repository.WfOutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelaisTest {

	@Mock private WfOutboxRepository outboxRepository;
	@Mock private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
	@Mock private KafkaTemplate<String, String> kafkaTemplate;
	@Mock private VerrouPlanificateur verrouPlanificateur;
	@Mock private PlatformTransactionManager txManager;

	private OutboxRelais relais;
	private final Instant t0 = Instant.parse("2026-11-20T12:00:00Z");

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		lenient().when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
		relais = new OutboxRelais(outboxRepository, kafkaTemplateProvider, verrouPlanificateur, txManager,
				Clock.fixed(t0, ZoneOffset.UTC));
	}

	@Test
	void relayerLot_publieEtMarquePublieLe() {
		WfOutbox msg = new WfOutbox();
		msg.setId(UUID.randomUUID());
		msg.setSujet("rh.workflow.evenements");
		msg.setCle("inst-1");
		msg.setEntetes(Map.of());
		msg.setCharge("{\"evenement\":\"INSTANCE_DEMARREE\"}");
		msg.setTentatives(0);

		when(outboxRepository.verrouillerLotAPublier(eq(t0), anyInt())).thenReturn(List.of(msg));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

		int ok = relais.relayerLot();
		assertThat(ok).isEqualTo(1);
		assertThat(msg.getPublieLe()).isEqualTo(t0);
		assertThat(msg.getDerniereErreur()).isNull();
		verify(outboxRepository).save(msg);
	}

	@Test
	void relayerLot_echecKafka_incrementeTentativesEtPlanifieBackoff() {
		WfOutbox msg = new WfOutbox();
		msg.setId(UUID.randomUUID());
		msg.setSujet("rh.notifications");
		msg.setCle("compte-1");
		msg.setCharge("{}");
		msg.setTentatives(0);

		when(outboxRepository.verrouillerLotAPublier(eq(t0), anyInt())).thenReturn(List.of(msg));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("Broker indisponible")));

		int ok = relais.relayerLot();
		assertThat(ok).isEqualTo(0);
		assertThat(msg.getPublieLe()).isNull();
		assertThat(msg.getTentatives()).isEqualTo(1);
		assertThat(msg.getProchaineTentativeLe()).isEqualTo(t0.plusSeconds(1));
		assertThat(msg.getDerniereErreur()).contains("Broker indisponible");
	}

	@Test
	void executer_utiliseVerrouPlanificateur() {
		relais.executer();
		verify(verrouPlanificateur).executerSiLibre(eq("wf-outbox-relais"), any());
	}
}
