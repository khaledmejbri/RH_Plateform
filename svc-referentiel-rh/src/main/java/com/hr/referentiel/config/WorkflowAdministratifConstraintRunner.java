package com.hr.referentiel.config;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : la valeur {@code ANNULATION_APRES_APPROBATION} ajoutée à
 * {@link ActionWorkflowAdministratif} violerait l'ancienne contrainte de l'historique de workflow. Recrée la contrainte
 * à partir de l'enum à chaque démarrage (idempotent), sur le modèle de {@code PointageStatutConstraintRunner}.
 */
@Component
public class WorkflowAdministratifConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(WorkflowAdministratifConstraintRunner.class);

	static final String TABLE = "rh_demande_admin_workflow_history";
	static final String CONSTRAINT = "rh_demande_admin_workflow_history_action_check";

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
		String valeurs = Arrays.stream(ActionWorkflowAdministratif.values())
				.map(a -> "'" + a.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
					+ " CHECK (action IN (" + valeurs + "))");
			log.info("Contrainte {} alignée sur l'enum ({})", CONSTRAINT, valeurs);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", CONSTRAINT, e.getMessage());
		}
	}
}
