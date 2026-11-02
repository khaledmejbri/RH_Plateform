package com.hr.presence.config;

import com.hr.presence.domain.StatutHeuresSup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Recrée la contrainte CHECK du statut d'une demande d'heures sup depuis l'enum à chaque démarrage (idempotent). */
@Component
public class HeuresSupStatutConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(HeuresSupStatutConstraintRunner.class);

	static final String TABLE = "presence_heures_sup_demande";
	static final String CONSTRAINT = "presence_heures_sup_demande_statut_check";

	private final JdbcTemplate jdbcTemplate;

	public HeuresSupStatutConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String valeurs = Arrays.stream(StatutHeuresSup.values())
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
