package com.hr.workflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Producteur Kafka explicite (Spring Boot 4) et déclaration des sujets du moteur (§ 9.8).
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaWorkflowConfig {

	public static final String SUJET_WORKFLOW_EVENEMENTS = "rh.workflow.evenements";
	public static final String SUJET_NOTIFICATIONS = "rh.notifications";

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ProducerFactory<String, String> workflowProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, String> workflowKafkaTemplate(
			ProducerFactory<String, String> workflowProducerFactory) {
		return new KafkaTemplate<>(workflowProducerFactory);
	}

	@Bean
	public NewTopic topicWorkflowEvenements() {
		return TopicBuilder.name(SUJET_WORKFLOW_EVENEMENTS)
				.partitions(3)
				.replicas(1)
				.build();
	}

	@Bean
	public NewTopic topicNotifications() {
		return TopicBuilder.name(SUJET_NOTIFICATIONS)
				.partitions(1)
				.replicas(1)
				.build();
	}
}
