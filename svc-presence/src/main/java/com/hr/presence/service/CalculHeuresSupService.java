package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Heures supplémentaires comptées (D-P14), calculées à la volée à partir de données figées : l'instantané d'horaire
 * porté par chaque pointage et les ouvertures de la demande validée. La tolérance n'est jamais comptée.
 * <p>
 * Appariement (décision client n° 3 et 16) : pointages triés par heure serveur ; chaque ENTREE est appariée à la
 * SORTIE suivante, une nouvelle ENTREE remplace la précédente qui devient orpheline (ENTREE, ENTREE, SORTIE : la
 * dernière ENTREE forme la paire) ; toute SORTIE sans ENTREE est orpheline. Les paires s'additionnent, les orphelins
 * sont ignorés ; le jour est INCOMPLET s'il en reste un une fois la fenêtre fermée, EN_COURS avant.
 */
@Service
public class CalculHeuresSupService {

	/** Arrondi du total journalier, vers le bas (D-P14). */
	static final int ARRONDI_MINUTES = 15;

	private static final Set<PointageStatut> STATUTS_COMPTES = EnumSet.of(PointageStatut.VALIDE, PointageStatut.VALIDE_MANUEL);

	/** Résultat du calcul d'une journée, avant mise en forme. */
	public record ResultatJour(long minutes, Instant premiereEntree, Instant derniereSortie, boolean orphelin,
							   Instant horaireDebut, Instant horaireFin) {
	}

	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final PresenceHoraireSiteRepository horaireRepository;
	private final PresenceHeuresSupDemandeRepository demandeRepository;
	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	private final PresenceProperties properties;
	private final Clock clock;

	public CalculHeuresSupService(
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceHoraireSiteRepository horaireRepository,
			PresenceHeuresSupDemandeRepository demandeRepository,
			PresenceHeuresSupBeneficiaireRepository beneficiaireRepository,
			PresenceProperties properties,
			Clock clock) {
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.horaireRepository = horaireRepository;
		this.demandeRepository = demandeRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.properties = properties;
		this.clock = clock;
	}

	// ---- Calcul pur --------------------------------------------------------------------------------------------

	/** Paire ENTREE / SORTIE successives (D-P14.a). */
	public record Paire(PresencePointage entree, PresencePointage sortie) {
	}

	/** Résultat de l'appariement : paires dans l'ordre chronologique, pointages orphelins (ENTREE ou SORTIE) à part. */
	public record Appariement(List<Paire> paires, List<PresencePointage> orphelins) {
	}

	/**
	 * Appariement D-P14.a, partagé avec la vue « Temps de travail » (D-P17.c) : chaque ENTREE est appariée à la SORTIE
	 * suivante, une nouvelle ENTREE rend la précédente orpheline, une SORTIE sans ENTREE est orpheline.
	 *
	 * @param pointages pointages acceptés triés par heure serveur
	 */
	public static Appariement apparier(List<PresencePointage> pointages) {
		List<Paire> paires = new ArrayList<>();
		List<PresencePointage> orphelins = new ArrayList<>();
		PresencePointage entreeEnCours = null;
		for (PresencePointage p : pointages) {
			if (p.getType() == PointageType.ENTREE) {
				if (entreeEnCours != null) {
					orphelins.add(entreeEnCours);
				}
				entreeEnCours = p;
			} else if (entreeEnCours == null) {
				orphelins.add(p);
			} else {
				paires.add(new Paire(entreeEnCours, p));
				entreeEnCours = null;
			}
		}
		if (entreeEnCours != null) {
			orphelins.add(entreeEnCours);
		}
		return new Appariement(paires, orphelins);
	}

	/**
	 * @param pointages pointages acceptés du collaborateur pour la journée, triés par heure serveur
	 */
	public static ResultatJour calculerJour(List<PresencePointage> pointages, int ouvertureAvantMinutes, int ouvertureApresMinutes) {
		Appariement appariement = apparier(pointages);
		long secondes = 0;
		Instant premiereEntree = null;
		Instant horaireDebut = null;
		Instant horaireFin = null;
		for (PresencePointage p : pointages) {
			if (horaireDebut == null && p.getHoraireDebutTs() != null) {
				horaireDebut = p.getHoraireDebutTs();
				horaireFin = p.getHoraireFinTs();
			}
			if (premiereEntree == null && p.getType() == PointageType.ENTREE) {
				premiereEntree = p.getServerTs();
			}
		}
		Instant derniereSortie = null;
		for (Paire paire : appariement.paires()) {
			secondes += secondesComptees(paire.entree(), paire.sortie(), ouvertureAvantMinutes, ouvertureApresMinutes);
			derniereSortie = paire.sortie().getServerTs();
		}
		long minutes = secondes / 60;
		return new ResultatJour(minutes - minutes % ARRONDI_MINUTES, premiereEntree, derniereSortie,
				!appariement.orphelins().isEmpty(), horaireDebut, horaireFin);
	}

