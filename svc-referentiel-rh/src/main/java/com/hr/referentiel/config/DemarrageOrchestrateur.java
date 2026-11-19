package com.hr.referentiel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrateur de démarrage (#6) :
 * s'exécute après la reprise et tous les amorçages ({@link Ordered#LOWEST_PRECEDENCE})
 * pour débloquer le trafic HTTP applicatif et démarrer les listeners Kafka ({@code auto-startup=false}).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemarrageOrchestrateur implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemarrageOrchestrateur.class);

	private final ObjectProvider<KafkaListenerEndpointRegistry> kafkaRegistryProvider;
	private final AtomicBoolean pret = new AtomicBoolean(false);

	public DemarrageOrchestrateur(ObjectProvider<KafkaListenerEndpointRegistry> kafkaRegistryProvider) {
		this.kafkaRegistryProvider = kafkaRegistryProvider;
	}

	@Override
	public void run(ApplicationArguments args) {
		pret.set(true);
		KafkaListenerEndpointRegistry registry = kafkaRegistryProvider.getIfAvailable();
		if (registry != null && !registry.isRunning()) {
			registry.start();
			log.info("Listeners Kafka démarrés après reprise et amorçages.");
		}
		log.info("svc-referentiel-rh prêt à recevoir le trafic HTTP.");
	}

	public boolean estPret() {
		return pret.get();
	}

	public OncePerRequestFilter filtreDisponibiliteHttp() {
		return new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(
					HttpServletRequest request,
					HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				String uri = request.getRequestURI();
				if (pret.get() || (uri != null && uri.startsWith("/actuator/health"))) {
					filterChain.doFilter(request, response);
					return;
				}
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding(StandardCharsets.UTF_8.name());
				response.getWriter().write(
						"{\"code\":\"SERVICE_EN_DEMARRAGE\",\"message\":\"Service en cours d'initialisation.\"}");
			}
		};
	}
}
