package com.hr.referentiel.importation;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Purge des imports (ADR § 6, D-SOC19) : le contenu extrait disparaît 30 minutes (réglable) après la dernière action,
 * confirmées comprises ; une analyse non confirmée disparaît 24 h après son expiration. Boucle par société active sous
 * {@link VerrouPlanificateur}.
 */
@Component
public class ImportPurgeScheduler {

	private static final Logger log = LoggerFactory.getLogger(ImportPurgeScheduler.class);

	private final JdbcTemplate jdbc;
	private final ImportService importService;
	private final Clock clock;
	private final VerrouPlanificateur verrouPlanificateur;
	private final SocietesActives societesActives;

	@Autowired
	public ImportPurgeScheduler(
			JdbcTemplate jdbc,
			ImportService importService,
			Clock clock,
			VerrouPlanificateur verrouPlanificateur,
			SocietesActives societesActives) {
		this.jdbc = jdbc;
		this.importService = importService;
		this.clock = clock;
		this.verrouPlanificateur = verrouPlanificateur;
		this.societesActives = societesActives;
	}

	public ImportPurgeScheduler(JdbcTemplate jdbc, ImportService importService, Clock clock) {
		this(jdbc, importService, clock, null, List::of);
	}

	@Scheduled(cron = "${referentiel.imports.purge.cron:0 */5 * * * *}", zone = "Africa/Tunis")
	public void purgerPlanifie() {
		if (verrouPlanificateur == null) {
			purgerSilencieux();
			return;
		}
		verrouPlanificateur.executerSiLibre("referentiel:purge-imports", () -> {
			List<UUID> actives;
			try {
				actives = societesActives.lister();
			} catch (RuntimeException e) {
				log.error("Purge des imports : impossible de lister les sociétés actives : {}", e.getMessage());
				return;
			}
			for (UUID societeId : actives) {
				try {
					SocieteContexte.executer(societeId, this::purger);
				} catch (RuntimeException e) {
					log.error("Purge des imports en échec pour société {} : {}", societeId, e.getMessage());
				}
			}
		});
	}

	private void purgerSilencieux() {
		try {
			purger();
		} catch (RuntimeException e) {
			log.error("Purge des imports en échec : {}", e.getMessage());
		}
	}

	@Transactional
	public int purger() {
		UUID societeId = SocieteContexte.courante();
		Instant limiteContenu = clock.instant().minus(Duration.ofMinutes(importService.expirationMinutes()));
		Instant limiteAnalyse = limiteContenu.minus(Duration.ofHours(ImportService.ECHEANCE_ANALYSE_NON_CONFIRMEE_HEURES));
		int contenus = jdbc.update("delete from rh_import_contenu where societe_id = ? and import_id in "
				+ "(select id from rh_import where societe_id = ? and derniere_action_le < ?)",
				societeId, societeId, Timestamp.from(limiteContenu));
		int analyses = jdbc.update("delete from rh_import where societe_id = ? and statut = 'ANALYSEE' and derniere_action_le < ?",
				societeId, Timestamp.from(limiteAnalyse));
		if (contenus + analyses > 0) {
			log.info("Purge des imports (société {}) : {} contenu(s) extrait(s), {} analyse(s) non confirmée(s) supprimé(s)",
					societeId, contenus, analyses);
		}
		return contenus + analyses;
	}
}
