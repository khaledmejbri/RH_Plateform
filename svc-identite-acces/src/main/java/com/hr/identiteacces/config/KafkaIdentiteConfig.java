package com.hr.identiteacces.config;

import com.hr.identiteacces.kafka.CollaborateurCompteCreeEvent;
import com.hr.identiteacces.kafka.CollaborateurCompteDemandeEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaIdentiteConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ProducerFactory<String, CollaborateurCompteCreeEvent> collaborateurCreeProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
		config.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, CollaborateurCompteCreeEvent> collaborateurCompteCreeKafkaTemplate(
			ProducerFactory<String, CollaborateurCompteCreeEvent> f) {
		return new KafkaTemplate<>(f);
	}

	@Bean
	public ProducerFactory<String, String> stringProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> f) {
		return new KafkaTemplate<>(f);
	}

	@Bean
	public ConsumerFactory<String, CollaborateurCompteDemandeEvent> collaborateurDemandeConsumerFactory(
			@Value("${spring.application.name:svc-identite-acces}") String appName) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ConsumerConfig.GROUP_ID_CONFIG, appName + "-compte-demande");
		JacksonJsonDeserializer<CollaborateurCompteDemandeEvent> deser =
				new JacksonJsonDeserializer<>(CollaborateurCompteDemandeEvent.class, false);
		deser.addTrustedPackages("*");
		return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deser);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, CollaborateurCompteDemandeEvent> collaborateurDemandeKafkaListenerContainerFactory(
			ConsumerFactory<String, CollaborateurCompteDemandeEvent> cf) {
		ConcurrentKafkaListenerContainerFactory<String, CollaborateurCompteDemandeEvent> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(cf);
		return factory;
	}
}
