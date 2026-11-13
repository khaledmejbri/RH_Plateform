package com.hr.referentiel.service;

import com.hr.referentiel.domain.PeriodeJour;
import com.hr.referentiel.web.ReferentielMetierException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Décompte des jours ouvrés d'un congé annuel (D-S01.a), fonctions pures : lundi à vendredi hors jours fériés actifs,
 * moins une demi-journée si le premier jour est « Après-midi », moins une si le dernier est « Matin ».
 * Contrôles de forme (400) avant règle (422), D-S15.a / f.
 */
public final class DecompteJoursOuvres {

	private static final BigDecimal DEMI = new BigDecimal("0.5");

	private DecompteJoursOuvres() {
	}

	/** Périodes normalisées : {@code dernier} vaut {@code premier} pour une demande d'un jour. */
	public record Periodes(LocalDate debut, LocalDate fin, PeriodeJour premier, PeriodeJour dernier) {

		public boolean unSeulJour() {
			return debut.equals(fin);
		}
	}

	public static boolean estOuvre(LocalDate jour, Set<LocalDate> feries) {
		DayOfWeek j = jour.getDayOfWeek();
		return j != DayOfWeek.SATURDAY && j != DayOfWeek.SUNDAY && !feries.contains(jour);
	}

	/** Lit une période envoyée par le client ({@code null} ou vide = journée entière) ; inconnue : 400. */
	public static PeriodeJour lirePeriode(Object brut) {
		if (brut == null || String.valueOf(brut).isBlank()) {
			return PeriodeJour.JOURNEE;
		}
		try {
			return PeriodeJour.valueOf(String.valueOf(brut).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw corpsInvalide("La période « " + brut + " » n'est pas reconnue (journée entière, matin ou après-midi).");
		}
	}

	/** Valide la forme des périodes (D-S15.a) et renvoie les périodes normalisées. */
	public static Periodes normaliser(LocalDate debut, LocalDate fin, PeriodeJour premier, PeriodeJour dernier) {
		if (fin.isBefore(debut)) {
			throw corpsInvalide("La date de fin de congé ne peut pas être antérieure à la date de début.");
		}
		PeriodeJour p = premier == null ? PeriodeJour.JOURNEE : premier;
		PeriodeJour d = dernier == null ? p : dernier;
		if (debut.equals(fin)) {
			if (p != d) {
				throw corpsInvalide("Un congé d'un seul jour ne peut avoir qu'une seule période.");
			}
			return new Periodes(debut, fin, p, p);
		}
		if (p == PeriodeJour.MATIN) {
			throw corpsInvalide("Le premier jour d'un congé de plusieurs jours commence le matin ou l'après-midi : "
					+ "choisissez « Journée entière » ou « Après-midi ».");
		}
		if (dernier == null) {
			d = PeriodeJour.JOURNEE;
		}
		if (d == PeriodeJour.APRES_MIDI) {
			throw corpsInvalide("Le dernier jour d'un congé de plusieurs jours se termine à la journée entière ou au matin.");
		}
		return new Periodes(debut, fin, p, d);
	}

	/** Une demi-journée ne se pose pas un samedi, un dimanche ou un jour férié actif (400, même sans jour ouvré). */
	public static void verifierDemiJournees(Periodes p, Set<LocalDate> feries) {
		boolean demiPremier = p.premier() != PeriodeJour.JOURNEE;
		boolean demiDernier = !p.unSeulJour() && p.dernier() == PeriodeJour.MATIN;
		if ((demiPremier && !estOuvre(p.debut(), feries)) || (demiDernier && !estOuvre(p.fin(), feries))) {
			throw ReferentielMetierException.badRequest("DEMI_JOURNEE_JOUR_NON_OUVRE",
					"Une demi-journée ne peut pas être posée un jour non ouvré (week-end ou jour férié).");
		}
	}

	/** Nombre de jours décomptés (échelle 1) ; 0 si aucun jour ouvré. */
	public static BigDecimal jours(Periodes p, Set<LocalDate> feries) {
		if (p.unSeulJour()) {
			if (!estOuvre(p.debut(), feries)) {
				return zero();
			}
			return p.premier() == PeriodeJour.JOURNEE ? new BigDecimal("1.0") : new BigDecimal("0.5");
		}
		long entiers = 0;
		for (LocalDate j = p.debut(); !j.isAfter(p.fin()); j = j.plusDays(1)) {
			if (estOuvre(j, feries)) {
				entiers++;
			}
		}
		BigDecimal total = BigDecimal.valueOf(entiers).setScale(1);
		if (p.premier() == PeriodeJour.APRES_MIDI && estOuvre(p.debut(), feries)) {
			total = total.subtract(DEMI);
		}
		if (p.dernier() == PeriodeJour.MATIN && estOuvre(p.fin(), feries)) {
			total = total.subtract(DEMI);
		}
		return total.max(zero());
	}

	/** Jours fériés actifs tombant dans la période (affichés par la simulation). */
	public static java.util.List<LocalDate> feriesDansPeriode(Periodes p, Set<LocalDate> feries) {
		return feries.stream().filter(j -> !j.isBefore(p.debut()) && !j.isAfter(p.fin())).sorted().toList();
	}

	/**
	 * Intervalle en demi-journées {@code [debut, fin)} : le contrôle de chevauchement compare ces bornes.
	 * Autres types de congé : journées entières.
	 */
	public static long[] intervalle(LocalDate debut, LocalDate fin, PeriodeJour premier, PeriodeJour dernier) {
		long a = 2 * debut.toEpochDay() + (premier == PeriodeJour.APRES_MIDI ? 1 : 0);
		PeriodeJour fd = debut.equals(fin) ? premier : dernier;
		long b = 2 * fin.toEpochDay() + (fd == PeriodeJour.MATIN ? 1 : 2);
		return new long[] {a, b};
	}

	private static BigDecimal zero() {
		return new BigDecimal("0.0");
	}

	private static ReferentielMetierException corpsInvalide(String message) {
		return ReferentielMetierException.badRequest("CORPS_INVALIDE", message);
	}
}
