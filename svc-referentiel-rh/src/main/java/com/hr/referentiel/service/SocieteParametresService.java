package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.domain.BaseDecompteConge;
import com.hr.referentiel.domain.JourSemaine;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresJournalItem;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresUpdateRequest;
import com.hr.referentiel.entity.SocieteParametres;
import com.hr.referentiel.entity.SocieteParametresJournal;
import com.hr.referentiel.repository.SocieteParametresJournalRepository;
import com.hr.referentiel.repository.SocieteParametresRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Paramètres de fonctionnement d'une société (§ 4.2, D-SOC04 à D-SOC13, US-04).
 */
@Service
public class SocieteParametresService {

	private final SocieteParametresRepository repository;
	private final SocieteParametresJournalRepository journalRepository;
	private final ReglagesCongeService reglagesCongeService;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	@Autowired
	public SocieteParametresService(
			SocieteParametresRepository repository,
			SocieteParametresJournalRepository journalRepository,
			ReglagesCongeService reglagesCongeService,
			JsonMapper jsonMapper,
			Clock clock) {
		this.repository = repository;
		this.journalRepository = journalRepository;
		this.reglagesCongeService = reglagesCongeService;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional
	public SocieteParametresResponse obtenir() {
		SocieteParametres entity = obtenirOuCreerDefaut();
		return versResponse(entity, joursAcquisCourants());
	}

	@Transactional(readOnly = true)
	public BaseDecompteConge baseDecompteCongeCourante() {
		return repository.findFirstByOrderByIdAsc()
				.map(SocieteParametres::getBaseDecompteConge)
				.orElse(BaseDecompteConge.JOURS_TRAVAILLES);
	}

	@Transactional(readOnly = true)
	public Set<DayOfWeek> joursTravaillesCourants() {
		List<String> codes = repository.findFirstByOrderByIdAsc()
				.map(SocieteParametres::getJoursTravailles)
				.orElse(JourSemaine.DEFAUT_CODES);
		Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
		for (String code : codes) {
			jours.add(JourSemaine.parse(code).toDayOfWeek());
		}
		return jours;
	}

	@Transactional(readOnly = true)
	public int dureeMaxSortieMinutes() {
		return repository.findFirstByOrderByIdAsc()
				.map(SocieteParametres::getDureeMaxSortieMinutes)
				.orElse(120);
	}

	@Transactional
	public SocieteParametresResponse modifier(SocieteParametresUpdateRequest req, AuteurParametre auteur) {
		SocieteParametres entity = obtenirOuCreerDefaut();
		if (req.version() == null || !Objects.equals(req.version(), entity.getVersion())) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"Les paramètres société ont été modifiés entre-temps par un autre utilisateur.");
		}

