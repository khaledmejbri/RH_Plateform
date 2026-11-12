package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.dto.PresenceCollaborateurDtos.JourneePresence;
import com.hr.presence.dto.PresenceCollaborateurDtos.PersonneResume;
import com.hr.presence.dto.PresenceCollaborateurDtos.PointageJournee;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceCollaborateur;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceEquipe;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresence;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresenceSite;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.service.TempsTravailCalcul.JourTravail;
import com.hr.presence.service.TempsTravailService.LigneAvecJour;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Présence d'un collaborateur sur 31 jours au plus (D-N01.b, D-N02, D-P19.g) : synthèse, journées avec pointages
 * (refusés compris, motif en clair), pointages manuels, heures sup comptées. Tout est calculé à la volée sur les
 * mêmes règles que la vue « Temps de travail » (D-P17) : mêmes lignes, mêmes totaux.
 * <p>
 * Aucune transaction englobante : les appels au référentiel (noms, droit de consulter) ne gardent pas de connexion
 * à la base ; chaque lecture du dépôt a sa propre transaction.
 */
@Service
public class PresenceCollaborateurService {

	/** Durée par défaut des fiches de présence, bornes comprises (D-P17.g). */
	static final int PERIODE_DEFAUT_JOURS = 31;
	private static final int MARGE_JOURS = 3;

	private final TempsTravailService tempsTravail;
	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final PresenceDemandeManuelleRepository demandeRepository;
	private final PresenceHeuresSupJourRepository heuresSupJourRepository;
	private final CalculHeuresSupService calculHeuresSup;
	private final PresenceDemandeManuelleService demandeManuelleService;
	private final FacialIdentiteService identiteService;
	private final ReferentielClient referentielClient;
	private final PresenceProperties properties;
	private final PresenceParametresService parametres;
	private final Clock clock;

	public PresenceCollaborateurService(
			TempsTravailService tempsTravail,
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceDemandeManuelleRepository demandeRepository,
			PresenceHeuresSupJourRepository heuresSupJourRepository,
			CalculHeuresSupService calculHeuresSup,
			PresenceDemandeManuelleService demandeManuelleService,
			FacialIdentiteService identiteService,
			ReferentielClient referentielClient,
			PresenceProperties properties,
			PresenceParametresService parametres,
			Clock clock) {
		this.tempsTravail = tempsTravail;
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.demandeRepository = demandeRepository;
		this.heuresSupJourRepository = heuresSupJourRepository;
		this.calculHeuresSup = calculHeuresSup;
		this.demandeManuelleService = demandeManuelleService;
		this.identiteService = identiteService;
		this.referentielClient = referentielClient;
		this.properties = properties;
		this.parametres = parametres;
		this.clock = clock;
	}

	/** Résultat interne : journées et lignes de la période. */
	private record Calcul(List<LigneAvecJour> lignes, List<JourneePresence> journees) {
	}

	// ---- Plateforme RH ------------------------------------------------------------------------------------------

	/**
	 * Synthèse de présence d'un collaborateur (C2). Période : 31 derniers jours par défaut, 31 au plus (400
	 * PERIODE_TROP_LONGUE), fin avant début 400 PERIODE_INVALIDE ; collaborateur inconnu du référentiel : 404.
	 */
	public SynthesePresence syntheseAdmin(UUID collaborateurId, LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		Affectation a = exigerCollaborateur(collaborateurId);
		return construire(a, periode, false).synthese();
	}

	/** Présence complète (onglets Pointages et Temps de travail de la modale / de la fiche collaborateur). */
	public PresenceCollaborateur presenceAdmin(UUID collaborateurId, LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		Affectation a = exigerCollaborateur(collaborateurId);
		PresenceEquipe p = construire(a, periode, true);
		return new PresenceCollaborateur(p.collaborateur(), p.synthese(), p.journees(), p.pointagesManuels());
	}

