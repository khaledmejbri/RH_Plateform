package com.hr.referentiel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Exécuteur dédié : l'envoi Kafka après création collaborateur ne doit pas bloquer le thread HTTP.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class CollaborateurCompteKafkaExecutorConfig {

	@Bean(name = "collaborateurCompteKafkaExecutor")
	public Executor collaborateurCompteKafkaExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		ex.setCorePoolSize(2);
		ex.setMaxPoolSize(8);
		ex.setQueueCapacity(256);
		ex.setThreadNamePrefix("rh-kafka-compte-");
		ex.setDaemon(true);
		ex.initialize();
		return ex;
	}
}
