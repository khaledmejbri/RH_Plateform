package com.hr.presence.config;

import com.hr.presence.domain.PointageStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : les statuts ajoutés à {@link PointageStatut}
 * (REJETE_POSITION_SIMULEE — D-P01, VALIDE_MANUEL — D-P04) violaient l'ancienne contrainte.
 * Recrée la contrainte à partir de l'enum à chaque démarrage (idempotent), comme SCRUM-8 côté référentiel.
 */
@Component
public class PointageStatutConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PointageStatutConstraintRunner.class);

	static final String TABLE = "presence_pointage";
	static final String CONSTRAINT = "presence_pointage_statut_check";

	private final JdbcTemplate jdbcTemplate;

	public PointageStatutConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String valeurs = Arrays.stream(PointageStatut.values())
				.map(s -> "'" + s.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
					+ " CHECK (statut IN (" + valeurs + "))");
			log.info("Contrainte {} alignée sur l'enum ({})", CONSTRAINT, valeurs);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", CONSTRAINT, e.getMessage());
		}
	}
}
