package com.hr.presence.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.util.HashMap;
import java.util.Map;

/**
 * Producteur et consommateur Kafka déclarés explicitement : Spring Boot 4 n'auto-configure plus de
 * {@link KafkaTemplate} ni de fabrique de conteneur avec la seule dépendance spring-kafka.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaPresenceConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ProducerFactory<String, String> presenceProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, String> presenceKafkaTemplate(ProducerFactory<String, String> presenceProducerFactory) {
		return new KafkaTemplate<>(presenceProducerFactory);
	}

	@Bean
	public ConsumerFactory<String, String> workflowEvenementsConsumerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ConsumerConfig.GROUP_ID_CONFIG, "svc-presence-workflow");
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
	}

	/**
	 * Fabrique dédiée à l'écoute des événements workflow ({@code rh.workflow.evenements}),
	 * reprise 1 s, 5 s, 30 s puis {@code rh.workflow.evenements.DLT}.
	 * Démarrage automatique actif (aucun {@code setAutoStartup(false)}).
	 */
	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> workflowEvenementsKafkaListenerContainerFactory(
			KafkaTemplate<String, String> presenceKafkaTemplate) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(workflowEvenementsConsumerFactory());
		var recoverer = new DeadLetterPublishingRecoverer(
				presenceKafkaTemplate,
				(record, ex) -> new TopicPartition("rh.workflow.evenements.DLT", -1));
		BackOff backOff = () -> new BackOffExecution() {
			private final long[] delais = {1_000L, 5_000L, 30_000L};
			private int index = 0;

			@Override
			public long nextBackOff() {
				return index < delais.length ? delais[index++] : STOP;
			}
		};
		factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backOff));
		return factory;
	}
}
