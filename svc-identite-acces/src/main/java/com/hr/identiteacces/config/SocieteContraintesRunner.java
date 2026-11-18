package com.hr.identiteacces.config;

import com.hr.identiteacces.domain.StatutSociete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Aligne les contraintes CHECK et d'unicité de {@code societe} et {@code users} au démarrage (idempotent).
 * Remplace l'ancienne contrainte unique sur {@code users(username)} seul par l'unicité composée
 * {@code (societe_id, lower(username))}.
 */
@Component
@Order(2)
public class SocieteContraintesRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SocieteContraintesRunner.class);

	static final String TABLE_SOCIETE = "societe";
	static final String CONSTRAINT_SOCIETE_STATUT = "societe_statut_check";
	static final String ANCIENNE_CONTRAINTE_USERNAME_HIBERNATE = "ukr43af9ap4edm43mmtq01oddj6";
	static final String ANCIENNE_CONTRAINTE_USERNAME_SQL = "users_username_key";

	private final JdbcTemplate jdbcTemplate;

	public SocieteContraintesRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		recreer();
	}

	public void recreer() {
		String valeursStatut = Arrays.stream(StatutSociete.values())
				.map(s -> "'" + s.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + TABLE_SOCIETE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT_SOCIETE_STATUT);
			jdbcTemplate.execute("ALTER TABLE " + TABLE_SOCIETE + " ADD CONSTRAINT " + CONSTRAINT_SOCIETE_STATUT
					+ " CHECK (statut IN (" + valeursStatut + "))");
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_societe_code_lower ON societe (lower(code))");

			jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + ANCIENNE_CONTRAINTE_USERNAME_HIBERNATE);
			jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + ANCIENNE_CONTRAINTE_USERNAME_SQL);
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_societe_username_lower "
					+ "ON users (societe_id, lower(username))");
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_plateforme_username_lower "
					+ "ON users (lower(username)) WHERE societe_id IS NULL");
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower "
					+ "ON users (lower(email))");
			log.info("Contraintes societe et users alignées ({})", valeursStatut);
		} catch (Exception e) {
			log.warn("Impossible d'aligner les contraintes societe / users : {}", e.getMessage());
		}
	}
}
