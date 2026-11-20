package com.hr.referentiel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests de l'ordonnancement de démarrage (#6, B2) : HTTP bloqué avant reprise/amorçage, Kafka auto-startup=false. */
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
	@DisplayName("B2 : les deux fabriques KafkaReferentielConfig ont autoStartup=false et les conteneurs ne tournent qu'après run()")
	void fabriquesKafkaAutoStartupFalseEtDemarreesApresReprise() throws Exception {
		KafkaReferentielConfig config = new KafkaReferentielConfig();
		ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

		var collabFactory = config.collaborateurCreeKafkaListenerContainerFactory("svc-referentiel-rh");
		var societesFactory = config.societesKafkaListenerContainerFactory("svc-referentiel-rh");

		assertThat(ReflectionTestUtils.getField(collabFactory, "autoStartup")).isEqualTo(Boolean.FALSE);
		assertThat(ReflectionTestUtils.getField(societesFactory, "autoStartup")).isEqualTo(Boolean.FALSE);

		MessageListenerContainer c1 = collabFactory.createContainer("rh.collaborateur-compte.cree");
		MessageListenerContainer c2 = societesFactory.createContainer("rh.societes");
		assertThat(c1.isAutoStartup()).isFalse();
		assertThat(c2.isAutoStartup()).isFalse();

		DefaultMessageHandlerMethodFactory handlerFactory = new DefaultMessageHandlerMethodFactory();
		handlerFactory.afterPropertiesSet();
		var toStringMethod = Object.class.getMethod("toString");

		KafkaListenerEndpointRegistry registry = new KafkaListenerEndpointRegistry();
		MethodKafkaListenerEndpoint<String, Object> ep1 = new MethodKafkaListenerEndpoint<>();
		ep1.setId("ep-collab");
		ep1.setTopics("rh.collaborateur-compte.cree");
		ep1.setGroupId("svc-referentiel-rh");
		ep1.setBean(new Object());
		ep1.setMethod(toStringMethod);
		ep1.setMessageHandlerMethodFactory(handlerFactory);
		MethodKafkaListenerEndpoint<String, String> ep2 = new MethodKafkaListenerEndpoint<>();
		ep2.setId("ep-societes");
		ep2.setTopics("rh.societes");
		ep2.setGroupId("svc-referentiel-rh-societes");
		ep2.setBean(new Object());
		ep2.setMethod(toStringMethod);
		ep2.setMessageHandlerMethodFactory(handlerFactory);

		registry.registerListenerContainer(ep1, collabFactory, false);
		registry.registerListenerContainer(ep2, societesFactory, false);

		// Avant run() : aucun conteneur ne tourne
		assertThat(registry.getListenerContainers()).hasSize(2);
		assertThat(registry.getListenerContainers()).noneMatch(MessageListenerContainer::isRunning);
		assertThat(registry.isRunning()).isFalse();

		@SuppressWarnings("unchecked")
		ObjectProvider<KafkaListenerEndpointRegistry> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(registry);

		DemarrageOrchestrateur orchestrateur = new DemarrageOrchestrateur(provider);
		orchestrateur.run(new DefaultApplicationArguments());

		try {
			// Après run() : tous les conteneurs ont démarré
			assertThat(registry.isRunning()).isTrue();
			assertThat(registry.getListenerContainers()).allMatch(MessageListenerContainer::isRunning);
		} finally {
			registry.stop();
		}
	}
}
