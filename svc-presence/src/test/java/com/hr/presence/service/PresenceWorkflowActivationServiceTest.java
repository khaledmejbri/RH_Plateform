package com.hr.presence.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.hr.presence.client.PresenceWorkflowMoteurClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceWorkflowActivationServiceTest {

	@Mock
	private PresenceWorkflowMoteurClient client;

	@Test
	void cache30sEtRepliHeriteNonMisEnCache() {
		AtomicLong nanos = new AtomicLong(0);
		Ticker ticker = nanos::get;
		PresenceWorkflowActivationService service = new PresenceWorkflowActivationService(client, ticker);

		// Moteur injoignable -> HERITE non mis en cache
		when(client.modeActivation("POINTAGE_MANUEL")).thenReturn(Optional.empty());
		assertThat(service.estMoteur("POINTAGE_MANUEL")).isFalse();

		when(client.modeActivation("POINTAGE_MANUEL")).thenReturn(Optional.of("MOTEUR"));
		assertThat(service.estMoteur("POINTAGE_MANUEL")).isTrue();
		// Dans les 30 s, le cache répond sans réappeler le client
		nanos.addAndGet(Duration.ofSeconds(20).toNanos());
		assertThat(service.estMoteur("POINTAGE_MANUEL")).isTrue();
		verify(client, times(2)).modeActivation("POINTAGE_MANUEL");

		// Après 30 s, réinterrogation
		nanos.addAndGet(Duration.ofSeconds(15).toNanos());
		when(client.modeActivation("POINTAGE_MANUEL")).thenReturn(Optional.of("HERITE"));
		assertThat(service.estMoteur("POINTAGE_MANUEL")).isFalse();
		verify(client, times(3)).modeActivation("POINTAGE_MANUEL");
	}
}
