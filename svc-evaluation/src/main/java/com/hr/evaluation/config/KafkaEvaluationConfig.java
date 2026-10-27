package com.hr.evaluation.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Producteur Kafka déclaré explicitement : Spring Boot 4 n'auto-configure plus de
 * {@link KafkaTemplate} avec la seule dépendance spring-kafka (SCRUM-31).
 * Les messages sont sérialisés en JSON par {@code EvaluationEventPublisher}, comme dans svc-referentiel-rh.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaEvaluationConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ProducerFactory<String, String> evaluationProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		// Ne pas bloquer une activation de campagne si le broker est indisponible
		config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, String> evaluationKafkaTemplate(ProducerFactory<String, String> evaluationProducerFactory) {
		return new KafkaTemplate<>(evaluationProducerFactory);
	}
}
