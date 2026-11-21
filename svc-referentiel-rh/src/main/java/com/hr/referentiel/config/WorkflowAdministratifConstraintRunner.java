package com.hr.referentiel.config;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : aligne au démarrage (idempotent) les contraintes de
 * {@code rh_demande_admin_workflow_history} et {@code rh_demande_administrative} (statuts, {@code workflow_mode},
 * {@code workflow_etat}, D-W5-01 / D-W5-08).
 */
@Component
public class WorkflowAdministratifConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(WorkflowAdministratifConstraintRunner.class);

	static final String TABLE = "rh_demande_admin_workflow_history";
	static final String CONSTRAINT = "rh_demande_admin_workflow_history_action_check";
	static final String TABLE_DEMANDE = "rh_demande_administrative";
	static final String CONSTRAINT_STATUT = "rh_demande_administrative_statut_check";
	static final String CONSTRAINT_WF_MODE = "rh_demande_administrative_workflow_mode_check";
	static final String CONSTRAINT_WF_ETAT = "rh_demande_administrative_workflow_etat_check";

	private final JdbcTemplate jdbcTemplate;

	public WorkflowAdministratifConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		recreer();
	}

	/** Visible pour les tests. */
	void recreer() {
		String actions = Arrays.stream(ActionWorkflowAdministratif.values())
				.map(a -> "'" + a.name() + "'")
				.collect(Collectors.joining(", "));
		alignerContrainte(TABLE, CONSTRAINT, "action IN (" + actions + ")");

		String statuts = Arrays.stream(StatutDemandeAdministrativeRh.values())
				.map(s -> "'" + s.name() + "'")
				.collect(Collectors.joining(", "));
		alignerContrainte(TABLE_DEMANDE, CONSTRAINT_STATUT, "statut IN (" + statuts + ")");

		try {
			jdbcTemplate.execute("UPDATE " + TABLE_DEMANDE + " SET workflow_mode = 'HERITE' WHERE workflow_mode IS NULL");
			jdbcTemplate.execute("UPDATE " + TABLE_DEMANDE + " SET workflow_tentatives = 0 WHERE workflow_tentatives IS NULL");
		} catch (Exception e) {
			log.warn("Impossible d'initialiser les valeurs par défaut workflow sur {} : {}", TABLE_DEMANDE, e.getMessage());
		}

		alignerContrainte(TABLE_DEMANDE, CONSTRAINT_WF_MODE, "workflow_mode IN ('HERITE', 'MOTEUR')");
		alignerContrainte(TABLE_DEMANDE, CONSTRAINT_WF_ETAT,
				"workflow_etat IS NULL OR workflow_etat IN ('A_DEMARRER', 'DEMARRE', 'ECHEC')");
	}

	private void alignerContrainte(String table, String contrainte, String expression) {
		try {
			jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + contrainte);
			jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + contrainte
					+ " CHECK (" + expression + ")");
			log.info("Contrainte {} alignée sur {}", contrainte, table);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", contrainte, e.getMessage());
		}
	}
}
