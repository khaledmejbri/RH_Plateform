package com.hr.referentiel.importation;

import com.hr.commun.societe.SocieteContexte;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Verrou unique par société pour toutes les confirmations d'import (ADR § 6, § 9.2.2) :
 * {@code pg_try_advisory_xact_lock}, libéré au commit, au rollback et à la coupure de connexion. Hors PostgreSQL (tests),
 * repli sur un verrou mémoire par société libéré en fin de transaction.
 */
@Component
public class ImportVerrou {

	private static final ConcurrentHashMap<UUID, ReentrantLock> VERROUS_LOCAUX = new ConcurrentHashMap<>();

	private final JdbcTemplate jdbc;

	public ImportVerrou(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** À appeler dans la transaction de confirmation ; {@code false} = un autre import est en cours dans la société. */
	public boolean essayer() {
		UUID societeId = SocieteContexte.courante();
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		if (produit != null && produit.toLowerCase(Locale.ROOT).contains("postgres")) {
			Boolean ok = jdbc.queryForObject(
					"select pg_try_advisory_xact_lock(hashtext(?))",
					Boolean.class,
					"import:" + societeId);
			return Boolean.TRUE.equals(ok);
		}
		ReentrantLock lock = VERROUS_LOCAUX.computeIfAbsent(societeId, k -> new ReentrantLock());
		if (!lock.tryLock()) {
			return false;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					lock.unlock();
				}
			});
		} else {
			lock.unlock();
		}
		return true;
	}
}
