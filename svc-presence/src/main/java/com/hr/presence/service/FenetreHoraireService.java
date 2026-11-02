package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceHoraireSite;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fenêtre de pointage d'un site (D-P11, D-P12) : horaire du jour, tolérances, heures sup validées.
 * Fuseau Africa/Tunis ; les bornes sont incluses et comparées à la seconde.
 */
@Service
public class FenetreHoraireService {

	public static final DateTimeFormatter HEURE_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

	/** Horaire d'un jour : fin ≤ début = horaire de nuit (fin le lendemain). */
	public record Horaire(LocalTime debut, LocalTime fin) {
		public boolean deNuit() {
			return !fin.isAfter(debut);
		}
	}

	/** Fenêtre d'une journée : horaire et plage d'ouverture du pointage (tolérances et heures sup comprises). */
	public record Fenetre(LocalDate journee, ZonedDateTime horaireDebut, ZonedDateTime horaireFin,
						  ZonedDateTime ouvertureDebut, ZonedDateTime ouvertureFin) {
		boolean contient(Instant t) {
			return !t.isBefore(ouvertureDebut.toInstant()) && !t.isAfter(ouvertureFin.toInstant());
		}
	}

	/** Ouverture d'heures sup validée pour un bénéficiaire et une journée. */
	public record OuvertureValidee(LocalDate journee, String reference, int avantMinutes, int apresMinutes) {
	}

	public sealed interface Resultat permits SansHoraire, DansFenetre, HorsFenetre {
	}

	/** Site sans horaire (D-P11.b) : aucun contrôle. */
	public record SansHoraire() implements Resultat {
	}

	public record DansFenetre(LocalDate journee, Instant horaireDebut, Instant horaireFin) implements Resultat {
	}

	public record HorsFenetre(String message) implements Resultat {
	}

	private final PresenceHoraireSiteRepository horaireRepository;
	private final PresenceHeuresSupJourRepository jourRepository;
	private final PresenceHeuresSupDemandeRepository demandeRepository;

	public FenetreHoraireService(
			PresenceHoraireSiteRepository horaireRepository,
			PresenceHeuresSupJourRepository jourRepository,
			PresenceHeuresSupDemandeRepository demandeRepository) {
		this.horaireRepository = horaireRepository;
		this.jourRepository = jourRepository;
		this.demandeRepository = demandeRepository;
	}

	// ---- Calcul pur --------------------------------------------------------------------------------------------

	public static Fenetre fenetre(LocalDate journee, Horaire horaire, int toleranceAvant, int toleranceApres,
								  int ouvertureAvant, int ouvertureApres) {
		ZonedDateTime debut = journee.atTime(horaire.debut()).atZone(ClockConfig.ZONE_TUNIS);
		LocalDate jourFin = horaire.deNuit() ? journee.plusDays(1) : journee;
		ZonedDateTime fin = jourFin.atTime(horaire.fin()).atZone(ClockConfig.ZONE_TUNIS);
		return new Fenetre(journee, debut, fin,
				debut.minusMinutes((long) toleranceAvant + ouvertureAvant),
				fin.plusMinutes((long) toleranceApres + ouvertureApres));
	}

	/**
	 * D-P11.a : les fenêtres de deux jours consécutifs ne se chevauchent pas (égalité tolérée), dimanche vers lundi compris.
	 */
	public static void verifierSemaineSansChevauchement(Map<DayOfWeek, Horaire> jours, int toleranceAvant, int toleranceApres) {
		LocalDate lundiReference = LocalDate.of(2024, 1, 1);
		for (Map.Entry<DayOfWeek, Horaire> e : jours.entrySet()) {
			DayOfWeek suivant = e.getKey().plus(1);
			Horaire horaireSuivant = jours.get(suivant);
			if (horaireSuivant == null) {
				continue;
			}
			LocalDate jour = lundiReference.plusDays(e.getKey().getValue() - 1L);
			Fenetre courante = fenetre(jour, e.getValue(), toleranceAvant, toleranceApres, 0, 0);
			Fenetre suivante = fenetre(jour.plusDays(1), horaireSuivant, toleranceAvant, toleranceApres, 0, 0);
			if (courante.ouvertureFin().isAfter(suivante.ouvertureDebut())) {
				throw PresenceMetierException.unprocessable("HORAIRES_CHEVAUCHANTS",
						"Les fenêtres de pointage de deux jours consécutifs se chevauchent ("
								+ JourFr.de(e.getKey()) + " et " + JourFr.de(suivant) + ")");
			}
		}
	}

	/** Libellés des jours pour les messages. */
	private static final class JourFr {
		static String de(DayOfWeek jour) {
			return com.hr.presence.domain.JourSemaine.de(jour).name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	// ---- Lecture -----------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public Map<DayOfWeek, Horaire> horaires(UUID siteId) {
		return versHoraires(horaireRepository.findBySiteIdOrderByJourSemaine(siteId));
	}

