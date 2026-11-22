package com.hr.presence.config;

import com.hr.presence.domain.StatutDemandeManuelle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Recrée la contrainte CHECK du statut d'une demande de pointage manuel (EXPIREE, D-P23.b), idempotent. */
@Component
public class DemandeManuelleStatutConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemandeManuelleStatutConstraintRunner.class);

	static final String TABLE = "presence_demande_manuelle";
	static final String CONSTRAINT = "presence_demande_manuelle_statut_check";
	static final String CONSTRAINT_WF_MODE = "presence_demande_manuelle_workflow_mode_check";
	static final String CONSTRAINT_WF_ETAT = "presence_demande_manuelle_workflow_etat_check";

	private final JdbcTemplate jdbcTemplate;

	public DemandeManuelleStatutConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String valeurs = Arrays.stream(StatutDemandeManuelle.values())
				.map(s -> "'" + s.name() + "'")
				.collect(Collectors.joining(", "));
		alignerContrainte(CONSTRAINT, "statut IN (" + valeurs + ")");
		try {
			jdbcTemplate.execute("UPDATE " + TABLE + " SET workflow_mode = 'HERITE' WHERE workflow_mode IS NULL");
			jdbcTemplate.execute("UPDATE " + TABLE + " SET workflow_tentatives = 0 WHERE workflow_tentatives IS NULL");
		} catch (Exception e) {
			log.warn("Impossible d'initialiser les valeurs par défaut workflow sur {} : {}", TABLE, e.getMessage());
		}
		alignerContrainte(CONSTRAINT_WF_MODE, "workflow_mode IN ('HERITE', 'MOTEUR')");
		alignerContrainte(CONSTRAINT_WF_ETAT,
				"workflow_etat IS NULL OR workflow_etat IN ('A_DEMARRER', 'DEMARRE', 'ECHEC')");
	}

	private void alignerContrainte(String contrainte, String expression) {
		try {
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + contrainte);
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + contrainte
					+ " CHECK (" + expression + ")");
			log.info("Contrainte {} alignée sur {}", contrainte, TABLE);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", contrainte, e.getMessage());
		}
	}
}
