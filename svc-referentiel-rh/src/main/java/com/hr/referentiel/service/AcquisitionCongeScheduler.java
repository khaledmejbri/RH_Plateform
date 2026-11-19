package com.hr.referentiel.service;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Déclencheurs de l'acquisition (ADR § 5, D-SOC19) : passage quotidien (mois courant) et une passe au démarrage,
 * asynchrone, sous verrou consultatif global et boucle isolée par société active.
 */
@Component
public class AcquisitionCongeScheduler {

	private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

	private final AcquisitionCongeService service;
	private final VerrouPlanificateur verrouPlanificateur;
	private final SocietesActives societesActives;
	private final boolean auDemarrage;

	public AcquisitionCongeScheduler(
			AcquisitionCongeService service,
			VerrouPlanificateur verrouPlanificateur,
			SocietesActives societesActives,
			@Value("${referentiel.conges.acquisition.au-demarrage:true}") boolean auDemarrage) {
		this.service = service;
		this.verrouPlanificateur = verrouPlanificateur;
		this.societesActives = societesActives;
		this.auDemarrage = auDemarrage;
	}

	@Scheduled(cron = "${referentiel.conges.acquisition.cron:0 15 0 * * *}", zone = "Africa/Tunis")
	public void passageQuotidien() {
		executer();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void passeAuDemarrage() {
		if (auDemarrage) {
			Thread.ofVirtual().name("acquisition-demarrage").start(this::executer);
		}
	}

	void executer() {
		verrouPlanificateur.executerSiLibre("referentiel:acquisition-conge", () -> {
			List<UUID> actives;
			try {
				actives = societesActives.lister();
			} catch (RuntimeException e) {
				log.error("Impossible de lister les sociétés actives pour l'acquisition mensuelle : {}", e.getMessage());
				return;
			}
			for (UUID societeId : actives) {
				try {
					SocieteContexte.executer(societeId, () -> service.traiter(service.moisCourant()));
				} catch (RuntimeException e) {
					log.error("Acquisition mensuelle en échec pour société {} : {}", societeId, e.getMessage());
				}
			}
		});
	}
}
