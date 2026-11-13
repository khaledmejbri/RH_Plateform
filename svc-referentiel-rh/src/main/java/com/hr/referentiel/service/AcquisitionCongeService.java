package com.hr.referentiel.service;

import com.hr.referentiel.repository.CollaborateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Acquisition mensuelle des jours de congé (D-S07, D-S07.a à d, D-S10, D-S13) : +N le 1er du mois (heure de Tunis) pour
 * chaque fiche active existant ce jour-là, une seule fois par (fiche, mois) grâce à l'unicité en base. Idempotente :
 * repassages quotidiens et passe au démarrage rattrapent le mois en cours seulement. Auteur « Système (automatique) »,
 * aucune notification. Non transactionnelle : chaque fiche a sa propre transaction.
 */
@Service
public class AcquisitionCongeService {

	private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeService.class);

	/** Bilan d'un passage (X1). */
	public record Bilan(
			@com.fasterxml.jackson.annotation.JsonProperty("mois") String mois,
			@com.fasterxml.jackson.annotation.JsonProperty("jours_par_mois") BigDecimal joursParMois,
			@com.fasterxml.jackson.annotation.JsonProperty("eligibles") int eligibles,
			@com.fasterxml.jackson.annotation.JsonProperty("creees") int creees,
			@com.fasterxml.jackson.annotation.JsonProperty("deja_faites") int dejaFaites,
			@com.fasterxml.jackson.annotation.JsonProperty("ignore") boolean ignore) {
	}

	private final AcquisitionFicheService ficheService;
	private final ReglagesCongeService reglages;
	private final CollaborateurRepository collaborateurRepository;
	private final Clock clock;

	public AcquisitionCongeService(AcquisitionFicheService ficheService, ReglagesCongeService reglages,
			CollaborateurRepository collaborateurRepository, Clock clock) {
		this.ficheService = ficheService;
		this.reglages = reglages;
		this.collaborateurRepository = collaborateurRepository;
		this.clock = clock;
	}

	/** Mois courant à Tunis. */
	public YearMonth moisCourant() {
		return YearMonth.now(clock.withZone(FormatJours.TUNIS));
	}

	public Bilan traiter(YearMonth mois) {
		String cle = mois.toString();
		LocalDate debut = reglages.debutAcquisitions();
		if (debut != null && mois.atDay(1).isBefore(debut)) {
			log.info("Acquisition {} ignorée : avant le début des acquisitions ({})", cle, debut);
			return new Bilan(cle, null, 0, 0, 0, true);
		}
		BigDecimal jours = ficheService.figerJoursDuMois(cle);
		if (jours.signum() == 0) {
			log.info("Acquisition {} : N = 0, rien ce mois-ci", cle);
			return new Bilan(cle, jours, 0, 0, 0, true);
		}
		LocalDate premier = mois.atDay(1);
		List<UUID> eligibles = collaborateurRepository.idsEligiblesAcquisition(
				premier.plusDays(1).atStartOfDay(FormatJours.TUNIS).toInstant(), premier);
		int creees = 0;
		int dejaFaites = 0;
		for (UUID id : eligibles) {
			try {
				if (ficheService.acquerir(id, cle, jours)) {
					creees++;
				} else {
					dejaFaites++;
				}
			} catch (DataIntegrityViolationException e) {
				dejaFaites++;
				log.debug("Acquisition {} déjà faite pour une fiche (collision d'unicité)", cle);
			} catch (RuntimeException e) {
				log.error("Acquisition {} en échec pour une fiche : {}", cle, e.getMessage());
			}
		}
		log.info("Acquisition {} : {} éligible(s), {} créée(s), {} déjà faite(s), N = {}", cle, eligibles.size(),
				creees, dejaFaites, jours);
		return new Bilan(cle, jours, eligibles.size(), creees, dejaFaites, false);
	}
}
