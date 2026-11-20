package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.entity.WfOutbox;
import com.hr.workflow.repository.WfOutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Relais outbox → Kafka (§ 9.6.6, D-W3-01) :
 * lots de 200 sous {@code VerrouPlanificateur} et {@code FOR UPDATE SKIP LOCKED},
 * attente progressive (1 s, 5 s, 30 s, 2 min, 10 min), alerte ERROR au-delà de 50 tentatives.
 */
@Component
public class OutboxRelais {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelais.class);
	private static final int TAILLE_LOT = 200;
	private static final int SEUIL_ERREUR_TENTATIVES = 50;

	private final WfOutboxRepository outboxRepository;
	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
	private final VerrouPlanificateur verrouPlanificateur;
	private final TransactionTemplate txTemplate;
	private final Clock clock;

	public OutboxRelais(
			WfOutboxRepository outboxRepository,
			ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
			VerrouPlanificateur verrouPlanificateur,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplateProvider = kafkaTemplateProvider;
		this.verrouPlanificateur = verrouPlanificateur;
		this.txTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${workflow.outbox.delai-ms:2000}")
	public void executer() {
		verrouPlanificateur.executerSiLibre("wf-outbox-relais", this::relayerLot);
	}

	/** Exécute un lot de publication (visible pour les tests). */
	public int relayerLot() {
		KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
		if (kafkaTemplate == null) {
			return 0;
		}
		Integer publies = txTemplate.execute(status -> {
			Instant maintenant = clock.instant();
			List<WfOutbox> lot = outboxRepository.verrouillerLotAPublier(maintenant, TAILLE_LOT);
			int ok = 0;
			for (WfOutbox msg : lot) {
				try {
					ProducerRecord<String, String> record = new ProducerRecord<>(msg.getSujet(), msg.getCle(), msg.getCharge());
					if (msg.getEntetes() != null) {
						for (Map.Entry<String, String> e : msg.getEntetes().entrySet()) {
							if (e.getKey() != null && e.getValue() != null) {
								record.headers().add(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
							}
						}
					}
					kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
					msg.setPublieLe(clock.instant());
					msg.setDerniereErreur(null);
					ok++;
				} catch (Exception ex) {
					if (ex instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					int tentatives = msg.getTentatives() + 1;
					msg.setTentatives(tentatives);
					msg.setProchaineTentativeLe(clock.instant().plus(delaiReessai(tentatives)));
					String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
					msg.setDerniereErreur(err.length() > 500 ? err.substring(0, 500) : err);
					if (tentatives > SEUIL_ERREUR_TENTATIVES) {
						log.error("Outbox {} en échec après {} tentatives sur {}", msg.getId(), tentatives, msg.getSujet());
					} else {
						log.warn("Échec publication outbox {} (tentative {}) sur {}", msg.getId(), tentatives, msg.getSujet());
					}
				}
				outboxRepository.save(msg);
			}
			return ok;
		});
		return publies != null ? publies : 0;
	}

	static Duration delaiReessai(int tentatives) {
		return switch (tentatives) {
			case 1 -> Duration.ofSeconds(1);
			case 2 -> Duration.ofSeconds(5);
			case 3 -> Duration.ofSeconds(30);
			case 4 -> Duration.ofMinutes(2);
			default -> Duration.ofMinutes(10);
		};
	}
}
