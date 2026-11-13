package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionJournalParametre;
import com.hr.referentiel.dto.ParametreDto.PageJournal;
import com.hr.referentiel.dto.ParametreDto.Reglage;
import com.hr.referentiel.entity.AcquisitionMois;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglage « Jours acquis par mois » du référentiel (D-S07, D-R06) : décimal de 0 à 5 par pas de 0,5, défaut 1,5,
 * journalisé. La date de début des acquisitions (D-S07.b) est en lecture seule.
 */
@Service
public class ReglagesCongeService {

	public static final String CLE_JOURS_PAR_MOIS = "jours_acquis_par_mois";
	public static final BigDecimal DEFAUT = new BigDecimal("1.5");
	public static final BigDecimal MIN = BigDecimal.ZERO;
	public static final BigDecimal MAX = new BigDecimal("5");
	public static final BigDecimal PAS = new BigDecimal("0.5");

	private final ReglagesCongesRepository repository;
	private final AcquisitionMoisRepository acquisitionMoisRepository;
	private final JournalParametres journal;
	private final Clock clock;

	public ReglagesCongeService(ReglagesCongesRepository repository,
			AcquisitionMoisRepository acquisitionMoisRepository, JournalParametres journal, Clock clock) {
		this.repository = repository;
		this.acquisitionMoisRepository = acquisitionMoisRepository;
		this.journal = journal;
		this.clock = clock;
	}

	/** Date de début des acquisitions : le 1er du mois qui suit la date donnée (D-S07.b). */
	public static LocalDate debutParDefaut(LocalDate aujourdhui) {
		return YearMonth.from(aujourdhui).plusMonths(1).atDay(1);
	}

	@Transactional(readOnly = true)
	public List<Reglage> reglages() {
		return List.of(vue(repository.findById(ParametresConges.ID_UNIQUE).orElse(null)));
	}

	/** Jours par mois courants (repli sur le défaut tant que la ligne n'existe pas). */
	@Transactional(readOnly = true)
	public BigDecimal joursParMois() {
		return repository.findById(ParametresConges.ID_UNIQUE)
				.map(ParametresConges::getJoursAcquisParMois).orElse(DEFAUT);
	}

	@Transactional(readOnly = true)
	public LocalDate debutAcquisitions() {
		return repository.findById(ParametresConges.ID_UNIQUE).map(ParametresConges::getDebutAcquisitionsLe).orElse(null);
	}

	@Transactional
	public Reglage modifier(String cle, Object valeurBrute, Long version, AuteurParametre auteur) {
		if (!CLE_JOURS_PAR_MOIS.equals(cle)) {
			throw ReferentielMetierException.notFound("REGLAGE_INCONNU", "Réglage inconnu : " + cle + ".");
		}
		BigDecimal valeur = lireDecimal(valeurBrute);
		if (valeur == null || valeur.compareTo(MIN) < 0 || valeur.compareTo(MAX) > 0
				|| valeur.multiply(new BigDecimal("2")).stripTrailingZeros().scale() > 0) {
			throw ReferentielMetierException.badRequest("REGLAGE_HORS_BORNES",
					"Jours acquis par mois : la valeur doit être comprise entre 0 et 5, par pas de 0,5.");
		}
		if (version == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La version du réglage est obligatoire.");
		}
		repository.creerSiAbsente(DEFAUT, debutParDefaut(LocalDate.now(clock.withZone(FormatJours.TUNIS))));
		ParametresConges p = repository.findById(ParametresConges.ID_UNIQUE).orElseThrow();
		if (p.getVersion() != version) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"Ce paramètre a été modifié entre-temps. Rechargez la page puis recommencez.");
		}
		BigDecimal avant = p.getJoursAcquisParMois();
		p.setJoursAcquisParMois(valeur.setScale(1));
		p.setModifieLe(clock.instant());
		p.setModifieParCompteId(auteur.compteId());
		ParametresConges enregistre = repository.saveAndFlush(p);
		Map<String, Object> av = new LinkedHashMap<>();
		av.put("valeur", avant);
		Map<String, Object> ap = new LinkedHashMap<>();
		ap.put("valeur", enregistre.getJoursAcquisParMois());
		journal.tracer(JournalParametres.REF_REGLAGE, CLE_JOURS_PAR_MOIS, ActionJournalParametre.REGLAGE, av, ap, auteur);
		return vue(enregistre);
	}

	/** Nombre JSON ou texte numérique (virgule tolérée) ; {@code null} si illisible. */
	static BigDecimal lireDecimal(Object brut) {
		if (brut == null) {
			return null;
		}
		try {
			return new BigDecimal(brut instanceof Number ? brut.toString() : String.valueOf(brut).strip().replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Transactional(readOnly = true)
	public PageJournal journal(int page, int taille) {
		return journal.lire(JournalParametres.REF_REGLAGE, page, taille);
	}

	/** P12 : date de début des acquisitions et jours figés du mois courant (null avant le premier passage). */
	@Transactional(readOnly = true)
	public InfoConges info() {
		YearMonth mois = YearMonth.now(clock.withZone(FormatJours.TUNIS));
		BigDecimal fige = acquisitionMoisRepository.findById(mois.toString())
				.map(AcquisitionMois::getJoursParMois).orElse(null);
		return new InfoConges(debutAcquisitions(), mois.toString(), fige);
	}

	public record InfoConges(
			@com.fasterxml.jackson.annotation.JsonProperty("debut_acquisitions_le") LocalDate debutAcquisitionsLe,
			@com.fasterxml.jackson.annotation.JsonProperty("mois_courant") String moisCourant,
			@com.fasterxml.jackson.annotation.JsonProperty("jours_mois_courant") BigDecimal joursMoisCourant) {
	}

	private static Reglage vue(ParametresConges p) {
		return new Reglage(CLE_JOURS_PAR_MOIS, "Jours acquis par mois",
				p != null ? p.getJoursAcquisParMois() : DEFAUT, MIN, MAX, PAS, "JOURS", DEFAUT,
				p != null ? p.getVersion() : 0L);
	}
}