	/** Synthèse d'un site (fiche site, D-P19.g) : mêmes règles, tous collaborateurs confondus. */
	public SynthesePresenceSite syntheseSite(UUID siteId, LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		PresenceSite site = siteRepository.findById(siteId)
				.orElseThrow(() -> PresenceMetierException.notFound("SITE_INTROUVABLE", "Site introuvable"));
		List<LigneAvecJour> lignes = tempsTravail.calculerAvecJours(periode, null, siteId, null);
		Set<String> personnes = new HashSet<>();
		int retards = 0;
		long cumul = 0;
		int incompletes = 0;
		int enCours = 0;
		long minutes = 0;
		for (LigneAvecJour l : lignes) {
			personnes.add(l.detail().ligne().collaborateurId());
			minutes += l.detail().ligne().minutesTravaillees();
			Integer retard = l.detail().ligne().retardMinutes();
			if (retard != null && retard > 0) {
				retards++;
				cumul += retard;
			}
			if (l.detail().ligne().etat() == EtatJourHeuresSup.INCOMPLET) {
				incompletes++;
			} else if (l.detail().ligne().etat() == EtatJourHeuresSup.EN_COURS) {
				enCours++;
			}
		}
		return new SynthesePresenceSite(site.getId(), site.getCode(), site.getLibelle(), periode[0], periode[1],
				personnes.size(), lignes.size(), minutes, retards, cumul, incompletes, enCours);
	}

	// ---- Mobile : mes pointages ----------------------------------------------------------------------------------

	/**
	 * « Mes pointages » (C6) : journées de l'appelant, plus récente d'abord. Fiche lue au référentiel avec le JWT,
	 * 403 IDENTITE_INCOHERENTE si {@code X-Collaborateur-Id} diffère. Aucun pointage : liste vide.
	 */
	public List<JourneePresence> mesJournees(LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		return calculerMoi(periode).journees();
	}

	/**
	 * Synthèse de l'appelant (nombre de retards et cumul, D-P19.g) : même calcul que la synthèse de la Plateforme RH
	 * ({@link #construire}), l'identité venant du JWT (fiche du référentiel, compte du jeton). Les demandes de pointage
	 * manuel sont cherchées sous la fiche et sous le compte ; le matricule est lu au référentiel (nul si indisponible).
	 */
	public SynthesePresence maSynthese(LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		Identite moi = identiteService.resoudre();
		Affectation a = null;
		try {
			a = referentielClient.affectations(List.of(moi.fiche().collaborateurId())).stream()
					.filter(x -> moi.fiche().collaborateurId().equals(x.collaborateurId())).findFirst().orElse(null);
		} catch (PresenceMetierException ex) {
			// lecture dégradée : nom du JWT/fiche, matricule absent
		}
		if (a == null) {
			a = new Affectation(moi.fiche().collaborateurId(), moi.fiche().collaborateurCompteId(), null,
					moi.fiche().collaborateurNom(), null, null, null);
		}
		return construire(a, periode, false, identitesMoi(moi)).synthese();
	}

	private Calcul calculerMoi(LocalDate[] periode) {
		Identite moi = identiteService.resoudre();
		return calculer(identitesMoi(moi), periode);
	}

	private static Set<String> identitesMoi(Identite moi) {
		Set<String> identites = new LinkedHashSet<>();
		identites.add(moi.ficheId());
		if (moi.compteId() != null) {
			identites.add(moi.compteId());
		}
		if (moi.fiche().collaborateurCompteId() != null) {
			identites.add(moi.fiche().collaborateurCompteId().toString());
		}
		return identites;
	}

	// ---- Mobile : fiche d'équipe ---------------------------------------------------------------------------------

	/**
	 * Présence d'un membre de l'équipe (C7), seulement si l'appelant est son valideur D-H03. Ordre des contrôles : 400
	 * (période) puis identité (403 IDENTITE_INCOHERENTE) puis périmètre (403 HORS_PERIMETRE, collaborateur inconnu
	 * compris : pas d'énumération) puis données. Le droit est décidé par le référentiel à chaque appel, sans cache ;
	 * référentiel injoignable : 503 REFERENTIEL_INDISPONIBLE, aucune donnée.
	 */
	public PresenceEquipe equipe(UUID collaborateurId, LocalDate du, LocalDate au) {
		LocalDate[] periode = tempsTravail.periode(du, au, PERIODE_DEFAUT_JOURS);
		Identite moi = identiteService.resoudre();
		if (!referentielClient.estValideurDe(collaborateurId, moi.compteId())) {
			throw PresenceMetierException.forbidden("HORS_PERIMETRE", "Ce collaborateur ne fait pas partie de votre équipe");
		}
		Affectation a = referentielClient.affectations(List.of(collaborateurId)).stream()
				.filter(x -> collaborateurId.equals(x.collaborateurId())).findFirst().orElse(null);
		return construire(a != null ? a : new Affectation(collaborateurId, null, null, null, null, null, null), periode, true);
	}

