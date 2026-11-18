package com.hr.commun.planif;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerrouPlanificateurTest {

	@Test
	@DisplayName("Deux appels concurrents sur la même tâche : un seul est exécuté, puis le verrou est relâché")
	void deuxAppelsConcurrents_unSeulExecute() throws Exception {
		DataSource ds = creerDataSourceSiPostgresDisponible();
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

			// Une fois le premier terminé, le verrou est libre à nouveau
			boolean reexecution = verrou.executerSiLibre(nomTache, executions::incrementAndGet);
			assertThat(reexecution).isTrue();
			assertThat(executions.get()).isEqualTo(2);
		} finally {
			pool.shutdownNow();
		}
	}

	private static DataSource creerDataSourceSiPostgresDisponible() throws Exception {
		PGSimpleDataSource pg = new PGSimpleDataSource();
		String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5444/authentication_db");
		String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
		String pass = System.getenv().getOrDefault("TEST_DB_PASSWORD", "password");
		pg.setURL(url);
		pg.setUser(user);
		pg.setPassword(pass);
		try (Connection c = pg.getConnection()) {
			return pg;
		} catch (Exception e) {
			return creerDataSourceSimule();
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