	/** Intersection de la paire avec les plages validées : [début - ouverture avant ; début] et [fin ; fin + ouverture après]. */
	private static long secondesComptees(PresencePointage entree, PresencePointage sortie, int avant, int apres) {
		Instant debut = entree.getHoraireDebutTs();
		Instant fin = entree.getHoraireFinTs();
		if (debut == null || fin == null) {
			return 0;
		}
		long total = 0;
		if (avant > 0) {
			total += intersection(entree.getServerTs(), sortie.getServerTs(), debut.minus(avant, ChronoUnit.MINUTES), debut);
		}
		if (apres > 0) {
			total += intersection(entree.getServerTs(), sortie.getServerTs(), fin, fin.plus(apres, ChronoUnit.MINUTES));
		}
		return total;
	}

	private static long intersection(Instant debutA, Instant finA, Instant debutB, Instant finB) {
		Instant debut = debutA.isAfter(debutB) ? debutA : debutB;
		Instant fin = finA.isBefore(finB) ? finA : finB;
		return fin.isAfter(debut) ? Duration.between(debut, fin).getSeconds() : 0;
	}

	public static EtatJourHeuresSup etat(boolean orphelin, Instant maintenant, Instant fermetureFenetre) {
		if (!orphelin) {
			return EtatJourHeuresSup.COMPLET;
		}
		return fermetureFenetre != null && !maintenant.isAfter(fermetureFenetre)
				? EtatJourHeuresSup.EN_COURS
				: EtatJourHeuresSup.INCOMPLET;
	}

	public static String formaterHeures(long minutes) {
		return String.format("%02d:%02d", minutes / 60, minutes % 60);
	}

	// ---- Période -----------------------------------------------------------------------------------------------

