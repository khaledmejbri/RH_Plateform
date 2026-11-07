package com.hr.referentiel.config;

import com.hr.referentiel.repository.NumeroSequenceRepository;
import com.hr.referentiel.service.NumeroLisibleService;
import com.hr.referentiel.service.PrefixeNumero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * D-L02 : reprise des objets créés avant l'introduction du numéro lisible.
 *
 * <ul>
 *   <li>Tourne avant l'ouverture du serveur web ({@link SmartInitializingSingleton}) : aucune création ne peut
 *       prendre un rang avant les anciennes lignes, la plus ancienne de chaque mois reçoit {@code -0001}.</li>
 *   <li>Idempotent : ne traite que les lignes sans numéro, par date de création croissante ; un redémarrage
 *       sans ligne à reprendre ne fait rien.</li>
 *   <li>Une transaction par table, sous verrou consultatif PostgreSQL : deux instances qui démarrent en même
 *       temps ne numérotent pas deux fois.</li>
 * </ul>
 */
@Component
public class NumeroLisibleRepriseRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(NumeroLisibleRepriseRunner.class);

	/** Clé du verrou consultatif PostgreSQL (propre à cette reprise). */
	static final long VERROU_REPRISE = 7_001_902_001L;

	private final JdbcTemplate jdbcTemplate;
	private final NumeroSequenceRepository sequenceRepository;
	private final TransactionTemplate transactionTemplate;

	public NumeroLisibleRepriseRunner(JdbcTemplate jdbcTemplate, NumeroSequenceRepository sequenceRepository,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.sequenceRepository = sequenceRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void afterSingletonsInstantiated() {
		try {
			reprendre("rh_demande_administrative", PrefixeNumero.DEM);
			reprendre("rh_demande_document_administratif", PrefixeNumero.DOC);
			reprendre("rh_demande_formation", PrefixeNumero.FOR);
		} catch (Exception e) {
			// Ne bloque pas le démarrage : la reprise est rejouée au prochain démarrage.
			log.warn("Reprise des numéros lisibles incomplète : {}", e.getMessage());
		}
	}

	private void reprendre(String table, PrefixeNumero prefixe) {
		Integer repris = transactionTemplate.execute(statut -> {
			jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + VERROU_REPRISE + ")");
			List<Ligne> lignes = jdbcTemplate.query(
					"SELECT identifiant, cree_le FROM " + table + " WHERE numero IS NULL ORDER BY cree_le ASC, identifiant ASC",
					(rs, i) -> new Ligne((UUID) rs.getObject("identifiant"), rs.getTimestamp("cree_le")));
			for (Ligne ligne : lignes) {
				String numero = NumeroLisibleService.attribuerAvec(sequenceRepository, prefixe, ligne.creeLe().toInstant());
				jdbcTemplate.update("UPDATE " + table + " SET numero = ? WHERE identifiant = ? AND numero IS NULL",
						numero, ligne.id());
			}
			return lignes.size();
		});
		if (repris != null && repris > 0) {
			log.info("Numéros lisibles {} attribués à {} ligne(s) existante(s) de {}", prefixe, repris, table);
		}
	}

	private record Ligne(UUID id, Timestamp creeLe) {
	}
}
