package com.hr.commun.planif;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerrouPlanificateurTest {

	@Test
	@DisplayName("PostgreSQL (authentication_db_test) : deux appels concurrents sur la même tâche, un seul exécuté")
	void deuxAppelsConcurrents_unSeulExecute_postgres() throws Exception {
		DataSource ds = creerDataSourcePostgresDepuisEnvironnement();
		verifierExclusionConcurrente(ds);
	}

	@Test
	@DisplayName("Simulacre JDBC : deux appels concurrents sur la même tâche, un seul exécuté")
	void deuxAppelsConcurrents_unSeulExecute_simulacreJdbc() throws Exception {
		DataSource ds = creerDataSourceSimule();
		verifierExclusionConcurrente(ds);
	}

	@Test
	@DisplayName("Amélioration 8 : en cas d'échec de pg_advisory_unlock, la connexion physique est avortée (conn.abort)")
	void echecDeverrouillage_avorteConnexionPhysique() throws Exception {
		DataSource ds = mock(DataSource.class);
		Connection conn = mock(Connection.class);
		when(ds.getConnection()).thenReturn(conn);

		when(conn.prepareStatement(anyString())).thenAnswer(inv -> {
			String sql = inv.getArgument(0);
			PreparedStatement ps = mock(PreparedStatement.class);
			if (sql.contains("pg_try_advisory_lock")) {
				ResultSet rs = mock(ResultSet.class);
				when(rs.next()).thenReturn(true);
				when(rs.getBoolean(1)).thenReturn(true);
				when(ps.executeQuery()).thenReturn(rs);
			} else {
				when(ps.executeQuery()).thenThrow(new SQLException("Panne réseau déverrouillage"));
			}
			return ps;
		});

		VerrouPlanificateur verrou = new VerrouPlanificateur(ds);
		AtomicBoolean execute = new AtomicBoolean(false);
		boolean ok = verrou.executerSiLibre("tache-abort", () -> execute.set(true));

		assertThat(ok).isTrue();
		assertThat(execute.get()).isTrue();
		verify(conn).abort(any());
	}

	private static void verifierExclusionConcurrente(DataSource ds) throws Exception {
		VerrouPlanificateur verrou = new VerrouPlanificateur(ds);
		String nomTache = "test-concurrence-" + UUID.randomUUID();

		CountDownLatch dansTache = new CountDownLatch(1);
		CountDownLatch autoriserSortie = new CountDownLatch(1);
		AtomicInteger executions = new AtomicInteger(0);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> f1 = pool.submit(() -> verrou.executerSiLibre(nomTache, () -> {
				executions.incrementAndGet();
				dansTache.countDown();
				try {
					autoriserSortie.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));

			assertThat(dansTache.await(5, TimeUnit.SECONDS)).isTrue();

			Future<Boolean> f2 = pool.submit(() -> verrou.executerSiLibre(nomTache, executions::incrementAndGet));
			boolean resultatConcurrent = f2.get(5, TimeUnit.SECONDS);
			assertThat(resultatConcurrent).isFalse();

			autoriserSortie.countDown();
			assertThat(f1.get(5, TimeUnit.SECONDS)).isTrue();
			assertThat(executions.get()).isEqualTo(1);

			boolean reexecution = verrou.executerSiLibre(nomTache, executions::incrementAndGet);
			assertThat(reexecution).isTrue();
			assertThat(executions.get()).isEqualTo(2);
		} finally {
			pool.shutdownNow();
		}
	}

	private static DataSource creerDataSourcePostgresDepuisEnvironnement() {
		String url = System.getenv().getOrDefault("TEST_DB_URL",
				"jdbc:postgresql://localhost:5444/authentication_db_test");
		String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
		String pass = System.getenv("TEST_DB_PASSWORD");
		if (pass == null || pass.isBlank()) {
			pass = System.getenv("PGPASSWORD");
		}
		Assumptions.assumeTrue(pass != null && !pass.isBlank(),
				"Variable d'environnement TEST_DB_PASSWORD (ou PGPASSWORD) absente : test PostgreSQL ignoré");

		PGSimpleDataSource pg = new PGSimpleDataSource();
		pg.setURL(url);
		pg.setUser(user);
		pg.setPassword(pass);
		try (Connection c = pg.getConnection()) {
			return pg;
		} catch (SQLException e) {
			Assumptions.assumeTrue(false,
					"Base PostgreSQL authentication_db_test injoignable (" + e.getMessage() + ") : test ignoré");
			return pg;
		}
	}

	private static DataSource creerDataSourceSimule() throws Exception {
		AtomicBoolean occupe = new AtomicBoolean(false);
		DataSource ds = mock(DataSource.class);
		when(ds.getConnection()).thenAnswer(inv -> {
			Connection conn = mock(Connection.class);
			when(conn.prepareStatement(anyString())).thenAnswer(pInv -> {
				String sql = pInv.getArgument(0);
				PreparedStatement ps = mock(PreparedStatement.class);
				ResultSet rs = mock(ResultSet.class);
				when(rs.next()).thenReturn(true);
				if (sql.contains("pg_try_advisory_lock")) {
					when(rs.getBoolean(1)).thenAnswer(rInv -> occupe.compareAndSet(false, true));
				} else {
					when(rs.getBoolean(1)).thenAnswer(rInv -> {
						occupe.set(false);
						return true;
					});
				}
				when(ps.executeQuery()).thenReturn(rs);
				return ps;
			});
			return conn;
		});
		return ds;
	}
}
