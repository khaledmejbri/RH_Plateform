package com.hr.referentiel.config;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import com.hr.referentiel.repository.NumeroSequenceRepository;
import com.hr.referentiel.service.NumeroLisibleService;
import com.hr.referentiel.service.PrefixeNumero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * D-L02 / D-SOC19 : reprise des objets créés avant l'introduction du numéro lisible, exécutée par société après
 * {@link SocieteRepriseRunner}.
 */
@Component
@Order(3)
public class NumeroLisibleRepriseRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(NumeroLisibleRepriseRunner.class);

	private final JdbcTemplate jdbcTemplate;
	private final NumeroSequenceRepository sequenceRepository;
	private final TransactionTemplate transactionTemplate;
	private final SocietesActives societesActives;

	@Autowired
	public NumeroLisibleRepriseRunner(
			JdbcTemplate jdbcTemplate,
			NumeroSequenceRepository sequenceRepository,
			PlatformTransactionManager transactionManager,
			SocietesActives societesActives) {
		this.jdbcTemplate = jdbcTemplate;
		this.sequenceRepository = sequenceRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.societesActives = societesActives;
	}

	public NumeroLisibleRepriseRunner(JdbcTemplate jdbcTemplate, NumeroSequenceRepository sequenceRepository,
			PlatformTransactionManager transactionManager) {
		this(jdbcTemplate, sequenceRepository, transactionManager, List::of);
	}

	@Override
	public void run(ApplicationArguments args) {
		List<UUID> actives;
		try {
			actives = societesActives.lister();
		} catch (RuntimeException e) {
			log.warn("Reprise des numéros lisibles : impossible de lister les sociétés actives : {}", e.getMessage());
			return;
		}
		for (UUID societeId : actives) {
			try {
				SocieteContexte.executer(societeId, () -> {
					reprendre(societeId, "rh_demande_administrative", PrefixeNumero.DEM);
					reprendre(societeId, "rh_demande_document_administratif", PrefixeNumero.DOC);
					reprendre(societeId, "rh_demande_formation", PrefixeNumero.FOR);
				});
			} catch (Exception e) {
				log.warn("Reprise des numéros lisibles incomplète pour société {} : {}", societeId, e.getMessage());
			}
		}
	}

	private void reprendre(UUID societeId, String table, PrefixeNumero prefixe) {
		Integer repris = transactionTemplate.execute(statut -> {
			jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
					"reprise-numeros:" + societeId);
			List<Ligne> lignes = jdbcTemplate.query(
					"SELECT identifiant, cree_le FROM " + table
							+ " WHERE societe_id = ? AND numero IS NULL ORDER BY cree_le ASC, identifiant ASC",
					(rs, i) -> new Ligne((UUID) rs.getObject("identifiant"), rs.getTimestamp("cree_le")),
					societeId);
			for (Ligne ligne : lignes) {
				String numero = NumeroLisibleService.attribuerAvec(sequenceRepository, prefixe, ligne.creeLe().toInstant());
				jdbcTemplate.update(
						"UPDATE " + table + " SET numero = ? WHERE societe_id = ? AND identifiant = ? AND numero IS NULL",
						numero, societeId, ligne.id());
			}
			return lignes.size();
		});
		if (repris != null && repris > 0) {
			log.info("Numéros lisibles {} attribués à {} ligne(s) existante(s) de {} (société {})",
					prefixe, repris, table, societeId);
		}
	}

	private record Ligne(UUID id, Timestamp creeLe) {
	}
}
