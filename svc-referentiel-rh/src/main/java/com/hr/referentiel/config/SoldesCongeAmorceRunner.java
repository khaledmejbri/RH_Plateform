package com.hr.referentiel.config;

import com.hr.referentiel.service.FormatJours;
import com.hr.referentiel.service.ReglagesCongeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Amorçage idempotent des soldes de congé (ADR § 9) : ligne de réglages {@code rh_parametres} (1,5 jour par mois, début
 * des acquisitions = 1er du mois suivant la mise en service, posé une fois), contrainte {@code acquis >= 0}, et jours
 * fériés civils à date fixe de l'année en cours et de la suivante (une fois par année : une date supprimée n'est jamais
 * recréée). Rejoué au démarrage et chaque jour (l'année suivante est amorcée le 1er janvier). Verrou consultatif
 * PostgreSQL : deux instances ne s'amorcent pas deux fois.
 */
@Component
public class SoldesCongeAmorceRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(SoldesCongeAmorceRunner.class);

	static final long VERROU_AMORCAGE = 7_001_903_002L;
	static final String CONTRAINTE_ACQUIS = "ck_rh_solde_conge_acquis_positif";

	/** Fêtes civiles à date fixe (mois, jour, libellé) ; dates à faire vérifier par le client (D-S15.e, i). */
	static final List<Object[]> FETES_CIVILES = List.of(
			new Object[] {1, 1, "Jour de l'an"},
			new Object[] {3, 20, "Fête de l'Indépendance"},
			new Object[] {4, 9, "Fête des Martyrs"},
			new Object[] {5, 1, "Fête du Travail"},
			new Object[] {7, 25, "Fête de la République"},
			new Object[] {8, 13, "Fête de la Femme"},
			new Object[] {10, 15, "Fête de l'Évacuation"},
			new Object[] {12, 17, "Fête de la Révolution"});

	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	public SoldesCongeAmorceRunner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		amorcer();
	}

	/** Repassage quotidien : amorce l'année suivante dès le 1er janvier. */
	@Scheduled(cron = "${referentiel.conges.amorcage.cron:0 5 0 * * *}", zone = "Africa/Tunis")
	public void repasserChaqueJour() {
		try {
			amorcer();
		} catch (RuntimeException e) {
			log.error("Amorçage quotidien des soldes en échec : {}", e.getMessage());
		}
	}

	/** Point d'entrée rejouable (aussi appelé par les tests). */
	public void amorcer() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			if (estPostgres()) {
				jdbc.execute("select pg_advisory_xact_lock(" + VERROU_AMORCAGE + ")");
			}
			LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
			int reglages = amorcerReglages(aujourdhui);
			contrainteAcquis();
			int feries = amorcerJoursFeries(aujourdhui.getYear());
			if (reglages + feries > 0) {
				log.info("Soldes de congé amorcés : {} réglage(s), {} jour(s) férié(s)", reglages, feries);
			}
		});
	}

	private int amorcerReglages(LocalDate aujourdhui) {
		int n = jdbc.update("insert into rh_parametres (id, jours_acquis_par_mois, debut_acquisitions_le, version, "
				+ "modifie_le) select 1, ?, ?, 0, current_timestamp where not exists (select 1 from rh_parametres where id = 1)",
				ReglagesCongeService.DEFAUT, Date.valueOf(ReglagesCongeService.debutParDefaut(aujourdhui)));
		n += jdbc.update("update rh_parametres set debut_acquisitions_le = ? where id = 1 and debut_acquisitions_le is null",
				Date.valueOf(ReglagesCongeService.debutParDefaut(aujourdhui)));
		return n;
	}

	private void contrainteAcquis() {
		if (tableExiste("rh_solde_conge")) {
			jdbc.execute("ALTER TABLE rh_solde_conge DROP CONSTRAINT IF EXISTS " + CONTRAINTE_ACQUIS);
			jdbc.execute("ALTER TABLE rh_solde_conge ADD CONSTRAINT " + CONTRAINTE_ACQUIS + " CHECK (acquis >= 0)");
		}
	}

	private int amorcerJoursFeries(int anneeCourante) {
		jdbc.execute("create table if not exists rh_jour_ferie_amorcage ("
				+ "annee integer primary key, amorce_le timestamp with time zone not null)");
		int crees = 0;
		for (int annee = anneeCourante; annee <= anneeCourante + 1; annee++) {
			Integer deja = jdbc.queryForObject("select count(*) from rh_jour_ferie_amorcage where annee = ?",
					Integer.class, annee);
			if (deja != null && deja > 0) {
				continue;
			}
			for (Object[] f : FETES_CIVILES) {
				LocalDate date = LocalDate.of(annee, (Integer) f[0], (Integer) f[1]);
				crees += jdbc.update("insert into rh_jour_ferie (id, date_jour, libelle, actif, version, cree_le) "
						+ "select ?, ?, ?, true, 0, current_timestamp "
						+ "where not exists (select 1 from rh_jour_ferie where date_jour = ?)",
						UUID.randomUUID(), Date.valueOf(date), f[2], Date.valueOf(date));
			}
			jdbc.update("insert into rh_jour_ferie_amorcage (annee, amorce_le) values (?, current_timestamp)", annee);
		}
		return crees;
	}

	private boolean tableExiste(String table) {
		Integer n = jdbc.queryForObject(
				"select count(*) from information_schema.tables where lower(table_name) = ?", Integer.class, table);
		return n != null && n > 0;
	}

	private boolean estPostgres() {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		return produit != null && produit.toLowerCase(Locale.ROOT).contains("postgres");
	}
}
