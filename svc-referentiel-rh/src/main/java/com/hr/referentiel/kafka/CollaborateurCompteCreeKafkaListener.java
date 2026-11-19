package com.hr.referentiel.kafka;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteKafka;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.SocieteDefautResolver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class CollaborateurCompteCreeKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(CollaborateurCompteCreeKafkaListener.class);

	private final CollaborateurRepository collaborateurRepository;
	private final SocieteDefautResolver societeDefautResolver;
	private final TransactionTemplate transactionTemplate;

	public CollaborateurCompteCreeKafkaListener(
			CollaborateurRepository collaborateurRepository,
			SocieteDefautResolver societeDefautResolver,
			TransactionTemplate transactionTemplate) {
		this.collaborateurRepository = collaborateurRepository;
		this.societeDefautResolver = societeDefautResolver;
		this.transactionTemplate = transactionTemplate;
	}

	@KafkaListener(
			topics = RhKafkaTopics.COLLABORATEUR_COMPTE_CREE,
			containerFactory = "collaborateurCreeKafkaListenerContainerFactory")
	public void onCompteCree(ConsumerRecord<String, CollaborateurCompteCreeEvent> record) {
		CollaborateurCompteCreeEvent event = record.value();
		UUID societeId = SocieteKafka.lireEntete(record)
				.orElseGet(() -> societeDefautResolver
						.resoudrePourTransitionAvecWarn("Message Kafka collaborateur-compte.cree")
						.orElseThrow(() -> new IllegalStateException(
								"Message Kafka collaborateur-compte.cree sans en-tête societe_id et repli désactivé")));

		SocieteContexte.executer(societeId, () -> transactionTemplate.executeWithoutResult(status -> {
			collaborateurRepository.findById(event.collaborateurIdentifiant()).ifPresentOrElse(c -> {
				if (c.getCompteUtilisateurId() != null
						&& !c.getCompteUtilisateurId().equals(event.compteUtilisateurIdentifiant())) {
					log.warn("Collaborateur {} avait déjà un compte différent — remplacement par {}",
							event.collaborateurIdentifiant(), event.compteUtilisateurIdentifiant());
				}
				c.setCompteUtilisateurId(event.compteUtilisateurIdentifiant());
				collaborateurRepository.save(c);
				log.info("Compte {} lié au collaborateur {} (société {})",
						event.compteUtilisateurIdentifiant(), event.collaborateurIdentifiant(), societeId);
			}, () -> log.error("Collaborateur introuvable pour liaison compte : {} (société {})",
					event.collaborateurIdentifiant(), societeId));
		}));
	}
}