		validerBornes(req.dureeHebdoMinutes(), req.dureeMaxSortieMinutes());
		List<String> joursNormalises = normaliserJours(req.joursTravailles());
		Map<String, Integer> dureesJour = validerDurees(joursNormalises, req.dureeHebdoMinutes(), req.dureeJourMinutes());
		LocalTime debut = parseHeure(req.heureDebutJournee(), "heure_debut_journee");
		LocalTime fin = parseHeure(req.heureFinJournee(), "heure_fin_journee");
		if (!debut.isBefore(fin)) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"L'heure de début de journée doit être antérieure à l'heure de fin.");
		}

		String avantJson = serialiserEtat(entity);
		Instant maintenant = Instant.now(clock);

		entity.setJoursTravailles(joursNormalises);
		entity.setDureeHebdoMinutes(req.dureeHebdoMinutes());
		entity.setDureeJourMinutes(dureesJour);
		entity.setHeureDebutJournee(debut.toString());
		entity.setHeureFinJournee(fin.toString());
		entity.setBaseDecompteConge(req.baseDecompteConge());
		entity.setDureeMaxSortieMinutes(req.dureeMaxSortieMinutes());
		entity.setDelegationAutoConges(req.delegationAutoConges());
		entity.setPublicationQuatreYeux(req.publicationQuatreYeux());
		entity.setModifieLe(maintenant);
		if (auteur != null) {
			entity.setModifieParCompteId(auteur.compteId());
			entity.setModifieParNom(auteur.nom());
		}

		SocieteParametres sauve = repository.saveAndFlush(entity);

		BigDecimal joursAcquis = joursAcquisCourants();
		if (req.joursAcquisParMois() != null && reglagesCongeService != null) {
			Long versionReglage = reglagesCongeService.reglages().get(0).version();
			joursAcquis = reglagesCongeService.modifier(
					ReglagesCongeService.CLE_JOURS_PAR_MOIS,
					req.joursAcquisParMois(),
					versionReglage != null ? versionReglage : 0L,
					auteur != null ? auteur : AuteurParametre.systeme()).valeur();
		}

		String apresJson = serialiserEtat(sauve);
		journalRepository.save(new SocieteParametresJournal(
				auteur != null ? auteur.compteId() : null,
				auteur != null ? auteur.nom() : AuteurParametre.NOM_SYSTEME,
				avantJson,
				apresJson,
				maintenant));

		return versResponse(sauve, joursAcquis);
	}

	@Transactional(readOnly = true)
	public Page<SocieteParametresJournalItem> journal(Pageable pageable) {
		return journalRepository.findAllByOrderByHorodatageDesc(pageable)
				.map(j -> new SocieteParametresJournalItem(
						j.getId(), j.getAuteurCompteId(), j.getAuteurNom(),
						j.getAvantJson(), j.getApresJson(), j.getHorodatage()));
	}

	public SocieteParametres obtenirOuCreerDefaut() {
		return repository.findFirstByOrderByIdAsc().orElseGet(() -> {
			SocieteParametres defaut = new SocieteParametres();
			Instant maintenant = Instant.now(clock);
			defaut.setCreeLe(maintenant);
			defaut.setModifieLe(maintenant);
			return repository.saveAndFlush(defaut);
		});
	}

	private BigDecimal joursAcquisCourants() {
		return reglagesCongeService != null ? reglagesCongeService.joursParMois() : ReglagesCongeService.DEFAUT;
	}

	private static List<String> normaliserJours(List<String> jours) {
		if (jours == null || jours.isEmpty()) {
			throw ReferentielMetierException.badRequest("REPARTITION_INCOHERENTE",
					"Au moins un jour travaillé doit être défini.");
		}
		Set<String> uniques = new LinkedHashSet<>();
		for (String j : jours) {
			try {
				uniques.add(JourSemaine.parse(j).name());
			} catch (IllegalArgumentException e) {
				throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Jour de semaine inconnu : " + j);
			}
		}
		return new ArrayList<>(uniques);
	}

	private static Map<String, Integer> validerDurees(
			List<String> joursTravailles, int dureeHebdoMinutes, Map<String, Integer> dureeJourMinutes) {
		if (dureeJourMinutes == null || dureeJourMinutes.isEmpty()) {
			throw ReferentielMetierException.badRequest("REPARTITION_INCOHERENTE",
					"La répartition des minutes par jour travaillé est obligatoire.");
		}
		Map<String, Integer> normalise = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : dureeJourMinutes.entrySet()) {
			String jour;
			try {
				jour = JourSemaine.parse(entry.getKey()).name();
			} catch (IllegalArgumentException e) {
				throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
						"Jour de semaine inconnu dans duree_jour_minutes : " + entry.getKey());
			}
			Integer minutes = entry.getValue();
			if (minutes == null || minutes < 0 || minutes > 720) {
				throw ReferentielMetierException.badRequest("REGLAGE_HORS_BORNES",
						"Durée journalière hors bornes (0 à 720 min) pour " + jour + ".");
			}
			if (!joursTravailles.contains(jour) && minutes > 0) {
				throw ReferentielMetierException.badRequest("REPARTITION_INCOHERENTE",
						"Le jour non travaillé " + jour + " ne peut pas avoir une durée positive.");
			}
			if (joursTravailles.contains(jour)) {
				normalise.put(jour, minutes);
			}
		}
		int somme = 0;
		for (String jour : joursTravailles) {
			Integer minutes = normalise.get(jour);
			if (minutes == null || minutes <= 0) {
				throw ReferentielMetierException.badRequest("REPARTITION_INCOHERENTE",
						"Chaque jour travaillé doit avoir une durée positive (" + jour + ").");
			}
			somme += minutes;
		}
		if (somme != dureeHebdoMinutes) {
			throw ReferentielMetierException.badRequest("REPARTITION_INCOHERENTE",
					"La somme des durées journalières (" + somme + " min) ne correspond pas à la durée hebdomadaire ("
							+ dureeHebdoMinutes + " min).");
		}
		return normalise;
	}

	private static void validerBornes(Integer dureeHebdoMinutes, Integer dureeMaxSortieMinutes) {
		if (dureeHebdoMinutes == null || dureeHebdoMinutes < 60 || dureeHebdoMinutes > 3600
				|| dureeHebdoMinutes % 30 != 0) {
			throw ReferentielMetierException.badRequest("REGLAGE_HORS_BORNES",
					"La durée hebdomadaire doit être comprise entre 60 et 3600 minutes par pas de 30.");
		}
		if (dureeMaxSortieMinutes == null || dureeMaxSortieMinutes < 30 || dureeMaxSortieMinutes > 480
				|| dureeMaxSortieMinutes % 15 != 0) {
			throw ReferentielMetierException.badRequest("REGLAGE_HORS_BORNES",
					"La durée maximale de sortie doit être comprise entre 30 et 480 minutes par pas de 15.");
		}
	}

	private static LocalTime parseHeure(String valeur, String champ) {
		try {
			return LocalTime.parse(valeur.trim());
		} catch (DateTimeParseException | NullPointerException e) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"Format d'heure invalide pour " + champ + " (attendu HH:mm).");
		}
	}

	private String serialiserEtat(SocieteParametres e) {
		Map<String, Object> etat = new LinkedHashMap<>();
		etat.put("jours_travailles", e.getJoursTravailles());
		etat.put("duree_hebdo_minutes", e.getDureeHebdoMinutes());
		etat.put("duree_jour_minutes", e.getDureeJourMinutes());
		etat.put("heure_debut_journee", e.getHeureDebutJournee());
		etat.put("heure_fin_journee", e.getHeureFinJournee());
		etat.put("base_decompte_conge", e.getBaseDecompteConge().name());
		etat.put("duree_max_sortie_minutes", e.getDureeMaxSortieMinutes());
		etat.put("delegation_auto_conges", e.isDelegationAutoConges());
		etat.put("publication_quatre_yeux", e.isPublicationQuatreYeux());
		return jsonMapper.writeValueAsString(etat);
	}

	private static SocieteParametresResponse versResponse(SocieteParametres e, BigDecimal joursAcquis) {
		return new SocieteParametresResponse(
				e.getId(),
				e.getSocieteId() != null ? e.getSocieteId() : SocieteContexte.courante(),
				e.getJoursTravailles(),
				e.getDureeHebdoMinutes(),
				e.getDureeJourMinutes(),
				e.getHeureDebutJournee(),
				e.getHeureFinJournee(),
				e.getBaseDecompteConge(),
				joursAcquis,
				e.getDureeMaxSortieMinutes(),
				e.isDelegationAutoConges(),
				e.isPublicationQuatreYeux(),
				e.getVersion(),
				e.getModifieLe(),
				e.getModifieParNom());
	}
}
