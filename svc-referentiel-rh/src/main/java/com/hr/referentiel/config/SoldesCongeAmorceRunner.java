package com.hr.referentiel.config;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import com.hr.referentiel.service.FormatJours;
import com.hr.referentiel.service.ReglagesCongeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
 * Amorçage idempotent des soldes de congé par société (ADR § 9, D-SOC19) : ligne de réglages {@code rh_parametres},
 * contrainte {@code acquis >= 0}, et 8 jours fériés civils de l'année en cours et de la suivante.
 * Rejoué au démarrage (après {@link SocieteRepriseRunner}), chaque jour sous {@link VerrouPlanificateur}, et à la
 * réception de {@code SOCIETE_CREEE}.
 */
@Component
@Order(2)
public class SoldesCongeAmorceRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SoldesCongeAmorceRunner.class);

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
	private final SocietesActives societesActives;
	private final VerrouPlanificateur verrouPlanificateur;

	@Autowired
	public SoldesCongeAmorceRunner(
			JdbcTemplate jdbc,
			PlatformTransactionManager transactionManager,
			Clock clock,
			SocietesActives societesActives,
			VerrouPlanificateur verrouPlanificateur) {
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.clock = clock;
		this.societesActives = societesActives;
		this.verrouPlanificateur = verrouPlanificateur;
	}

	public SoldesCongeAmorceRunner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
		this(jdbc, transactionManager, clock, List::of, null);
	}

	@Override
	public void run(ApplicationArguments args) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> contrainteAcquis());
		executerPourToutesSocietes();
	}

	/** Repassage quotidien : amorce l'année suivante dès le 1er janvier. */
	@Scheduled(cron = "${referentiel.conges.amorcage.cron:0 5 0 * * *}", zone = "Africa/Tunis")
	public void repasserChaqueJour() {
		if (verrouPlanificateur != null) {
			verrouPlanificateur.executerSiLibre("referentiel:amorce-soldes", this::executerPourToutesSocietes);
		} else {
			executerPourToutesSocietes();
		}
	}

	public void executerPourToutesSocietes() {
		List<UUID> actives;
		try {
			actives = societesActives.lister();
		} catch (RuntimeException e) {
			log.error("Amorçage des soldes : impossible de lister les sociétés actives : {}", e.getMessage());
			return;
		}
		for (UUID societeId : actives) {
			try {
				amorcerPourSociete(societeId);
			} catch (RuntimeException e) {
				log.error("Amorçage quotidien des soldes en échec pour société {} : {}", societeId, e.getMessage());
			}
		}
	}

	public void amorcerPourSociete(UUID societeId) {
		SocieteContexte.executer(societeId, this::amorcer);
	}

	/** Point d'entrée rejouable dans un {@link SocieteContexte} actif (aussi appelé par les tests). */
	public void amorcer() {
		UUID societeId = SocieteContexte.courante();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			if (estPostgres()) {
				jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, "amorce-soldes:" + societeId);
			}
			LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
			int reglages = amorcerReglages(societeId, aujourdhui);
			amorcerParametresSociete(societeId);
			int feries = amorcerJoursFeries(societeId, aujourdhui.getYear());
			if (reglages + feries > 0) {
				log.info("Soldes de congé amorcés (société {}) : {} réglage(s), {} jour(s) férié(s)",
						societeId, reglages, feries);
			}
		});
	}

	private void amorcerParametresSociete(UUID societeId) {
		if (!tableExiste("rh_societe_parametres")) {
			return;
		}
		String castJson = estPostgres() ? "cast(? as jsonb)" : "?";
		jdbc.update("insert into rh_societe_parametres (id, societe_id, jours_travailles, duree_hebdo_minutes, "
						+ "duree_jour_minutes, heure_debut_journee, heure_fin_journee, base_decompte_conge, "
						+ "duree_max_sortie_minutes, delegation_auto_conges, publication_quatre_yeux, "
						+ "version, cree_le, modifie_le) "
						+ "select ?, ?, " + castJson + ", 2400, " + castJson
						+ ", '08:00', '17:00', 'JOURS_TRAVAILLES', 120, true, false, 0, current_timestamp, current_timestamp "
						+ "where not exists (select 1 from rh_societe_parametres where societe_id = ?)",
				UUID.randomUUID(),
				societeId,
				"[\"LUN\",\"MAR\",\"MER\",\"JEU\",\"VEN\"]",
				"{\"LUN\":480,\"MAR\":480,\"MER\":480,\"JEU\":480,\"VEN\":480}",
				societeId);
	}

	private int amorcerReglages(UUID societeId, LocalDate aujourdhui) {
		int n = jdbc.update("insert into rh_parametres (id, societe_id, jours_acquis_par_mois, debut_acquisitions_le, version, modifie_le) "
				+ "select (select coalesce(max(p.id), 0) + 1 from rh_parametres p), ?, ?, ?, 0, current_timestamp "
				+ "where not exists (select 1 from rh_parametres where societe_id = ?)",
				societeId, ReglagesCongeService.DEFAUT, Date.valueOf(ReglagesCongeService.debutParDefaut(aujourdhui)), societeId);
		n += jdbc.update("update rh_parametres set debut_acquisitions_le = ? where societe_id = ? and debut_acquisitions_le is null",
				Date.valueOf(ReglagesCongeService.debutParDefaut(aujourdhui)), societeId);
		return n;
	}

	private void contrainteAcquis() {
		if (tableExiste("rh_solde_conge")) {
			jdbc.execute("ALTER TABLE rh_solde_conge DROP CONSTRAINT IF EXISTS " + CONTRAINTE_ACQUIS);
			jdbc.execute("ALTER TABLE rh_solde_conge ADD CONSTRAINT " + CONTRAINTE_ACQUIS + " CHECK (acquis >= 0)");
		}
	}

	private int amorcerJoursFeries(UUID societeId, int anneeCourante) {
		jdbc.execute("create table if not exists rh_jour_ferie_amorcage ("
				+ "societe_id uuid not null, annee integer not null, amorce_le timestamp with time zone not null, "
				+ "primary key (societe_id, annee))");
		int crees = 0;
		for (int annee = anneeCourante; annee <= anneeCourante + 1; annee++) {
			Integer deja = jdbc.queryForObject(
					"select count(*) from rh_jour_ferie_amorcage where societe_id = ? and annee = ?",
					Integer.class, societeId, annee);
			if (deja != null && deja > 0) {
				continue;
			}
			for (Object[] f : FETES_CIVILES) {
				LocalDate date = LocalDate.of(annee, (Integer) f[0], (Integer) f[1]);
				crees += jdbc.update("insert into rh_jour_ferie (id, societe_id, date_jour, libelle, actif, version, cree_le) "
						+ "select ?, ?, ?, ?, true, 0, current_timestamp "
						+ "where not exists (select 1 from rh_jour_ferie where societe_id = ? and date_jour = ?)",
						UUID.randomUUID(), societeId, Date.valueOf(date), f[2], societeId, Date.valueOf(date));
			}
			jdbc.update("insert into rh_jour_ferie_amorcage (societe_id, annee, amorce_le) values (?, ?, current_timestamp)",
					societeId, annee);
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
