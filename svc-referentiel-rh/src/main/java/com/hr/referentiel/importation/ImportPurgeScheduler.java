package com.hr.referentiel.importation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Purge des imports (ADR § 6) : le contenu extrait disparaît 30 minutes (réglable) après la dernière action, confirmées
 * comprises ; une analyse non confirmée disparaît 24 h après son expiration (ensuite 404). L'historique confirmé et les
 * lignes d'import sont gardés 5 ans (purge à prévoir, hors lot). Le fichier brut n'existe jamais.
 */
@Component
public class ImportPurgeScheduler {

	private static final Logger log = LoggerFactory.getLogger(ImportPurgeScheduler.class);

	private final JdbcTemplate jdbc;
	private final ImportService importService;
	private final Clock clock;

	public ImportPurgeScheduler(JdbcTemplate jdbc, ImportService importService, Clock clock) {
		this.jdbc = jdbc;
		this.importService = importService;
		this.clock = clock;
	}

	@Scheduled(cron = "${referentiel.imports.purge.cron:0 */5 * * * *}", zone = "Africa/Tunis")
	public void purgerPlanifie() {
		try {
			purger();
		} catch (RuntimeException e) {
			log.error("Purge des imports en échec : {}", e.getMessage());
		}
	}

	@Transactional
	public int purger() {
		Instant limiteContenu = clock.instant().minus(Duration.ofMinutes(importService.expirationMinutes()));
		Instant limiteAnalyse = limiteContenu.minus(Duration.ofHours(ImportService.ECHEANCE_ANALYSE_NON_CONFIRMEE_HEURES));
		int contenus = jdbc.update("delete from rh_import_contenu where import_id in "
				+ "(select id from rh_import where derniere_action_le < ?)", Timestamp.from(limiteContenu));
		int analyses = jdbc.update("delete from rh_import where statut = 'ANALYSEE' and derniere_action_le < ?",
				Timestamp.from(limiteAnalyse));
		if (contenus + analyses > 0) {
			log.info("Purge des imports : {} contenu(s) extrait(s), {} analyse(s) non confirmée(s) supprimé(s)", contenus, analyses);
		}
		return contenus + analyses;
	}
}
