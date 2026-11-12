package com.hr.presence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Passe en EXPIREE les demandes de pointage manuel encore EN_ATTENTE à leur échéance (D-P23.b). Une transaction par
 * demande, idempotent (une demande déjà décidée ou expirée n'est pas touchée) ; l'expiration paresseuse de la décision
 * rend la fréquence non critique.
 */
@Component
public class DemandeManuelleExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(DemandeManuelleExpirationScheduler.class);

	private final PresenceDemandeManuelleService service;

	public DemandeManuelleExpirationScheduler(PresenceDemandeManuelleService service) {
		this.service = service;
	}

	@Scheduled(cron = "${presence.manuel.expiration-cron:0 */5 * * * *}", zone = "Africa/Tunis")
	public void expirerLesDemandesEchues() {
		List<UUID> ids = service.idsAExpirer();
		int expirees = 0;
		for (UUID id : ids) {
			try {
				if (service.expirer(id)) {
					expirees++;
				}
			} catch (RuntimeException ex) {
				// Décision simultanée (verrou) ou incident ponctuel : la prochaine exécution reprend.
				log.warn("Expiration de la demande de pointage manuel {} reportée : {}", id, ex.getMessage());
			}
		}
		if (expirees > 0) {
			log.info("{} demande(s) de pointage manuel expirée(s)", expirees);
		}
	}
}
