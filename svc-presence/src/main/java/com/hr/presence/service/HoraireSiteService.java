package com.hr.presence.service;

import com.hr.presence.domain.JourSemaine;
import com.hr.presence.dto.HoraireDtos.HoraireHistoriqueResponse;
import com.hr.presence.dto.HoraireDtos.HoraireSiteRequest;
import com.hr.presence.dto.HoraireDtos.HoraireSiteResponse;
import com.hr.presence.dto.HoraireDtos.HoraireSnapshot;
import com.hr.presence.dto.HoraireDtos.JourHoraireRequest;
import com.hr.presence.dto.HoraireDtos.JourHoraireResponse;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.entity.PresenceHoraireSite;
import com.hr.presence.entity.PresenceHoraireSiteHistorique;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHoraireSiteHistoriqueRepository;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Horaire hebdomadaire d'un site et tolérances (D-P11) : lecture, remplacement complet, retour à « sans horaire »,
 * historique avant / après (C-L-02). Le remplacement est atomique et ajuste les demandes d'heures sup concernées.
 */
@Service
public class HoraireSiteService {

	static final int TOLERANCE_MAX_MINUTES = 90;
	static final int TOLERANCE_PAS_MINUTES = 15;

	/** « HH:mm » strict : 24:00 ou 23:60 sont refusés (le mode souple les ramènerait à minuit). */
	private static final java.time.format.DateTimeFormatter HEURE_STRICTE = java.time.format.DateTimeFormatter
			.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final PresenceSiteRepository siteRepository;
	private final PresenceHoraireSiteRepository horaireRepository;
	private final PresenceHoraireSiteHistoriqueRepository historiqueRepository;
	private final HeuresSupAjustementHoraireService ajustementService;
	private final ActeurResolver acteurResolver;
	private final Clock clock;

