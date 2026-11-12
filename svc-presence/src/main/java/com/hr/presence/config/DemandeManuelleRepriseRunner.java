package com.hr.presence.config;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.DemandeManuelleReponseMapper;
import com.hr.presence.service.EligibiliteManuelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reprise à la mise en service (D-P26.a) : chaque demande EN_ATTENTE sans échéance reçoit une échéance égale à
 * maintenant + délai de décision (pas d'expiration rétroactive en masse) et ses signaux et mesures figés, calculés
 * depuis la tentative d'origine ; {@code plafond_metres} reste nul et {@code reprise} vaut vrai. Idempotent (une demande
 * qui a déjà une échéance n'est pas touchée), une seule instance à la fois (verrou consultatif PostgreSQL).
 */
@Component
public class DemandeManuelleRepriseRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(DemandeManuelleRepriseRunner.class);

	/** Clé du verrou consultatif (identifiant technique, pas un secret). */
	static final long VERROU = 7_204_202L;

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transaction;
	private final PresenceDemandeManuelleRepository demandeRepository;
	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final EligibiliteManuelService eligibilite;
	private final DemandeManuelleReponseMapper mapper;
	private final PresenceProperties properties;
	private final Clock clock;

	public DemandeManuelleRepriseRunner(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
			PresenceDemandeManuelleRepository demandeRepository, PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository, EligibiliteManuelService eligibilite,
			DemandeManuelleReponseMapper mapper, PresenceProperties properties, Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.transaction = new TransactionTemplate(transactionManager);
		this.demandeRepository = demandeRepository;
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.eligibilite = eligibilite;
		this.mapper = mapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		try {
			Integer total = transaction.execute(status -> {
				jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + VERROU + ")");
				return reprendre();
			});
			if (total != null && total > 0) {
				log.info("Reprise D-P26.a : {} demande(s) de pointage manuel en attente complétée(s)", total);
			}
		} catch (Exception e) {
			log.warn("Reprise des demandes de pointage manuel impossible : {}", e.getMessage());
		}
	}

	/** Complète les demandes en attente sans échéance ; renvoie leur nombre. */
	int reprendre() {
		List<PresenceDemandeManuelle> aReprendre =
				demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE);
		Instant maintenant = clock.instant();
		Duration delaiDecision = Duration.ofMinutes(properties.getManuel().getDelaiDecisionMinutes());
		for (PresenceDemandeManuelle d : aReprendre) {
			d.setEcheance(maintenant.plus(delaiDecision));
			d.setReprise(true);
			d.setPlafondMetres(null);
			PresencePointage rejet = pointageRepository.findById(d.getPointageRejeteId()).orElse(null);
			if (rejet != null) {
				PresenceSite site = rejet.getSiteId() != null ? siteRepository.findById(rejet.getSiteId()).orElse(null) : null;
				Instant creation = d.getCreeLe() != null ? d.getCreeLe() : maintenant;
				EligibiliteManuelService.Figees figees = eligibilite.figer(rejet, site,
						Duration.between(rejet.getServerTs(), creation),
						mapper.manuelsDuMois(d.getCollaborateurId(), d.getHeureDeclaree()));
				d.setRefusStatut(figees.refusStatut());
				d.setDistanceMetres(figees.distance());
				d.setPrecisionMetres(figees.precision());
				d.setRayonMetres(figees.rayon());
				d.setModeQr(figees.modeQr().name());
				d.setDelaiDemandeMinutes(figees.delaiDemandeMinutes());
				d.setSignaux(figees.signaux());
			}
			demandeRepository.save(d);
		}
		return aReprendre.size();
	}
}
