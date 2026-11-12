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

	private final JdbcTemplate jdbcTemplate;

	public DemandeManuelleStatutConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String valeurs = Arrays.stream(StatutDemandeManuelle.values())
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