	public HoraireSiteService(
			PresenceSiteRepository siteRepository,
			PresenceHoraireSiteRepository horaireRepository,
			PresenceHoraireSiteHistoriqueRepository historiqueRepository,
			HeuresSupAjustementHoraireService ajustementService,
			ActeurResolver acteurResolver,
			Clock clock) {
		this.siteRepository = siteRepository;
		this.horaireRepository = horaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.ajustementService = ajustementService;
		this.acteurResolver = acteurResolver;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public HoraireSiteResponse lire(UUID siteId) {
		PresenceSite site = exigerSite(siteId);
		List<PresenceHoraireSite> lignes = horaireRepository.findBySiteIdOrderByJourSemaine(siteId);
		var derniere = historiqueRepository.findFirstBySiteIdOrderByModifieLeDesc(siteId);
		return new HoraireSiteResponse(siteId, site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(),
				versJours(lignes), !lignes.isEmpty(),
				derniere.map(PresenceHoraireSiteHistorique::getModifieParNom).orElse(null),
				derniere.map(PresenceHoraireSiteHistorique::getModifieLe).orElse(null));
	}

	@Transactional
	public HoraireSiteResponse remplacer(UUID siteId, HoraireSiteRequest request) {
		// Ordre des erreurs : 400 de format, puis 404, puis 422 de règle métier.
		int toleranceAvant = exigerTolerance(request.toleranceAvantMinutes());
		int toleranceApres = exigerTolerance(request.toleranceApresMinutes());
		Map<DayOfWeek, Horaire> nouveaux = lireJours(request.jours());
		PresenceSite site = exigerSite(siteId);
		verifierRegles(nouveaux, toleranceAvant, toleranceApres);

		List<PresenceHoraireSite> existantes = horaireRepository.findBySiteIdOrderByJourSemaine(siteId);
		Map<DayOfWeek, Horaire> anciens = FenetreHoraireService.versHoraires(existantes);
		if (anciens.equals(nouveaux) && site.getToleranceAvantMinutes() == toleranceAvant
				&& site.getToleranceApresMinutes() == toleranceApres) {
			// Saisie identique à l'existant (D-P11.c) : ni historique, ni ajustement des demandes, réponse inchangée.
			return lire(siteId);
		}
		int ancienneToleranceAvant = site.getToleranceAvantMinutes();
		HoraireSnapshot avant = snapshot(site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(), existantes);

		Map<Integer, PresenceHoraireSite> parJour = new HashMap<>();
		existantes.forEach(l -> parJour.put(l.getJourSemaine(), l));
		for (Map.Entry<DayOfWeek, Horaire> e : nouveaux.entrySet()) {
			PresenceHoraireSite ligne = parJour.remove(e.getKey().getValue());
			if (ligne == null) {
				ligne = new PresenceHoraireSite();
				ligne.setSiteId(siteId);
				ligne.setJourSemaine(e.getKey().getValue());
			}
			ligne.setHeureDebut(e.getValue().debut());
			ligne.setHeureFin(e.getValue().fin());
			horaireRepository.save(ligne);
		}
		horaireRepository.deleteAll(parJour.values());

		site.setToleranceAvantMinutes(toleranceAvant);
		site.setToleranceApresMinutes(toleranceApres);
		siteRepository.save(site);

		Acteur auteur = acteurResolver.depuisJwt();
		HoraireSnapshot apres = new HoraireSnapshot(toleranceAvant, toleranceApres, versJours(nouveaux));
		tracer(siteId, auteur, avant, apres);
		ajustementService.appliquer(site, anciens, ancienneToleranceAvant, nouveaux, auteur);
		return lire(siteId);
	}

	/** Retour à « site sans horaire » (D-P11.b) : tracé, et les demandes d'heures sup sont ajustées. */
	@Transactional
	public void supprimer(UUID siteId) {
		PresenceSite site = exigerSite(siteId);
		List<PresenceHoraireSite> existantes = horaireRepository.findBySiteIdOrderByJourSemaine(siteId);
		if (existantes.isEmpty()) {
			// Le site est déjà sans horaire : ni historique, ni ajustement (comme une saisie identique).
			return;
		}
		Map<DayOfWeek, Horaire> anciens = FenetreHoraireService.versHoraires(existantes);
		HoraireSnapshot avant = snapshot(site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(), existantes);
		int ancienneToleranceAvant = site.getToleranceAvantMinutes();

		horaireRepository.deleteAll(existantes);
		Acteur auteur = acteurResolver.depuisJwt();
		tracer(siteId, auteur, avant, snapshot(site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(), List.of()));
		ajustementService.appliquer(site, anciens, ancienneToleranceAvant, new EnumMap<>(DayOfWeek.class), auteur);
	}

	@Transactional(readOnly = true)
	public PageResponse<HoraireHistoriqueResponse> historique(UUID siteId, int page, int size) {
		exigerSite(siteId);
		int taille = Math.clamp(size, 1, 50);
		Page<PresenceHoraireSiteHistorique> resultat = historiqueRepository.findBySiteIdOrderByModifieLeDesc(
				siteId, PageRequest.of(Math.max(page, 0), taille));
		List<HoraireHistoriqueResponse> contenu = resultat.getContent().stream()
				.map(h -> new HoraireHistoriqueResponse(h.getModifieParNom(), h.getModifieLe(),
						lireSnapshot(h.getAvantJson()), lireSnapshot(h.getApresJson())))
				.toList();
		return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
				resultat.getTotalElements(), resultat.getTotalPages());
	}

	// ---- Validation de la saisie -------------------------------------------------------------------------------

	static int exigerTolerance(Integer valeur) {
		if (valeur == null) {
			return PresenceSite.TOLERANCE_DEFAUT_MINUTES;
		}
		if (valeur < 0 || valeur > TOLERANCE_MAX_MINUTES || valeur % TOLERANCE_PAS_MINUTES != 0) {
			throw PresenceMetierException.badRequest("TOLERANCE_INVALIDE",
					"La tolérance doit être comprise entre 0 et " + TOLERANCE_MAX_MINUTES + " minutes, par pas de "
							+ TOLERANCE_PAS_MINUTES);
		}
		return valeur;
	}

