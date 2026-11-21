package com.hr.referentiel.service;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reprise périodique des demandes M01 en état {@code A_DEMARRER} (D-W5-05).
 * Sélection par lots de 50 sous verrou consultatif global ; les appels HTTP vers {@code svc-workflow} sont toujours
 * exécutés hors transaction.
 */
@Component
public class WorkflowDemarrageRepriseScheduler {

	private static final Logger log = LoggerFactory.getLogger(WorkflowDemarrageRepriseScheduler.class);
	private static final int TAILLE_LOT = 50;

	private final WorkflowDemarrageService demarrageService;
	private final VerrouPlanificateur verrouPlanificateur;
	private final SocietesActives societesActives;

	public WorkflowDemarrageRepriseScheduler(
			WorkflowDemarrageService demarrageService,
			VerrouPlanificateur verrouPlanificateur,
			SocietesActives societesActives) {
		this.demarrageService = demarrageService;
		this.verrouPlanificateur = verrouPlanificateur;
		this.societesActives = societesActives;
	}

	@Scheduled(cron = "${referentiel.workflow.reprise-cron:0/30 * * * * *}")
	public void reprendreDemarrages() {
		if (verrouPlanificateur == null) {
			executerPourToutesSocietes();
			return;
		}
		verrouPlanificateur.executerSiLibre("referentiel:workflow-reprise-demarrage", this::executerPourToutesSocietes);
	}

	void executerPourToutesSocietes() {
		List<UUID> actives;
		try {
			actives = societesActives.lister();
		} catch (RuntimeException e) {
			log.warn("Reprise démarrage workflow : impossible de lister les sociétés actives ({})", e.getMessage());
			return;
		}
		for (UUID societeId : actives) {
			try {
				SocieteContexte.executer(societeId, () -> {
					List<UUID> ids = demarrageService.selectionnerLotADemarrer(TAILLE_LOT);
					for (UUID id : ids) {
						try {
							demarrageService.demarrer(id);
						} catch (RuntimeException ex) {
							log.warn("Reprise démarrage workflow échouée pour demandeId={} : {}", id, ex.getMessage());
						}
					}
				});
			} catch (RuntimeException e) {
				log.warn("Reprise démarrage workflow en échec pour societeId={} : {}", societeId, e.getMessage());
			}
		}
	}
}
