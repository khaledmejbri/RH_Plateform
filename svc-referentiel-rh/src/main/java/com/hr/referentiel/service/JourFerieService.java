package com.hr.referentiel.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hr.referentiel.domain.ActionJournalParametre;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieActif;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieRequest;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieResponse;
import com.hr.referentiel.dto.ParametreDto.PageJournal;
import com.hr.referentiel.entity.JourFerie;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jours fériés (D-S02, D-S15.e) : date unique (actifs et inactifs), libellé 2 à 80 non unique, année de N-1 à N+5,
 * suppression libre. Les dates actives servent au décompte des jours ouvrés (cache Caffeine invalidé après commit).
 */
@Service
public class JourFerieService {

	public static final int LIBELLE_MIN = 2;
	public static final int LIBELLE_MAX = 80;
	static final int ANNEES_AVANT = 1;
	static final int ANNEES_APRES = 5;
	private static final String CLE = "dates";

	private final JourFerieRepository repository;
	private final JournalParametres journal;
	private final Clock clock;
	private final Cache<String, Set<LocalDate>> cache;

	public JourFerieService(JourFerieRepository repository, JournalParametres journal, Clock clock,
			@Value("${app.parametres.cache-ttl-secondes:30}") long ttlSecondes) {
		this.repository = repository;
		this.journal = journal;
		this.clock = clock;
		this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(ttlSecondes)).maximumSize(2).build();
	}

	/** Dates des jours fériés actifs (instantané en cache, au plus {@code ttl} de retard sur une autre instance). */
	@Transactional(readOnly = true)
	public Set<LocalDate> datesActives() {
		return cache.get(CLE, k -> Set.copyOf(repository.datesActives()));
	}

	@Transactional(readOnly = true)
	public List<JourFerieResponse> lister(Integer annee) {
		List<JourFerie> lignes = annee == null
				? repository.findAllByOrderByDateJourAsc()
				: repository.findByDateJourBetweenOrderByDateJourAsc(LocalDate.of(anneeValide(annee), 1, 1),
						LocalDate.of(anneeValide(annee), 12, 31));
		return lignes.stream().map(JourFerieService::vers).toList();
	}

	@Transactional(readOnly = true)
	public List<JourFerieActif> actifs(Integer annee) {
		List<JourFerie> lignes = annee == null
				? repository.findAllByOrderByDateJourAsc().stream().filter(JourFerie::isActif).toList()
				: repository.findByActifTrueAndDateJourBetweenOrderByDateJourAsc(LocalDate.of(anneeValide(annee), 1, 1),
						LocalDate.of(anneeValide(annee), 12, 31));
		return lignes.stream().map(j -> new JourFerieActif(j.getDateJour(), j.getLibelle())).toList();
	}

	@Transactional(readOnly = true)
	public PageJournal journal(int page, int taille) {
		return journal.lire(JournalParametres.REF_JOUR_FERIE, page, taille);
	}

	@Transactional
	public JourFerieResponse creer(JourFerieRequest req, AuteurParametre auteur) {
		LocalDate date = dateValide(req == null ? null : req.date());
		String libelle = libelleValide(req == null ? null : req.libelle());
		if (repository.findByDateJour(date).isPresent()) {
			throw doublon();
		}
		JourFerie j = new JourFerie();
		j.setDateJour(date);
		j.setLibelle(libelle);
		j.setActif(true);
		j.setCreeLe(clock.instant());
		j.setModifieLe(clock.instant());
		j.setModifieParCompteId(auteur.compteId());
		JourFerie enregistre = repository.saveAndFlush(j);
		journal.tracer(JournalParametres.REF_JOUR_FERIE, date.toString(), ActionJournalParametre.CREATION, null,
				etat(enregistre), auteur);
		invaliderApresCommit();
		return vers(enregistre);
	}

	@Transactional
	public JourFerieResponse modifier(UUID id, JourFerieRequest req, AuteurParametre auteur) {
		JourFerie j = charger(id);
		verifierVersion(j, req == null ? null : req.version());
		Map<String, Object> avant = etat(j);
		if (req.date() != null) {
			LocalDate date = dateValide(req.date());
			if (!date.equals(j.getDateJour()) && repository.findByDateJour(date).isPresent()) {
				throw doublon();
			}
			j.setDateJour(date);
		}
		if (req.libelle() != null) {
			j.setLibelle(libelleValide(req.libelle()));
		}
		return enregistrerEtTracer(j, avant, ActionJournalParametre.MODIFICATION, auteur);
	}

	@Transactional
	public JourFerieResponse desactiver(UUID id, Long version, AuteurParametre auteur) {
		return changerEtat(id, version, false, ActionJournalParametre.DESACTIVATION, auteur);
	}

	@Transactional
	public JourFerieResponse reactiver(UUID id, Long version, AuteurParametre auteur) {
		return changerEtat(id, version, true, ActionJournalParametre.REACTIVATION, auteur);
	}

	/** Suppression libre (D-S15.e, pas de 409 « utilisé ») : une demande approuvée garde ses jours (D-S01.c). */
	@Transactional
	public void supprimer(UUID id, Long version, AuteurParametre auteur) {
		JourFerie j = charger(id);
		verifierVersion(j, version);
		Map<String, Object> avant = etat(j);
		repository.delete(j);
		repository.flush();
		journal.tracer(JournalParametres.REF_JOUR_FERIE, j.getDateJour().toString(), ActionJournalParametre.SUPPRESSION,
				avant, null, auteur);
		invaliderApresCommit();
	}

	// ─── Privé ────────────────────────────────────────────────────────────────

	private JourFerieResponse changerEtat(UUID id, Long version, boolean actif, ActionJournalParametre action,
			AuteurParametre auteur) {
		JourFerie j = charger(id);
		verifierVersion(j, version);
		Map<String, Object> avant = etat(j);
		j.setActif(actif);
		return enregistrerEtTracer(j, avant, action, auteur);
	}

	private JourFerieResponse enregistrerEtTracer(JourFerie j, Map<String, Object> avant,
			ActionJournalParametre action, AuteurParametre auteur) {
		j.setModifieLe(clock.instant());
		j.setModifieParCompteId(auteur.compteId());
		JourFerie enregistre = repository.saveAndFlush(j);
		journal.tracer(JournalParametres.REF_JOUR_FERIE, enregistre.getDateJour().toString(), action, avant,
				etat(enregistre), auteur);
		invaliderApresCommit();
		return vers(enregistre);
	}

	private JourFerie charger(UUID id) {
		return repository.findById(id).orElseThrow(() -> ReferentielMetierException.notFound(
				"JOUR_FERIE_INTROUVABLE", "Jour férié introuvable."));
	}

	private static void verifierVersion(JourFerie j, Long version) {
		if (version == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La version du jour férié est obligatoire.");
		}
		if (version != j.getVersion()) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"Ce paramètre a été modifié entre-temps. Rechargez la liste puis recommencez.");
		}
	}

	private LocalDate dateValide(LocalDate date) {
		if (date == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La date du jour férié est obligatoire.");
		}
		anneeValide(date.getYear());
		return date;
	}

	private int anneeValide(int annee) {
		int courante = LocalDate.now(clock.withZone(FormatJours.TUNIS)).getYear();
		if (annee < courante - ANNEES_AVANT || annee > courante + ANNEES_APRES) {
			throw ReferentielMetierException.badRequest("DATE_INVALIDE", "L'année doit être comprise entre "
					+ (courante - ANNEES_AVANT) + " et " + (courante + ANNEES_APRES) + ".");
		}
		return annee;
	}

	private static String libelleValide(String brut) {
		String l = brut == null ? "" : brut.trim();
		if (l.length() < LIBELLE_MIN || l.length() > LIBELLE_MAX) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"Le libellé doit comporter entre " + LIBELLE_MIN + " et " + LIBELLE_MAX + " caractères.");
		}
		return l;
	}

	private static ReferentielMetierException doublon() {
		return ReferentielMetierException.conflict("JOUR_FERIE_EN_DOUBLE", "Ce jour férié existe déjà.");
	}

	private static Map<String, Object> etat(JourFerie j) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("date", j.getDateJour().toString());
		m.put("libelle", j.getLibelle());
		m.put("actif", j.isActif());
		return m;
	}

	private static JourFerieResponse vers(JourFerie j) {
		return new JourFerieResponse(j.getId(), j.getDateJour(), j.getLibelle(), j.isActif(), j.getVersion());
	}

	private void invaliderApresCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cache.invalidateAll();
				}
			});
		} else {
			cache.invalidateAll();
		}
	}
}
