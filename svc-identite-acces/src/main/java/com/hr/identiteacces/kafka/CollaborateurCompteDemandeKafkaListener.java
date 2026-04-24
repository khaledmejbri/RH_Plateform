package com.hr.identiteacces.kafka;

import com.hr.identiteacces.service.UserProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
	public void onDemande(CollaborateurCompteDemandeEvent event) {
		try {
			userProvisioningService.provisionCollaborateurCompte(event);
		} catch (Exception e) {
			log.error("Échec provisioning compte collaborateur {}", event.collaborateurIdentifiant(), e);
			throw e;
		}
	}
}
