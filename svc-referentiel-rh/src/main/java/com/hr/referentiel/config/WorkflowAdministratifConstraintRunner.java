package com.hr.referentiel.config;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.ActionWorkflowFormation;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
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
 * {@code rh_demande_admin_workflow_history}, {@code rh_demande_administrative},
 * {@code rh_demande_document_administratif}, {@code rh_demande_formation} et {@code rh_formation_workflow_history}
 * (statuts, actions, {@code workflow_mode}, {@code workflow_etat}, D-W5-01 / D-W6-07).
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

	static final String TABLE_DOC = "rh_demande_document_administratif";
	static final String CONSTRAINT_DOC_STATUT = "rh_demande_document_administratif_statut_check";
	static final String CONSTRAINT_DOC_WF_MODE = "rh_demande_document_administratif_workflow_mode_check";
	static final String CONSTRAINT_DOC_WF_ETAT = "rh_demande_document_administratif_workflow_etat_check";

	static final String TABLE_FORMATION = "rh_demande_formation";
	static final String CONSTRAINT_FORMATION_STATUT = "rh_demande_formation_statut_check";
	static final String CONSTRAINT_FORMATION_WF_MODE = "rh_demande_formation_workflow_mode_check";
	static final String CONSTRAINT_FORMATION_WF_ETAT = "rh_demande_formation_workflow_etat_check";
	static final String TABLE_FORMATION_HIST = "rh_formation_workflow_history";
	static final String CONSTRAINT_FORMATION_HIST_ACTION = "rh_formation_workflow_history_action_check";

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
		alignerContrainte(TABLE, CONSTRAINT, "action IN (" + valeursEnum(ActionWorkflowAdministratif.values()) + ")");
		alignerWorkflowTable(TABLE_DEMANDE, CONSTRAINT_STATUT, CONSTRAINT_WF_MODE, CONSTRAINT_WF_ETAT,
				StatutDemandeAdministrativeRh.values());

		alignerWorkflowTable(TABLE_DOC, CONSTRAINT_DOC_STATUT, CONSTRAINT_DOC_WF_MODE, CONSTRAINT_DOC_WF_ETAT,
				StatutDocumentAdministratifDemandeRh.values());

		alignerWorkflowTable(TABLE_FORMATION, CONSTRAINT_FORMATION_STATUT, CONSTRAINT_FORMATION_WF_MODE,
				CONSTRAINT_FORMATION_WF_ETAT, StatutDemandeFormationRh.values());
		alignerContrainte(TABLE_FORMATION_HIST, CONSTRAINT_FORMATION_HIST_ACTION,
				"action IN (" + valeursEnum(ActionWorkflowFormation.values()) + ")");
	}

	private void alignerWorkflowTable(
			String table,
			String contrainteStatut,
			String contrainteMode,
			String contrainteEtat,
			Enum<?>[] statuts) {
		alignerContrainte(table, contrainteStatut, "statut IN (" + valeursEnum(statuts) + ")");
		try {
			jdbcTemplate.execute("UPDATE " + table + " SET workflow_mode = 'HERITE' WHERE workflow_mode IS NULL");
			jdbcTemplate.execute("UPDATE " + table + " SET workflow_tentatives = 0 WHERE workflow_tentatives IS NULL");
		} catch (Exception e) {
			log.warn("Impossible d'initialiser les valeurs par défaut workflow sur {} : {}", table, e.getMessage());
		}
		alignerContrainte(table, contrainteMode, "workflow_mode IN ('HERITE', 'MOTEUR')");
		alignerContrainte(table, contrainteEtat,
				"workflow_etat IS NULL OR workflow_etat IN ('A_DEMARRER', 'DEMARRE', 'ECHEC')");
	}

	private static String valeursEnum(Enum<?>[] values) {
		return Arrays.stream(values)
				.map(v -> "'" + v.name() + "'")
				.collect(Collectors.joining(", "));
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
