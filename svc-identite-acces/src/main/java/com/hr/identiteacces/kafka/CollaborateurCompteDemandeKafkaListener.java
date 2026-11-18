package com.hr.identiteacces.kafka;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteKafka;
import com.hr.identiteacces.service.UserProvisioningService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class CollaborateurCompteDemandeKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(CollaborateurCompteDemandeKafkaListener.class);

	private final UserProvisioningService userProvisioningService;

	public CollaborateurCompteDemandeKafkaListener(UserProvisioningService userProvisioningService) {
		this.userProvisioningService = userProvisioningService;
	}

	@KafkaListener(
			topics = RhKafkaTopics.COLLABORATEUR_COMPTE_DEMANDE,
			containerFactory = "collaborateurDemandeKafkaListenerContainerFactory")
	public void onDemande(ConsumerRecord<String, CollaborateurCompteDemandeEvent> record) {
		CollaborateurCompteDemandeEvent event = record.value();
		Optional<UUID> societeId = SocieteKafka.lireEntete(record);
		try {
			if (societeId.isPresent()) {
				SocieteContexte.executer(societeId.get(),
						() -> userProvisioningService.provisionCollaborateurCompte(event));
			} else {
				userProvisioningService.provisionCollaborateurCompte(event);
			}
		} catch (Exception e) {
			log.error("Échec provisioning compte collaborateur {}", event != null ? event.collaborateurIdentifiant() : null, e);
			throw e;
		}
	}
}
