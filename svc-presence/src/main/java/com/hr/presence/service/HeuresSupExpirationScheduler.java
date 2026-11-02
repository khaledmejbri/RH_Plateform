package com.hr.presence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Passe en EXPIREE les demandes d'heures sup encore EN_ATTENTE à l'ouverture de la fenêtre de leur premier jour
 * (D-P13.d). Une transaction par demande ; l'expiration paresseuse des décisions rend la fréquence non critique.
 */
@Component
public class HeuresSupExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(HeuresSupExpirationScheduler.class);

	private final HeuresSupService service;

	public HeuresSupExpirationScheduler(HeuresSupService service) {
		this.service = service;
	}

	@Scheduled(cron = "${presence.heures-sup.expiration-cron:0 */5 * * * *}", zone = "Africa/Tunis")
	public void expirerLesDemandesEchues() {
		List<UUID> ids = service.idsAExpirer();
		for (UUID id : ids) {
			try {
				service.expirer(id);
			} catch (RuntimeException ex) {
				// Décision simultanée (@Version) ou incident ponctuel : la prochaine exécution reprend.
				log.warn("Expiration de la demande d'heures sup {} reportée : {}", id, ex.getMessage());
			}
		}
		if (!ids.isEmpty()) {
			log.info("{} demande(s) d'heures sup examinée(s) pour expiration", ids.size());
		}
	}
}
