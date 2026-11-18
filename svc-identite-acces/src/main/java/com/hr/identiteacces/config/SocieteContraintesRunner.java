package com.hr.identiteacces.config;

import com.hr.identiteacces.domain.StatutSociete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aligne les contraintes CHECK et d'unicité de {@code societe} et {@code users} au démarrage (idempotent),
 * sous verrou consultatif transactionnel {@code pg_advisory_xact_lock}.
 * <p>
 * Retrouve dans le catalogue PostgreSQL ({@code pg_constraint} / {@code pg_index}) toute ancienne contrainte
 * ou tout ancien index unique mono-colonne sur {@code users(username)} et le remplace par l'unicité composée
 * {@code (societe_id, lower(username))}. En cas d'erreur SQL, interrompt le démarrage (amélioration 2).
 */
@Component
@Order(2)
public class SocieteContraintesRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SocieteContraintesRunner.class);

	static final String TABLE_SOCIETE = "societe";
	static final String CONSTRAINT_SOCIETE_STATUT = "societe_statut_check";

	private static final String SQL_VERROU =
			"SELECT pg_advisory_xact_lock(hashtext('identite-contraintes-societe'))";

	private static final String SQL_CONTRAINTES_UNIQUES_USERNAME_SEUL = """
			SELECT c.conname
			FROM pg_constraint c
			JOIN pg_class t ON t.oid = c.conrelid
			JOIN pg_namespace n ON n.oid = t.relnamespace
			JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
			WHERE t.relname = 'users'
			  AND n.nspname = current_schema()
			  AND c.contype = 'u'
			  AND array_length(c.conkey, 1) = 1
			  AND a.attname = 'username'
			""";

	private static final String SQL_INDEX_UNIQUES_USERNAME_SEUL = """
			SELECT i.relname
			FROM pg_index ix
			JOIN pg_class i ON i.oid = ix.indexrelid
			JOIN pg_class t ON t.oid = ix.indrelid
			JOIN pg_namespace n ON n.oid = t.relnamespace
			JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
			WHERE t.relname = 'users'
			  AND n.nspname = current_schema()
			  AND ix.indisunique = true
			  AND ix.indpred IS NULL
			  AND array_length(ix.indkey, 1) = 1
			  AND a.attname = 'username'
			  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = i.oid)
			""";

	private final JdbcTemplate jdbcTemplate;

	public SocieteContraintesRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		recreer();
	}

	@Transactional
	public void recreer() {
		String valeursStatut = Arrays.stream(StatutSociete.values())
				.map(s -> "'" + s.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute(SQL_VERROU);

			jdbcTemplate.execute("ALTER TABLE " + TABLE_SOCIETE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT_SOCIETE_STATUT);
			jdbcTemplate.execute("ALTER TABLE " + TABLE_SOCIETE + " ADD CONSTRAINT " + CONSTRAINT_SOCIETE_STATUT
					+ " CHECK (statut IN (" + valeursStatut + "))");
			log.info("Contrainte CHECK {} posée sur {} ({})", CONSTRAINT_SOCIETE_STATUT, TABLE_SOCIETE, valeursStatut);

			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_societe_code_lower ON societe (lower(code))");
			log.info("Index unique uk_societe_code_lower vérifié sur societe");

			List<String> anciennesContraintes = jdbcTemplate.queryForList(
					SQL_CONTRAINTES_UNIQUES_USERNAME_SEUL, String.class);
			for (String conname : anciennesContraintes) {
				String identifiantSql = "\"" + conname.replace("\"", "\"\"") + "\"";
				jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + identifiantSql);
				log.info("Ancienne contrainte unique mono-colonne sur users(username) supprimée : {}", conname);
			}

			List<String> anciensIndex = jdbcTemplate.queryForList(
					SQL_INDEX_UNIQUES_USERNAME_SEUL, String.class);
			for (String indexName : anciensIndex) {
				String identifiantSql = "\"" + indexName.replace("\"", "\"\"") + "\"";
				jdbcTemplate.execute("DROP INDEX IF EXISTS " + identifiantSql);
				log.info("Ancien index unique mono-colonne sur users(username) supprimé : {}", indexName);
			}

			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_societe_username_lower "
					+ "ON users (societe_id, lower(username))");
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_plateforme_username_lower "
					+ "ON users (lower(username)) WHERE societe_id IS NULL");
			jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower "
					+ "ON users (lower(email))");
			log.info("Index uniques multi-société posés sur users : uk_users_societe_username_lower, "
					+ "uk_users_plateforme_username_lower, uk_users_email_lower");
		} catch (Exception e) {
			throw new IllegalStateException(
					"Impossible d'aligner les contraintes societe / users : démarrage interrompu", e);
		}
	}
}