	public static Map<DayOfWeek, Horaire> versHoraires(List<PresenceHoraireSite> lignes) {
		Map<DayOfWeek, Horaire> out = new EnumMap<>(DayOfWeek.class);
		for (PresenceHoraireSite l : lignes) {
			out.put(DayOfWeek.of(l.getJourSemaine()), new Horaire(l.getHeureDebut(), l.getHeureFin()));
		}
		return out;
	}

	/** Heures sup validées d'un pointeur (fiche ou compte) pour des journées d'un site, par journée. */
	@Transactional(readOnly = true)
	public Map<LocalDate, OuvertureValidee> ouverturesValidees(UUID siteId, String pointeurId, Collection<LocalDate> journees) {
		if (pointeurId == null || journees.isEmpty()) {
			return Map.of();
		}
		List<PresenceHeuresSupJour> lignes = jourRepository.findPourPointeur(siteId, journees, pointeurId);
		if (lignes.isEmpty()) {
			return Map.of();
		}
		Set<UUID> ids = lignes.stream().map(PresenceHeuresSupJour::getDemandeId).collect(Collectors.toSet());
		Map<UUID, PresenceHeuresSupDemande> demandes = demandeRepository.findAllById(ids).stream()
				.filter(d -> d.getStatut() == StatutHeuresSup.VALIDEE)
				.collect(Collectors.toMap(PresenceHeuresSupDemande::getId, d -> d));
		Map<LocalDate, OuvertureValidee> out = new HashMap<>();
		for (PresenceHeuresSupJour l : lignes) {
			PresenceHeuresSupDemande d = demandes.get(l.getDemandeId());
			if (d != null) {
				out.put(l.getJournee(), new OuvertureValidee(l.getJournee(), d.getReference(),
						d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes()));
			}
		}
		return out;
	}

	/** Fenêtre d'une journée précise pour un pointeur (vide si le site est fermé ce jour-là ou sans horaire). */
	@Transactional(readOnly = true)
	public java.util.Optional<Fenetre> fenetreDuJour(PresenceSite site, String pointeurId, LocalDate journee) {
		Horaire h = horaires(site.getId()).get(journee.getDayOfWeek());
		if (h == null) {
			return java.util.Optional.empty();
		}
		OuvertureValidee o = ouverturesValidees(site.getId(), pointeurId, List.of(journee)).get(journee);
		return java.util.Optional.of(fenetre(journee, h, site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(),
				o != null ? o.avantMinutes() : 0, o != null ? o.apresMinutes() : 0));
	}

	/**
	 * Contrôle D-P12 d'un pointage (ou d'une heure déclarée en pointage manuel) à l'instant {@code instant}.
	 * Plusieurs journées candidates (fenêtres qui se touchent) : ENTREE → la plus tardive, SORTIE → la plus ancienne.
	 */
	@Transactional(readOnly = true)
	public Resultat evaluer(PresenceSite site, String pointeurId, Instant instant, PointageType type) {
		Map<DayOfWeek, Horaire> horaires = horaires(site.getId());
		if (horaires.isEmpty()) {
			return new SansHoraire();
		}
		Instant t = instant.truncatedTo(ChronoUnit.SECONDS);
		LocalDate jourLocal = t.atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		List<LocalDate> candidats = List.of(jourLocal.minusDays(1), jourLocal, jourLocal.plusDays(1));
		Map<LocalDate, OuvertureValidee> ouvertures = ouverturesValidees(site.getId(), pointeurId, candidats);

		List<Fenetre> retenues = new ArrayList<>();
		Fenetre fenetreDuJour = null;
		for (LocalDate c : candidats) {
			Horaire h = horaires.get(c.getDayOfWeek());
			if (h == null) {
				continue;
			}
			OuvertureValidee o = ouvertures.get(c);
			Fenetre f = fenetre(c, h, site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(),
					o != null ? o.avantMinutes() : 0, o != null ? o.apresMinutes() : 0);
			if (c.equals(jourLocal)) {
				fenetreDuJour = f;
			}
			if (f.contient(t)) {
				retenues.add(f);
			}
		}
		if (retenues.isEmpty()) {
			return new HorsFenetre(fenetreDuJour == null
					? "Site fermé aujourd'hui"
					: "Pointage ouvert de " + HEURE_MINUTE.format(fenetreDuJour.ouvertureDebut())
					+ " à " + HEURE_MINUTE.format(fenetreDuJour.ouvertureFin()));
		}
		Fenetre choisie = type == PointageType.SORTIE
				? retenues.stream().min(java.util.Comparator.comparing(Fenetre::journee)).orElseThrow()
				: retenues.stream().max(java.util.Comparator.comparing(Fenetre::journee)).orElseThrow();
		return new DansFenetre(choisie.journee(), choisie.horaireDebut().toInstant(), choisie.horaireFin().toInstant());
	}
}
