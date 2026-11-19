package com.hr.referentiel.config;

import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.SocieteDefautResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test d'intégration PostgreSQL de {@link SocieteRepriseRunner} (§ 9.2.3, § 10.3) sur {@code referentiel_rh_db_test} :
 * <ul>
 *   <li>rattachment des lignes {@code societe_id IS NULL} à la société par défaut et passage en {@code NOT NULL} ;</li>
 *   <li>suppression dynamique d'une contrainte UNIQUE portant un nom arbitraire (ex. {@code uk_legacy_arbitraire_123})
 *       découverte dans {@code pg_constraint} ;</li>
 *   <li>bascule des clés primaires de {@code rh_numero_sequence} et {@code rh_acquisition_mois} vers {@code (societe_id, ...)} ;</li>
 *   <li>idempotence lors d'une seconde exécution.</li>
 * </ul>
 */
class SocieteRepriseRunnerTest {

	private static final UUID SOC_DEFAUT = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SOC_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	@DisplayName("PostgreSQL : reprise multi-société idempotente, contrainte de nom arbitraire remplacée et clés primaires composées")
	void reprisePostgreSqlIdempotenteEtCatalogueDynamique() throws Exception {
		String password = System.getenv("TEST_DB_PASSWORD");
		Assumptions.assumeTrue(password != null && !password.isBlank(),
				"TEST_DB_PASSWORD absent : test PostgreSQL ignoré");
		String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5444/referentiel_rh_db_test");
		String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");

		try (Connection conn = DriverManager.getConnection(url, user, password)) {
			SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
			JdbcTemplate jdbc = new JdbcTemplate(ds);
			TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(ds));

			jdbc.execute("DROP SCHEMA IF EXISTS test_reprise_w1 CASCADE");
			jdbc.execute("CREATE SCHEMA test_reprise_w1");
			jdbc.execute("SET search_path TO test_reprise_w1");
			try {
				// 1. Table avec contrainte UNIQUE au nom arbitraire (comme un nom généré par Hibernate uk_xxx)
				jdbc.execute("CREATE TABLE rh_collaborateur ("
						+ "id UUID PRIMARY KEY, "
						+ "societe_id UUID, "
						+ "matricule VARCHAR(64) NOT NULL, "
						+ "compte_utilisateur_id UUID, "
						+ "CONSTRAINT uk_legacy_arbitraire_123 UNIQUE (matricule))");
				UUID collab1 = UUID.randomUUID();
				jdbc.update("INSERT INTO rh_collaborateur (id, societe_id, matricule) VALUES (?, NULL, 'COL-WEB')", collab1);

				// 2. Table de séquence avec ancienne clé primaire mono-colonne (cle)
				jdbc.execute("CREATE TABLE rh_numero_sequence ("
						+ "cle VARCHAR(16) PRIMARY KEY, "
						+ "societe_id UUID, "
						+ "dernier INTEGER NOT NULL)");
				jdbc.update("INSERT INTO rh_numero_sequence (cle, societe_id, dernier) VALUES ('DEM-202611', NULL, 4)");

				SocieteRepriseRunner runner = new SocieteRepriseRunner(
						jdbc, tt, mock(IdentiteSocieteClient.class), mock(SocieteDefautResolver.class));

				// Première exécution
				runner.executerPourSociete(SOC_DEFAUT);
				// Seconde exécution (idempotence)
				runner.executerPourSociete(SOC_DEFAUT);

				UUID socRattachee = jdbc.queryForObject(
						"SELECT societe_id FROM rh_collaborateur WHERE id = ?", UUID.class, collab1);
				assertThat(socRattachee).isEqualTo(SOC_DEFAUT);

				// L'ancienne contrainte uk_legacy_arbitraire_123 a disparu : une 2e société peut insérer le même matricule COL-WEB
				jdbc.update("INSERT INTO rh_collaborateur (id, societe_id, matricule) VALUES (?, ?, 'COL-WEB')",
						UUID.randomUUID(), SOC_2);
				Integer nbColWeb = jdbc.queryForObject(
						"SELECT count(*) FROM rh_collaborateur WHERE matricule = 'COL-WEB'", Integer.class);
				assertThat(nbColWeb).isEqualTo(2);

				// La séquence accepte désormais la même clé pour une 2e société
				jdbc.update("INSERT INTO rh_numero_sequence (cle, societe_id, dernier) VALUES ('DEM-202611', ?, 1)", SOC_2);
				Integer nbSeq = jdbc.queryForObject(
						"SELECT count(*) FROM rh_numero_sequence WHERE cle = 'DEM-202611'", Integer.class);
				assertThat(nbSeq).isEqualTo(2);
			} finally {
				jdbc.execute("SET search_path TO public");
				jdbc.execute("DROP SCHEMA IF EXISTS test_reprise_w1 CASCADE");
			}
		}
	}
}
