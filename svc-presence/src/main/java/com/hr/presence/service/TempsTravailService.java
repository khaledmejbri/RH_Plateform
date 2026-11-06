package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.dto.TempsTravailDtos.LigneTempsTravail;
import com.hr.presence.dto.TempsTravailDtos.OrphelinDto;
import com.hr.presence.dto.TempsTravailDtos.PageLignes;
import com.hr.presence.dto.TempsTravailDtos.PaireDto;
import com.hr.presence.dto.TempsTravailDtos.SyntheseCollaborateur;
import com.hr.presence.dto.TempsTravailDtos.TempsTravailResponse;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.CalculHeuresSupService.Paire;
import com.hr.presence.service.TempsTravailCalcul.JourTravail;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
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
 * Temps de travail par collaborateur × journée × site (D-P17), calculé à la volée sans rien écrire en base.
 * Les pointages d'un même collaborateur sont fusionnés qu'ils portent l'identifiant de la fiche ou du compte
 * (comme {@link CalculHeuresSupService}) ; noms et affectations viennent du référentiel, par lot, et la liste reste
 * servie sans nom si le référentiel est indisponible (sauf pour le filtre d'unité, qui en dépend : 503).
 */
@Service
public class TempsTravailService {

	private static final Logger log = LoggerFactory.getLogger(TempsTravailService.class);

	/** Période par défaut quand « du » est absent : 7 derniers jours, aujourd'hui compris (D-P17.g). */
	static final int PERIODE_DEFAUT_JOURS = 7;
	static final int TAILLE_PAGE_MAX = 200;
	/** Marge autour de la période sur l'heure serveur : poste de nuit et rattachement après minuit. */
	private static final int MARGE_JOURS = 3;
	/** D-P17.a : seuls ces statuts sont retenus ; tous les REJETE_* sont exclus, comme les demandes manuelles en attente. */
	static final Set<PointageStatut> STATUTS_RETENUS = EnumSet.of(PointageStatut.VALIDE, PointageStatut.VALIDE_MANUEL);
	private static final DateTimeFormatter ISO_DECALAGE = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	/** Ligne calculée avec l'affectation du collaborateur (colonnes d'unité de l'export CSV). */
	public record LigneDetaillee(LigneTempsTravail ligne, String direction, String departement, String service,
								 boolean manuel) {
	}

	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final PresenceHeuresSupJourRepository heuresSupJourRepository;
	private final PresenceHeuresSupDemandeRepository demandeRepository;
	private final ReferentielClient referentielClient;
	private final PresenceProperties properties;
	private final Clock clock;

	public TempsTravailService(
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceHeuresSupJourRepository heuresSupJourRepository,
			PresenceHeuresSupDemandeRepository demandeRepository,
			ReferentielClient referentielClient,
			PresenceProperties properties,
			Clock clock) {
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.heuresSupJourRepository = heuresSupJourRepository;
		this.demandeRepository = demandeRepository;
		this.referentielClient = referentielClient;
		this.properties = properties;
		this.clock = clock;
	}

	// ---- Vue -----------------------------------------------------------------------------------------------------

	/** Page de lignes ; totaux et synthèse portent sur tout le résultat filtré (D-P17.h). */
	@Transactional(readOnly = true)
	public TempsTravailResponse consulter(LocalDate du, LocalDate au, UUID collaborateurId, UUID siteId, UUID uniteId,
										  EtatJourHeuresSup etat, int page, int size) {
		List<LigneDetaillee> toutes = calculer(du, au, collaborateurId, siteId, uniteId, etat);
		int taille = Math.clamp(size, 1, TAILLE_PAGE_MAX);
		int numero = Math.max(page, 0);
		int debut = (int) Math.min((long) numero * taille, toutes.size());
		List<LigneTempsTravail> contenu = toutes.subList(debut, Math.min(debut + taille, toutes.size())).stream()
				.map(LigneDetaillee::ligne).toList();
		int totalPages = (toutes.size() + taille - 1) / taille;

		long totalMinutes = toutes.stream().mapToLong(l -> l.ligne().minutesTravaillees()).sum();
		int incompletes = (int) toutes.stream().filter(l -> l.ligne().etat() == EtatJourHeuresSup.INCOMPLET).count();
		int enCours = (int) toutes.stream().filter(l -> l.ligne().etat() == EtatJourHeuresSup.EN_COURS).count();
		return new TempsTravailResponse(
				new PageLignes(contenu, toutes.size(), totalPages, numero, taille),
				totalMinutes, toutes.size(), incompletes, enCours, synthese(toutes));
	}

	/** Toutes les lignes filtrées, dans l'ordre de la vue (export CSV). */
	@Transactional(readOnly = true)
	public List<LigneDetaillee> exporter(LocalDate du, LocalDate au, UUID collaborateurId, UUID siteId, UUID uniteId,
										 EtatJourHeuresSup etat) {
		return calculer(du, au, collaborateurId, siteId, uniteId, etat);
	}

	private static List<SyntheseCollaborateur> synthese(List<LigneDetaillee> lignes) {
		Map<String, long[]> cumuls = new LinkedHashMap<>();
		Map<String, LigneTempsTravail> modeles = new HashMap<>();
		for (LigneDetaillee l : lignes) {
			String id = l.ligne().collaborateurId();
			long[] c = cumuls.computeIfAbsent(id, k -> new long[3]);
			c[0]++;
			c[1] += l.ligne().minutesTravaillees();
			if (l.ligne().etat() == EtatJourHeuresSup.INCOMPLET) {
				c[2]++;
			}
			modeles.putIfAbsent(id, l.ligne());
		}
		return cumuls.entrySet().stream()
				.map(e -> {
					LigneTempsTravail m = modeles.get(e.getKey());
					return new SyntheseCollaborateur(e.getKey(), m.collaborateurNom(), m.collaborateurMatricule(),
							(int) e.getValue()[0], e.getValue()[1], (int) e.getValue()[2]);
				})
				.sorted(Comparator.comparing((SyntheseCollaborateur s) -> s.collaborateurNom() == null ? "￿" : s.collaborateurNom().toLowerCase())
						.thenComparing(SyntheseCollaborateur::collaborateurId))
				.toList();
	}

	// ---- Période -------------------------------------------------------------------------------------------------

	/** Période par défaut : 7 derniers jours ; fin avant début 400 ; au-delà du plafond 400 (D-P17.g). */
	LocalDate[] periode(LocalDate du, LocalDate au) {
		LocalDate aujourdhui = clock.instant().atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		LocalDate fin = au != null ? au : aujourdhui;
		LocalDate debut = du != null ? du : fin.minusDays(PERIODE_DEFAUT_JOURS - 1L);
		if (fin.isBefore(debut)) {
			throw PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début");
		}
		int max = properties.getTempsTravail().getPeriodeMaxJours();
		if (ChronoUnit.DAYS.between(debut, fin) + 1 > max) {
			throw PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + max + " jours");
		}
		return new LocalDate[] {debut, fin};
	}

	// ---- Calcul --------------------------------------------------------------------------------------------------

	private List<LigneDetaillee> calculer(LocalDate du, LocalDate au, UUID collaborateurId, UUID siteId, UUID uniteId,
										  EtatJourHeuresSup etatFiltre) {
		LocalDate[] periode = periode(du, au);
		Set<String> identites = identitesFiltrees(collaborateurId, uniteId);
		if (identites != null && identites.isEmpty()) {
			return List.of();
		}
		Instant from = periode[0].minusDays(MARGE_JOURS).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();
		Instant to = periode[1].plusDays(MARGE_JOURS + 1L).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();
		List<PresencePointage> pointages = pointageRepository.findAll(
				PresencePointageRepository.filtreTempsTravail(STATUTS_RETENUS, from, to, siteId, identites),
				Sort.by(Sort.Direction.ASC, "serverTs"));
		if (pointages.isEmpty()) {
			return List.of();
		}

		Map<String, Affectation> affectations = resoudreAffectations(pointages);
		Map<String, Affectation> parIdentifiant = new HashMap<>();
		affectations.values().forEach(a -> {
			parIdentifiant.put(a.collaborateurId().toString(), a);
			if (a.compteId() != null) {
				parIdentifiant.put(a.compteId().toString(), a);
			}
		});
		java.util.function.Function<String, String> cle = id -> {
			Affectation a = parIdentifiant.get(id);
			return a != null ? a.collaborateurId().toString() : id;
		};

		int dureeMax = properties.getTempsTravail().getDureeMaxSansHoraireHeures();
		List<JourTravail> jours = TempsTravailCalcul.regrouper(pointages, cle, dureeMax).stream()
				.filter(j -> !j.jour().isBefore(periode[0]) && !j.jour().isAfter(periode[1]))
				.toList();
		if (jours.isEmpty()) {
			return List.of();
		}

		Map<UUID, PresenceSite> sites = siteRepository.findAllById(
				jours.stream().map(JourTravail::siteId).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceSite::getId, s -> s));
		Map<String, Integer> ouvertures = ouverturesApres(jours, parIdentifiant, cle);
		Instant maintenant = clock.instant();

		List<LigneDetaillee> lignes = new ArrayList<>();
		for (JourTravail j : jours) {
			PresenceSite site = j.siteId() != null ? sites.get(j.siteId()) : null;
			int tolerance = site != null ? site.getToleranceApresMinutes() : PresenceSite.TOLERANCE_DEFAUT_MINUTES;
			int ouverture = ouvertures.getOrDefault(cleOuverture(j.collaborateur(), j.siteId(), j.jour()), 0);
			EtatJourHeuresSup etat = TempsTravailCalcul.etat(j, maintenant, tolerance, ouverture, dureeMax);
			if (etatFiltre != null && etat != etatFiltre) {
				continue;
			}
			lignes.add(versLigne(j, etat, site, parIdentifiant.get(j.collaborateur())));
		}
		lignes.sort(Comparator.comparing((LigneDetaillee l) -> l.ligne().jour()).reversed()
				.thenComparing(l -> l.ligne().collaborateurNom() == null ? "￿" : l.ligne().collaborateurNom().toLowerCase())
				.thenComparing(l -> l.ligne().collaborateurId())
				.thenComparing(l -> l.ligne().siteLibelle() == null ? "" : l.ligne().siteLibelle()));
		return lignes;
	}

	/**
	 * Identités (fiche et compte, en texte) auxquelles restreindre les pointages ; {@code null} sans filtre.
	 * Le filtre d'unité exige le référentiel (503 s'il est injoignable) ; celui de collaborateur s'élargit au compte
	 * quand le référentiel répond, sinon il se limite à l'identifiant fourni.
	 */
	private Set<String> identitesFiltrees(UUID collaborateurId, UUID uniteId) {
		Set<String> identites = null;
		if (uniteId != null) {
			identites = new LinkedHashSet<>();
			for (ReferentielClient.MembreUnite m : referentielClient.membresDeUnite(uniteId)) {
				identites.add(m.collaborateurId().toString());
				if (m.compteId() != null) {
					identites.add(m.compteId().toString());
				}
			}
		}
		if (collaborateurId != null) {
			Set<String> collab = new LinkedHashSet<>();
			collab.add(collaborateurId.toString());
			try {
				for (Affectation a : referentielClient.affectations(List.of(collaborateurId))) {
					collab.add(a.collaborateurId().toString());
					if (a.compteId() != null) {
						collab.add(a.compteId().toString());
					}
				}
			} catch (PresenceMetierException ex) {
				log.warn("Filtre collaborateur sans élargissement au compte : {}", ex.getMessage());
			}
			if (identites == null) {
				identites = collab;
			} else {
				identites.retainAll(collab);
			}
		}
		return identites;
	}

	/** Affectations par identifiant brut des pointages (fiche ou compte) ; carte vide si le référentiel échoue. */
	private Map<String, Affectation> resoudreAffectations(List<PresencePointage> pointages) {
		List<UUID> ids = pointages.stream().map(PresencePointage::getCollaborateurId).distinct()
				.map(TempsTravailService::versUuid).filter(Objects::nonNull).toList();
		Map<String, Affectation> out = new HashMap<>();
		if (ids.isEmpty()) {
			return out;
		}
		try {
			for (Affectation a : referentielClient.affectations(ids)) {
				out.put(a.collaborateurId().toString(), a);
			}
		} catch (PresenceMetierException ex) {
			log.warn("Temps de travail servi sans noms : {}", ex.getMessage());
		}
		return out;
	}

	/**
	 * Ouverture « après » des heures sup validées (D-P17.e), par collaborateur × site × journée : repousse la
	 * fermeture de la fenêtre d'un jour avec horaire. Une requête pour toute la période, pas de N+1.
	 */
	private Map<String, Integer> ouverturesApres(List<JourTravail> jours, Map<String, Affectation> parIdentifiant,
												 java.util.function.Function<String, String> cle) {
		Set<String> pointeurs = new HashSet<>();
		jours.forEach(j -> {
			pointeurs.add(j.collaborateur());
			Affectation a = parIdentifiant.get(j.collaborateur());
			if (a != null && a.compteId() != null) {
				pointeurs.add(a.compteId().toString());
			}
		});
		LocalDate min = jours.stream().map(JourTravail::jour).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate max = jours.stream().map(JourTravail::jour).max(Comparator.naturalOrder()).orElseThrow();
		List<PresenceHeuresSupJour> lignes = heuresSupJourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(min, max, null, null, null, pointeurs, null, null));
		if (lignes.isEmpty()) {
			return Map.of();
		}
		Map<UUID, PresenceHeuresSupDemande> demandes = demandeRepository
				.findAllById(lignes.stream().map(PresenceHeuresSupJour::getDemandeId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceHeuresSupDemande::getId, d -> d));
		Map<String, Integer> out = new HashMap<>();
		for (PresenceHeuresSupJour l : lignes) {
			PresenceHeuresSupDemande d = demandes.get(l.getDemandeId());
			if (d == null) {
				continue;
			}
			out.merge(cleOuverture(cle.apply(l.getCollaborateurId()), l.getSiteId(), l.getJournee()),
					d.getOuvertureApresMinutes(), Math::max);
		}
		return out;
	}

	private static String cleOuverture(String collaborateur, UUID siteId, LocalDate jour) {
		return collaborateur + "|" + siteId + "|" + jour;
	}

	// ---- Mise en forme -------------------------------------------------------------------------------------------

	private static LigneDetaillee versLigne(JourTravail j, EtatJourHeuresSup etat, PresenceSite site, Affectation a) {
		List<PaireDto> paires = j.paires().stream().map(TempsTravailService::versPaire).toList();
		List<OrphelinDto> orphelins = j.orphelins().stream()
				.map(o -> new OrphelinDto(o.getType().name(), iso(o.getServerTs()), TempsTravailCalcul.estManuel(o))).toList();
		String direction = a != null ? a.directionLibelle() : null;
		String departement = a != null ? a.departementLibelle() : null;
		String service = a != null ? a.serviceLibelle() : null;
		LigneTempsTravail ligne = new LigneTempsTravail(
				j.collaborateur(),
				a != null ? a.nom() : null,
				a != null ? a.matricule() : null,
				j.siteId(),
				site != null ? site.getCode() : null,
				site != null ? site.getLibelle() : null,
				j.jour(),
				j.premiereEntree() != null ? iso(j.premiereEntree()) : null,
				j.derniereSortie() != null ? iso(j.derniereSortie()) : null,
				paires, orphelins, (int) j.minutes(), etat, direction, departement, service);
		return new LigneDetaillee(ligne, direction, departement, service, j.manuel());
	}

	private static PaireDto versPaire(Paire p) {
		long secondes = java.time.Duration.between(p.entree().getServerTs(), p.sortie().getServerTs()).getSeconds();
		return new PaireDto(iso(p.entree().getServerTs()), iso(p.sortie().getServerTs()),
				TempsTravailCalcul.estManuel(p.entree()), TempsTravailCalcul.estManuel(p.sortie()), (int) (secondes / 60));
	}

	static String iso(Instant instant) {
		return ISO_DECALAGE.format(ZonedDateTime.ofInstant(instant, ClockConfig.ZONE_TUNIS).withNano(0));
	}

	private static UUID versUuid(String texte) {
		try {
			return UUID.fromString(texte);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