	/** Lecture de la saisie : uniquement des erreurs 400 (format) ; les règles métier sont dans {@link #verifierRegles}. */
	static Map<DayOfWeek, Horaire> lireJours(List<JourHoraireRequest> jours) {
		if (jours == null || jours.isEmpty()) {
			throw PresenceMetierException.badRequest("HORAIRE_FORMAT_INVALIDE",
					"Au moins un jour est attendu ; pour retirer l'horaire du site, utilisez la suppression");
		}
		Map<DayOfWeek, Horaire> out = new EnumMap<>(DayOfWeek.class);
		for (JourHoraireRequest j : jours) {
			if (j == null) {
				throw PresenceMetierException.badRequest("HORAIRE_FORMAT_INVALIDE", "Jour manquant");
			}
			JourSemaine jour = JourSemaine.parse(j.jour()).orElseThrow(() -> PresenceMetierException.badRequest(
					"HORAIRE_FORMAT_INVALIDE", "Jour inconnu : " + j.jour()));
			if (estVide(j.debut()) || estVide(j.fin())) {
				throw PresenceMetierException.badRequest("HORAIRE_INCOMPLET",
						"L'heure de début et l'heure de fin sont toutes deux obligatoires (" + jour.name() + ")");
			}
			if (out.put(jour.jour(), new Horaire(heure(j.debut()), heure(j.fin()))) != null) {
				throw PresenceMetierException.badRequest("HORAIRE_FORMAT_INVALIDE", "Jour en double : " + jour.name());
			}
		}
		return out;
	}

	/** Règles métier de l'horaire : début = fin (422 HORAIRE_INVALIDE), puis chevauchement des fenêtres (422). */
	private static void verifierRegles(Map<DayOfWeek, Horaire> jours, int toleranceAvant, int toleranceApres) {
		for (Map.Entry<DayOfWeek, Horaire> e : jours.entrySet()) {
			if (e.getValue().debut().equals(e.getValue().fin())) {
				throw PresenceMetierException.unprocessable("HORAIRE_INVALIDE",
						"L'heure de début et l'heure de fin ne peuvent pas être identiques ("
								+ JourSemaine.de(e.getKey()).name() + ")");
			}
		}
		FenetreHoraireService.verifierSemaineSansChevauchement(jours, toleranceAvant, toleranceApres);
	}

	private static boolean estVide(String valeur) {
		return valeur == null || valeur.isBlank();
	}

	private static LocalTime heure(String brut) {
		try {
			return LocalTime.parse(brut == null ? "" : brut.trim(), HEURE_STRICTE);
		} catch (DateTimeParseException ex) {
			throw PresenceMetierException.badRequest("HORAIRE_FORMAT_INVALIDE",
					"Heure illisible (format HH:mm attendu) : " + brut);
		}
	}

	// ---- Conversions et trace ----------------------------------------------------------------------------------

	private static List<JourHoraireResponse> versJours(List<PresenceHoraireSite> lignes) {
		return versJours(FenetreHoraireService.versHoraires(lignes));
	}

	private static List<JourHoraireResponse> versJours(Map<DayOfWeek, Horaire> horaires) {
		return horaires.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> new JourHoraireResponse(JourSemaine.de(e.getKey()).name(),
						FenetreHoraireService.HEURE_MINUTE.format(e.getValue().debut()),
						FenetreHoraireService.HEURE_MINUTE.format(e.getValue().fin()),
						e.getValue().deNuit()))
				.toList();
	}

	private static HoraireSnapshot snapshot(int toleranceAvant, int toleranceApres, List<PresenceHoraireSite> lignes) {
		return new HoraireSnapshot(toleranceAvant, toleranceApres, versJours(lignes));
	}

	private void tracer(UUID siteId, Acteur auteur, HoraireSnapshot avant, HoraireSnapshot apres) {
		PresenceHoraireSiteHistorique h = new PresenceHoraireSiteHistorique();
		h.setSiteId(siteId);
		h.setModifieParCompteId(auteur.compteId());
		h.setModifieParNom(auteur.login() != null ? auteur.login() : auteur.libelle());
		h.setModifieLe(clock.instant());
		h.setAvantJson(JSON.writeValueAsString(avant));
		h.setApresJson(JSON.writeValueAsString(apres));
		historiqueRepository.save(h);
	}

	private static HoraireSnapshot lireSnapshot(String json) {
		if (json == null) {
			return null;
		}
		return JSON.readValue(json, HoraireSnapshot.class);
	}

	private PresenceSite exigerSite(UUID siteId) {
		return siteRepository.findById(siteId)
				.orElseThrow(() -> PresenceMetierException.notFound("SITE_INTROUVABLE", "Site introuvable"));
	}
}