	// ---- Calcul --------------------------------------------------------------------------------------------------

	private Affectation exigerCollaborateur(UUID collaborateurId) {
		return referentielClient.affectations(List.of(collaborateurId)).stream()
				.filter(x -> collaborateurId.equals(x.collaborateurId())).findFirst()
				.orElseThrow(() -> PresenceMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable"));
	}

	private PresenceEquipe construire(Affectation a, LocalDate[] periode, boolean avecDetails) {
		return construire(a, periode, avecDetails, Set.of());
	}

	/** {@code identitesSupplementaires} : identités connues par ailleurs (ex. compte du JWT de l'appelant). */
	private PresenceEquipe construire(Affectation a, LocalDate[] periode, boolean avecDetails,
									  Set<String> identitesSupplementaires) {
		Set<String> identites = new LinkedHashSet<>();
		identites.add(a.collaborateurId().toString());
		if (a.compteId() != null) {
			identites.add(a.compteId().toString());
		}
		identites.addAll(identitesSupplementaires);
		Calcul calcul = calculer(identites, periode);
		List<DemandeManuelleResponse> manuels = demandeManuelleService.pourIdentites(identites,
				periode[0].atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant(),
				periode[1].plusDays(1).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant());
		PersonneResume personne = new PersonneResume(a.collaborateurId().toString(), a.nom(), a.matricule());
		SynthesePresence synthese = synthese(personne, periode, calcul, manuels, identites);
		return new PresenceEquipe(personne, synthese, avecDetails ? calcul.journees() : List.of(),
				avecDetails ? manuels : List.of());
	}

	private SynthesePresence synthese(PersonneResume personne, LocalDate[] periode, Calcul calcul,
									  List<DemandeManuelleResponse> manuels, Set<String> identites) {
		int retards = 0;
		long cumul = 0;
		int incompletes = 0;
		int enCours = 0;
		long minutes = 0;
		Set<LocalDate> jours = new HashSet<>();
		for (LigneAvecJour l : calcul.lignes()) {
			jours.add(l.detail().ligne().jour());
			minutes += l.detail().ligne().minutesTravaillees();
			Integer retard = l.detail().ligne().retardMinutes();
			if (retard != null && retard > 0) {
				retards++;
				cumul += retard;
			}
			if (l.detail().ligne().etat() == EtatJourHeuresSup.INCOMPLET) {
				incompletes++;
			} else if (l.detail().ligne().etat() == EtatJourHeuresSup.EN_COURS) {
				enCours++;
			}
		}
		int valides = (int) manuels.stream().filter(m -> m.statut() == StatutDemandeManuelle.VALIDEE).count();
		int enAttente = (int) manuels.stream().filter(m -> m.statut() == StatutDemandeManuelle.EN_ATTENTE).count();
		return new SynthesePresence(personne.collaborateurId(), personne.nomComplet(), personne.matricule(),
				periode[0], periode[1], jours.size(), minutes, retards, cumul, incompletes, enCours, valides, enAttente,
				heuresSupMinutes(identites, periode));
	}

	/** Heures sup comptées (D-P14) des demandes validées, bénéficiaire désigné par fiche ou compte. */
	private long heuresSupMinutes(Set<String> identites, LocalDate[] periode) {
		List<HeuresComptees> comptees = calculHeuresSup.compter(heuresSupJourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(periode[0], periode[1], null, null, null, identites, null, null)));
		return comptees.stream().mapToLong(HeuresComptees::minutesComptees).sum();
	}

	/** Clé d'une journée sur un site. */
	private record CleJournee(LocalDate jour, UUID siteId) {
	}

	/** Journée en cours de construction. */
	private static final class Brouillon {
		int tempsMinutes;
		Integer retardMinutes;
		EtatJourHeuresSup etat;
		final List<PresencePointage> pointages = new ArrayList<>();
	}