	/** Période du comptage : par défaut les 31 derniers jours ; fin avant début 400, au-delà du plafond 400. */
	public LocalDate[] periode(LocalDate du, LocalDate au) {
		LocalDate aujourdhui = clock.instant().atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		LocalDate fin = au != null ? au : aujourdhui;
		LocalDate debut = du != null ? du : fin.minusDays(30);
		if (fin.isBefore(debut)) {
			throw PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début");
		}
		int max = properties.getHeuresSup().getPeriodeExportMaxJours();
		if (ChronoUnit.DAYS.between(debut, fin) + 1 > max) {
			throw PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + max + " jours");
		}
		return new LocalDate[] {debut, fin};
	}

	// ---- Comptage ----------------------------------------------------------------------------------------------

	/**
	 * Une ligne par bénéficiaire × site × journée des lignes fournies (demandes VALIDEE), dans l'ordre reçu.
	 * Chargements par lot : une requête de pointages par site, pas de N+1.
	 */
	@Transactional(readOnly = true)
	public List<HeuresComptees> compter(List<PresenceHeuresSupJour> lignes) {
		if (lignes.isEmpty()) {
			return List.of();
		}
		Instant maintenant = clock.instant();
		Map<UUID, PresenceHeuresSupDemande> demandes = demandeRepository
				.findAllById(lignes.stream().map(PresenceHeuresSupJour::getDemandeId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceHeuresSupDemande::getId, d -> d));
		Map<UUID, PresenceHeuresSupBeneficiaire> beneficiaires = beneficiaireRepository
				.findAllById(lignes.stream().map(PresenceHeuresSupJour::getBeneficiaireId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceHeuresSupBeneficiaire::getId, b -> b));
		Set<UUID> siteIds = lignes.stream().map(PresenceHeuresSupJour::getSiteId).collect(Collectors.toSet());
		Map<UUID, PresenceSite> sites = siteRepository.findAllById(siteIds).stream()
				.collect(Collectors.toMap(PresenceSite::getId, s -> s));
		Map<UUID, Map<DayOfWeek, Horaire>> horaires = new HashMap<>();
		// Pointages indexés par site, puis par (collaborateur - fiche ou compte - et journée) : pas de filtre O(n x m).
		Map<UUID, Map<String, List<PresencePointage>>> pointagesParSite = new HashMap<>();
		for (UUID siteId : siteIds) {
			horaires.put(siteId, FenetreHoraireService.versHoraires(horaireRepository.findBySiteIdOrderByJourSemaine(siteId)));
			LocalDate min = lignes.stream().filter(l -> l.getSiteId().equals(siteId)).map(PresenceHeuresSupJour::getJournee)
					.min(Comparator.naturalOrder()).orElseThrow();
			LocalDate max = lignes.stream().filter(l -> l.getSiteId().equals(siteId)).map(PresenceHeuresSupJour::getJournee)
					.max(Comparator.naturalOrder()).orElseThrow();
			Set<String> collaborateurs = new LinkedHashSet<>();
			for (PresenceHeuresSupJour l : lignes) {
				if (l.getSiteId().equals(siteId)) {
					collaborateurs.add(l.getCollaborateurId());
					if (l.getCompteId() != null) {
						collaborateurs.add(l.getCompteId());
					}
				}
			}
			Map<String, List<PresencePointage>> index = new HashMap<>();
			for (PresencePointage p : pointageRepository.findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(
					siteId, min, max, STATUTS_COMPTES, collaborateurs)) {
				index.computeIfAbsent(cle(p.getCollaborateurId(), p.getJournee()), k -> new ArrayList<>()).add(p);
			}
			pointagesParSite.put(siteId, index);
		}

		List<HeuresComptees> out = new ArrayList<>();
		for (PresenceHeuresSupJour l : lignes) {
			PresenceHeuresSupDemande d = demandes.get(l.getDemandeId());
			if (d == null || d.getStatut() != StatutHeuresSup.VALIDEE) {
				continue;
			}
			PresenceHeuresSupBeneficiaire b = beneficiaires.get(l.getBeneficiaireId());
			PresenceSite site = sites.get(l.getSiteId());
			Map<String, List<PresencePointage>> index = pointagesParSite.get(l.getSiteId());
			List<PresencePointage> duJour = new ArrayList<>(index.getOrDefault(cle(l.getCollaborateurId(), l.getJournee()), List.of()));
			if (l.getCompteId() != null) {
				duJour.addAll(index.getOrDefault(cle(l.getCompteId(), l.getJournee()), List.of()));
				duJour.sort(Comparator.comparing(PresencePointage::getServerTs));
			}
			ResultatJour r = calculerJour(duJour, d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
			out.add(versLigne(l, d, b, site, horaires.get(l.getSiteId()), r, maintenant));
		}
		return out;
	}

	private HeuresComptees versLigne(PresenceHeuresSupJour l, PresenceHeuresSupDemande d, PresenceHeuresSupBeneficiaire b,
									 PresenceSite site, Map<DayOfWeek, Horaire> horaires, ResultatJour r, Instant maintenant) {
		Instant debut = r.horaireDebut();
		Instant fin = r.horaireFin();
		if (debut == null) {
			// Aucun pointage portant d'instantané : horaire en vigueur pour l'affichage seulement.
			Horaire h = horaires.get(l.getJournee().getDayOfWeek());
			if (h != null) {
				FenetreHoraireService.Fenetre f = FenetreHoraireService.fenetre(l.getJournee(), h, 0, 0, 0, 0);
				debut = f.horaireDebut().toInstant();
				fin = f.horaireFin().toInstant();
			}
		}
		int toleranceApres = site != null ? site.getToleranceApresMinutes() : PresenceSite.TOLERANCE_DEFAUT_MINUTES;
		Instant fermeture = fin != null
				? fin.plus((long) toleranceApres + d.getOuvertureApresMinutes(), ChronoUnit.MINUTES) : null;
		EtatJourHeuresSup etat = etat(r.orphelin(), maintenant, fermeture);
		String plageAvant = debut != null && d.getOuvertureAvantMinutes() > 0
				? heure(debut.minus(d.getOuvertureAvantMinutes(), ChronoUnit.MINUTES)) + "-" + heure(debut) : null;
		String plageApres = fin != null && d.getOuvertureApresMinutes() > 0
				? heure(fin) + "-" + heure(fin.plus(d.getOuvertureApresMinutes(), ChronoUnit.MINUTES)) : null;
		return new HeuresComptees(
				b != null ? b.getCollaborateurId().toString() : l.getCollaborateurId(),
				b != null ? b.getMatricule() : null,
				b != null ? b.getNom() : null,
				l.getSiteId(),
				site != null ? site.getLibelle() : null,
				l.getJournee(),
				debut != null ? heure(debut) : null,
				fin != null ? heure(fin) : null,
				plageAvant,
				plageApres,
				r.premiereEntree(),
				r.derniereSortie(),
				r.minutes(),
				formaterHeures(r.minutes()),
				d.getReference(),
				etat);
	}

	private static String cle(String collaborateurId, LocalDate journee) {
		return collaborateurId + "|" + journee;
	}

	private static String heure(Instant instant) {
		return FenetreHoraireService.HEURE_MINUTE.format(ZonedDateTime.ofInstant(instant, ClockConfig.ZONE_TUNIS));
	}
}
