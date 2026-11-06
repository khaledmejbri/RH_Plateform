package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.service.CalculHeuresSupService.Appariement;
import com.hr.presence.service.CalculHeuresSupService.Paire;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Calcul pur du temps de travail (D-P17) : journée de travail (D-P17.b), appariement (D-P17.c, repris de D-P14.a),
 * total tronqué à la minute (D-P17.d) et état du jour (D-P17.e). Sans accès aux données : les pointages retenus
 * (VALIDE et VALIDE_MANUEL) sont fournis par l'appelant.
 */
public final class TempsTravailCalcul {

	private TempsTravailCalcul() {
	}

	/** Journée de travail d'un collaborateur sur un site, avec ses paires et ses orphelins. */
	public record JourTravail(String collaborateur, UUID siteId, LocalDate jour, List<Paire> paires,
							  List<PresencePointage> orphelins, long secondes, Instant premiereEntree,
							  Instant derniereSortie, Instant horaireFin, boolean manuel) {

		public long minutes() {
			return secondes / 60;
		}
	}

	private record Cle(String collaborateur, UUID siteId) {
	}

	private record CleJour(String collaborateur, UUID siteId, LocalDate jour) {
	}

	/**
	 * @param pointages     pointages retenus, dans n'importe quel ordre
	 * @param collaborateur identité de regroupement d'un pointage (fiche ou compte ramenés à la même clé)
	 * @param dureeMaxHeures rattachement après minuit d'une SORTIE à l'ENTREE ouverte, sans horaire (16 h)
	 */
	public static List<JourTravail> regrouper(Collection<PresencePointage> pointages,
											  Function<String, String> collaborateur, int dureeMaxHeures) {
		Map<Cle, List<PresencePointage>> parCollaborateurSite = new LinkedHashMap<>();
		for (PresencePointage p : pointages) {
			parCollaborateurSite.computeIfAbsent(new Cle(collaborateur.apply(p.getCollaborateurId()), p.getSiteId()),
					k -> new ArrayList<>()).add(p);
		}
		Map<CleJour, List<PresencePointage>> parJour = new LinkedHashMap<>();
		parCollaborateurSite.forEach((cle, liste) -> {
			liste.sort(Comparator.comparing(PresencePointage::getServerTs));
			Map<PresencePointage, LocalDate> journees = attribuerJournees(liste, dureeMaxHeures);
			for (PresencePointage p : liste) {
				parJour.computeIfAbsent(new CleJour(cle.collaborateur(), cle.siteId(), journees.get(p)),
						k -> new ArrayList<>()).add(p);
			}
		});
		List<JourTravail> out = new ArrayList<>();
		parJour.forEach((cle, liste) -> out.add(construire(cle, liste)));
		return out;
	}

	/**
	 * D-P17.b : la journée enregistrée sur le pointage prévaut ; sinon date Africa/Tunis de l'ENTREE, et une SORTIE
	 * rejoint l'ENTREE ouverte qui la précède si elle arrive au plus {@code dureeMaxHeures} après elle.
	 */
	private static Map<PresencePointage, LocalDate> attribuerJournees(List<PresencePointage> triees, int dureeMaxHeures) {
		Map<PresencePointage, LocalDate> journees = new java.util.IdentityHashMap<>();
		PresencePointage entreeOuverte = null;
		for (PresencePointage p : triees) {
			if (p.getJournee() != null) {
				journees.put(p, p.getJournee());
				continue;
			}
			if (p.getType() == PointageType.ENTREE) {
				journees.put(p, dateTunis(p.getServerTs()));
				entreeOuverte = p;
			} else if (entreeOuverte != null
					&& Duration.between(entreeOuverte.getServerTs(), p.getServerTs()).compareTo(Duration.ofHours(dureeMaxHeures)) <= 0) {
				journees.put(p, journees.get(entreeOuverte));
				entreeOuverte = null;
			} else {
				journees.put(p, dateTunis(p.getServerTs()));
				entreeOuverte = null;
			}
		}
		return journees;
	}

	private static JourTravail construire(CleJour cle, List<PresencePointage> pointages) {
		Appariement appariement = CalculHeuresSupService.apparier(pointages);
		long secondes = 0;
		Instant derniereSortie = null;
		Instant premiereEntree = null;
		Instant horaireFin = null;
		boolean manuel = false;
		for (PresencePointage p : pointages) {
			if (horaireFin == null && p.getHoraireFinTs() != null) {
				horaireFin = p.getHoraireFinTs();
			}
			if (p.getType() == PointageType.ENTREE && premiereEntree == null) {
				premiereEntree = p.getServerTs();
			}
			if (p.getType() == PointageType.SORTIE) {
				derniereSortie = p.getServerTs();
			}
			manuel |= estManuel(p);
		}
		for (Paire paire : appariement.paires()) {
			secondes += Duration.between(paire.entree().getServerTs(), paire.sortie().getServerTs()).getSeconds();
		}
		List<PresencePointage> orphelins = new ArrayList<>(appariement.orphelins());
		orphelins.sort(Comparator.comparing(PresencePointage::getServerTs));
		return new JourTravail(cle.collaborateur(), cle.siteId(), cle.jour(), appariement.paires(), orphelins,
				secondes, premiereEntree, derniereSortie, horaireFin, manuel);
	}

	/**
	 * D-P17.e : COMPLET sans orphelin ; sinon EN_COURS tant que la fenêtre est ouverte, INCOMPLET ensuite.
	 * Fenêtre avec horaire : fin + tolérance après + ouverture après des heures sup validées ; sans horaire : dernière
	 * ENTREE orpheline + {@code dureeMaxHeures}. Une SORTIE orpheline seule ferme la fenêtre d'emblée.
	 */
	public static EtatJourHeuresSup etat(JourTravail jour, Instant maintenant, int toleranceApresMinutes,
										 int ouvertureApresMinutes, int dureeMaxHeures) {
		Instant fermeture = null;
		if (jour.horaireFin() != null) {
			fermeture = jour.horaireFin().plus((long) toleranceApresMinutes + ouvertureApresMinutes, ChronoUnit.MINUTES);
		} else {
			// Seule l'ENTREE orpheline finale (après la dernière paire) garde la fenêtre ouverte : une ENTREE
			// remplacée par une suivante (ENTREE, ENTREE, SORTIE) est déjà perdue.
			Instant derniereSortiePaire = jour.paires().stream().map(p -> p.sortie().getServerTs())
					.max(Comparator.naturalOrder()).orElse(null);
			for (PresencePointage o : jour.orphelins()) {
				if (o.getType() == PointageType.ENTREE
						&& (derniereSortiePaire == null || o.getServerTs().isAfter(derniereSortiePaire))) {
					fermeture = o.getServerTs().plus(dureeMaxHeures, ChronoUnit.HOURS);
				}
			}
		}
		return CalculHeuresSupService.etat(!jour.orphelins().isEmpty(), maintenant, fermeture);
	}

	public static boolean estManuel(PresencePointage p) {
		return p.getStatut() == PointageStatut.VALIDE_MANUEL;
	}

	public static LocalDate dateTunis(Instant instant) {
		return instant.atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
	}
}
