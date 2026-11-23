package com.hr.presence.service;

import com.hr.commun.planif.VerrouPlanificateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reprise périodique des demandes présence en état {@code A_DEMARRER} (W6).
 * Sélection par lots de 50 sous verrou consultatif global ; les appels HTTP vers {@code svc-workflow} sont toujours
 * exécutés hors transaction.
 */
@Component
public class PresenceWorkflowDemarrageRepriseScheduler {

	private static final Logger log = LoggerFactory.getLogger(PresenceWorkflowDemarrageRepriseScheduler.class);
	private static final int TAILLE_LOT = 50;

	private final PresenceWorkflowDemarrageService demarrageService;
	private final VerrouPlanificateur verrouPlanificateur;

	public PresenceWorkflowDemarrageRepriseScheduler(
			PresenceWorkflowDemarrageService demarrageService,
			VerrouPlanificateur verrouPlanificateur) {
		this.demarrageService = demarrageService;
		this.verrouPlanificateur = verrouPlanificateur;
	}

	@Scheduled(cron = "${presence.workflow.reprise-cron:0/30 * * * * *}")
	public void reprendreDemarrages() {
		if (verrouPlanificateur == null) {
			executerLots();
			return;
		}
		verrouPlanificateur.executerSiLibre("presence:workflow-reprise-demarrage", this::executerLots);
	}

	void executerLots() {
		List<UUID> manuels = demarrageService.selectionnerLotPointagesManuels(TAILLE_LOT);
		for (UUID id : manuels) {
			try {
				demarrageService.demarrerPointageManuel(id);
			} catch (RuntimeException ex) {
				log.warn("Reprise démarrage POINTAGE_MANUEL échouée demandeId={} : {}", id, ex.getMessage());
			}
		}

		List<UUID> heuresSup = demarrageService.selectionnerLotHeuresSup(TAILLE_LOT);
		for (UUID id : heuresSup) {
			try {
				demarrageService.demarrerHeuresSup(id);
			} catch (RuntimeException ex) {
				log.warn("Reprise démarrage HEURES_SUP échouée demandeId={} : {}", id, ex.getMessage());
			}
		}
	}
}
