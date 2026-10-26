package com.hr.referentiel.config;

import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * SCRUM-8 — {@code ddl-auto=update} ne régénère pas les CHECK existants : un type ajouté à
 * {@link TypeDocumentAdministratifDemandeRh} (ex. FEUILLE_POINTAGE_MENSUELLE) violait l'ancienne contrainte.
 * Recrée la contrainte à partir de l'enum à chaque démarrage (idempotent).
 */
@Component
public class TypeDocumentConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TypeDocumentConstraintRunner.class);

	static final String TABLE = "rh_demande_document_administratif";
	static final String CONSTRAINT = "rh_demande_document_administratif_type_document_check";

	private final JdbcTemplate jdbcTemplate;

	public TypeDocumentConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String valeurs = Arrays.stream(TypeDocumentAdministratifDemandeRh.values())
				.map(t -> "'" + t.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
			jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
					+ " CHECK (type_document IN (" + valeurs + "))");
			log.info("Contrainte {} alignée sur l'enum ({})", CONSTRAINT, valeurs);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", CONSTRAINT, e.getMessage());
		}
	}
}
