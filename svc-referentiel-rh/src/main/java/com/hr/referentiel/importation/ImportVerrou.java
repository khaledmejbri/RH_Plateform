package com.hr.referentiel.importation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Verrou unique pour toutes les confirmations d'import (ADR § 6) : {@code pg_try_advisory_xact_lock}, libéré au commit,
 * au rollback et à la coupure de connexion (arrêt du service). Hors PostgreSQL (tests), repli sur un verrou mémoire
 * libéré en fin de transaction.
 */
@Component
public class ImportVerrou {

	/** Clé du verrou consultatif PostgreSQL (propre aux imports). */
	static final long CLE = 7_001_903_003L;

	private static final ReentrantLock LOCAL = new ReentrantLock();

	private final JdbcTemplate jdbc;

	public ImportVerrou(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** À appeler dans la transaction de confirmation ; {@code false} = un autre import est en cours. */
	public boolean essayer() {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		if (produit != null && produit.toLowerCase(Locale.ROOT).contains("postgres")) {
			Boolean ok = jdbc.queryForObject("select pg_try_advisory_xact_lock(" + CLE + ")", Boolean.class);
			return Boolean.TRUE.equals(ok);
		}
		if (!LOCAL.tryLock()) {
			return false;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					LOCAL.unlock();
				}
			});
		} else {
			LOCAL.unlock();
		}
		return true;
	}
}
