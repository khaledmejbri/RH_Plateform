package com.hr.referentiel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests de l'ordonnancement de démarrage (#6) : HTTP bloqué avant reprise/amorçage, Kafka auto-startup=false. */
class DemarrageOrchestrateurTest {

	@Test
	@DisplayName("Bloque le trafic HTTP applicatif (503) avant run(), puis débloque et démarre Kafka")
	void bloqueHttpAvantRunPuisDemarreKafka() throws Exception {
		KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
		when(registry.isRunning()).thenReturn(false);
		@SuppressWarnings("unchecked")
		ObjectProvider<KafkaListenerEndpointRegistry> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(registry);

		DemarrageOrchestrateur orchestrateur = new DemarrageOrchestrateur(provider);
		OncePerRequestFilter filtre = orchestrateur.filtreDisponibiliteHttp();

		// Avant run() : /api/... -> 503 SERVICE_EN_DEMARRAGE
		MockHttpServletResponse repApiAvant = new MockHttpServletResponse();
		MockFilterChain chainAvant = new MockFilterChain();
		filtre.doFilter(new MockHttpServletRequest("GET", "/api/rh/v1/admin/fonctions"), repApiAvant, chainAvant);
		assertThat(repApiAvant.getStatus()).isEqualTo(503);
		assertThat(repApiAvant.getContentAsString()).contains("SERVICE_EN_DEMARRAGE");

		// /actuator/health passe même avant run()
		MockHttpServletResponse repHealth = new MockHttpServletResponse();
		MockFilterChain chainHealth = new MockFilterChain();
		filtre.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), repHealth, chainHealth);
		assertThat(repHealth.getStatus()).isEqualTo(200);

		// run() : débloque HTTP et démarre les listeners Kafka
		orchestrateur.run(new DefaultApplicationArguments());
		assertThat(orchestrateur.estPret()).isTrue();
		verify(registry).start();

		MockHttpServletResponse repApiApres = new MockHttpServletResponse();
		MockFilterChain chainApres = new MockFilterChain();
		filtre.doFilter(new MockHttpServletRequest("GET", "/api/rh/v1/admin/fonctions"), repApiApres, chainApres);
		assertThat(repApiApres.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("application.properties désactive l'auto-démarrage des listeners Kafka")
	void kafkaAutoStartupFalseDansApplicationProperties() throws Exception {
		String contenu = Files.readString(Path.of("src/main/resources/application.properties"));
		assertThat(contenu).contains("spring.kafka.listener.auto-startup=false");
	}
}
