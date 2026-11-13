package com.hr.referentiel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclencheurs de l'acquisition (ADR § 5) : passage quotidien (mois courant) et une passe au démarrage, asynchrone, pour
 * rattraper un service arrêté le 1er. Le cron est réglable ({@code referentiel.conges.acquisition.cron}).
 */
@Component
public class AcquisitionCongeScheduler {

	private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

	private final AcquisitionCongeService service;
	private final boolean auDemarrage;

	public AcquisitionCongeScheduler(AcquisitionCongeService service,
			@Value("${referentiel.conges.acquisition.au-demarrage:true}") boolean auDemarrage) {
		this.service = service;
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

	private void executer() {
		try {
			service.traiter(service.moisCourant());
		} catch (RuntimeException e) {
			log.error("Acquisition mensuelle en échec : {}", e.getMessage());
		}
	}
}
