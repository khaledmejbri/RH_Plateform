package com.hr.presence.numero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Reprise des demandes existantes (D-L02) : numérote par date de création les lignes sans numéro. Idempotent
 * (ne touche que les lignes sans numéro), une seule instance à la fois (verrou consultatif PostgreSQL).
 * Pour les heures sup, l'ancienne référence « HS-XXXXXXXX » est alignée sur le numéro.
 */
@Component
public class NumeroRepriseRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(NumeroRepriseRunner.class);

	/** Clé du verrou consultatif (identifiant technique, pas un secret). */
	static final long VERROU = 7_204_201L;

	private final JdbcTemplate jdbcTemplate;
	private final NumeroDemandeService numeroService;
	private final TransactionTemplate transaction;

	public NumeroRepriseRunner(JdbcTemplate jdbcTemplate, NumeroDemandeService numeroService,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.numeroService = numeroService;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public void afterSingletonsInstantiated() {
		try {
			Integer total = transaction.execute(status -> {
				jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + VERROU + ")");
				return reprendre("presence_heures_sup_demande", PrefixeNumero.HS, true)
						+ reprendre("presence_demande_manuelle", PrefixeNumero.PM, false);
			});
			if (total != null && total > 0) {
				log.info("Numéros lisibles attribués aux demandes existantes : {}", total);
			}
		} catch (Exception e) {
			log.warn("Reprise des numéros de demande impossible : {}", e.getMessage());
		}
	}

	private int reprendre(String table, PrefixeNumero prefixe, boolean alignerReference) {
		List<Ligne> lignes = jdbcTemplate.query(
				"SELECT id, cree_le FROM " + table + " WHERE numero IS NULL ORDER BY cree_le, id",
				(rs, i) -> new Ligne(rs.getObject("id"), rs.getTimestamp("cree_le").toInstant()));
		String maj = "UPDATE " + table + " SET numero = ?" + (alignerReference ? ", reference = ?" : "")
				+ " WHERE id = ? AND numero IS NULL";
		for (Ligne l : lignes) {
			String numero = numeroService.attribuer(prefixe, l.creeLe());
			if (alignerReference) {
				jdbcTemplate.update(maj, numero, numero, l.id());
			} else {
				jdbcTemplate.update(maj, numero, l.id());
			}
		}
		return lignes.size();
	}

	record Ligne(Object id, Instant creeLe) {
	}
}