	private Calcul calculer(Set<String> identites, LocalDate[] periode) {
		List<LigneAvecJour> lignes = tempsTravail.calculerAvecJours(periode, identites, null, null);

		Instant from = periode[0].minusDays(MARGE_JOURS).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();
		Instant to = periode[1].plusDays(MARGE_JOURS + 1L).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();
		List<PresencePointage> tous = pointageRepository.findAll(
				PresencePointageRepository.filtreTempsTravail(EnumSet.allOf(PointageStatut.class), from, to, null, identites),
				Sort.by(Sort.Direction.ASC, "serverTs"));
		List<PresencePointage> refuses = tous.stream()
				.filter(p -> !TempsTravailService.STATUTS_RETENUS.contains(p.getStatut()))
				.filter(p -> {
					LocalDate jour = TempsTravailCalcul.dateTunis(p.getServerTs());
					return !jour.isBefore(periode[0]) && !jour.isAfter(periode[1]);
				})
				.toList();

		Map<CleJournee, Brouillon> brouillons = new LinkedHashMap<>();
		for (LigneAvecJour l : lignes) {
			JourTravail j = l.jour();
			Brouillon b = brouillons.computeIfAbsent(new CleJournee(j.jour(), j.siteId()), k -> new Brouillon());
			b.tempsMinutes += l.detail().ligne().minutesTravaillees();
			b.etat = l.detail().ligne().etat();
			b.retardMinutes = l.detail().ligne().retardMinutes();
			j.paires().forEach(pa -> {
				b.pointages.add(pa.entree());
				b.pointages.add(pa.sortie());
			});
			b.pointages.addAll(j.orphelins());
		}
		for (PresencePointage p : refuses) {
			brouillons.computeIfAbsent(new CleJournee(TempsTravailCalcul.dateTunis(p.getServerTs()), p.getSiteId()),
					k -> new Brouillon()).pointages.add(p);
		}
		if (brouillons.isEmpty()) {
			return new Calcul(lignes, List.of());
		}

		Set<UUID> siteIds = brouillons.keySet().stream().map(CleJournee::siteId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<UUID, PresenceSite> sites = siteRepository.findAllById(siteIds).stream()
				.collect(Collectors.toMap(PresenceSite::getId, s -> s));
		Set<UUID> dejaDemandes = refuses.isEmpty() ? Set.of() : demandeRepository
				.findByPointageRejeteIdIn(refuses.stream().map(PresencePointage::getId).toList()).stream()
				.map(PresenceDemandeManuelle::getPointageRejeteId).collect(Collectors.toSet());
		Instant limiteAnciennete = clock.instant()
				.minus(java.time.Duration.ofDays(properties.getManuel().getAncienneteMaxJours()));

		List<JourneePresence> journees = new ArrayList<>();
		brouillons.forEach((cle, b) -> {
			PresenceSite site = cle.siteId() != null ? sites.get(cle.siteId()) : null;
			List<PointageJournee> pointages = b.pointages.stream()
					.sorted(Comparator.comparing(PresencePointage::getServerTs))
					.map(p -> versPointage(p, site, dejaDemandes, limiteAnciennete))
					.toList();
			journees.add(new JourneePresence(cle.jour(), cle.siteId(), site != null ? site.getLibelle() : null,
					b.tempsMinutes, b.retardMinutes, b.etat, b.etat != null ? b.etat.libelle() : null, pointages));
		});
		journees.sort(Comparator.comparing(JourneePresence::journee).reversed()
				.thenComparing(j -> j.siteLibelle() == null ? "" : j.siteLibelle()));
		return new Calcul(lignes, journees);
	}

	private PointageJournee versPointage(PresencePointage p, PresenceSite site, Set<UUID> dejaDemandes,
												Instant limiteAnciennete) {
		boolean refus = p.getStatut().name().startsWith("REJETE_");
		// D-P04 / D-P12.a : tout refus sauf « hors horaire », sans demande déjà faite, assez récent
		boolean eligible = refus && p.getStatut() != PointageStatut.REJETE_HORS_HORAIRE
				&& !dejaDemandes.contains(p.getId()) && p.getServerTs().isAfter(limiteAnciennete);
		return new PointageJournee(p.getId(), p.getType(), LibellesPresence.type(p.getType()), p.getStatut(),
				parametres.libelleStatutPointage(p.getStatut()), p.getServerTs(), TempsTravailCalcul.estManuel(p),
				LibellesPresence.motifRejet(p, site), eligible);
	}
}
