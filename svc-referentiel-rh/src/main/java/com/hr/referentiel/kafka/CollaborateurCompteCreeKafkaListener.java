package com.hr.referentiel.kafka;

import com.hr.referentiel.repository.CollaborateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class CollaborateurCompteCreeKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(CollaborateurCompteCreeKafkaListener.class);

	private final CollaborateurRepository collaborateurRepository;

	public CollaborateurCompteCreeKafkaListener(CollaborateurRepository collaborateurRepository) {
		this.collaborateurRepository = collaborateurRepository;
	}

	@KafkaListener(
			topics = RhKafkaTopics.COLLABORATEUR_COMPTE_CREE,
			containerFactory = "collaborateurCreeKafkaListenerContainerFactory")
	@Transactional
	public void onCompteCree(CollaborateurCompteCreeEvent event) {
		collaborateurRepository.findById(event.collaborateurIdentifiant()).ifPresentOrElse(c -> {
			if (c.getCompteUtilisateurId() != null
					&& !c.getCompteUtilisateurId().equals(event.compteUtilisateurIdentifiant())) {
				log.warn("Collaborateur {} avait déjà un compte différent — remplacement par {}",
						event.collaborateurIdentifiant(), event.compteUtilisateurIdentifiant());
			}
			c.setCompteUtilisateurId(event.compteUtilisateurIdentifiant());
			collaborateurRepository.save(c);
			log.info("Compte {} lié au collaborateur {}", event.compteUtilisateurIdentifiant(),
					event.collaborateurIdentifiant());
		}, () -> log.error("Collaborateur introuvable pour liaison compte : {}", event.collaborateurIdentifiant()));
	}
}
