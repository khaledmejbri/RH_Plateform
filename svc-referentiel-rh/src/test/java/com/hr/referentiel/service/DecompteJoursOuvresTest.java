package com.hr.referentiel.service;

import com.hr.referentiel.domain.PeriodeJour;
import com.hr.referentiel.service.DecompteJoursOuvres.Periodes;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C1-01 et C1-02 : décompte des jours ouvrés (semaine du lundi 07/12/2026 au vendredi 11/12/2026, sans férié). */
class DecompteJoursOuvresTest {

	private static final LocalDate LUN = LocalDate.of(2026, 12, 7);
	private static final LocalDate MAR = LUN.plusDays(1);
	private static final LocalDate MER = LUN.plusDays(2);
	private static final LocalDate VEN = LUN.plusDays(4);
	private static final LocalDate SAM = LUN.plusDays(5);
	private static final LocalDate DIM = LUN.plusDays(6);
	private static final LocalDate LUN_SUIVANT = LUN.plusDays(7);
	private static final Set<LocalDate> AUCUN = Set.of();

	private static BigDecimal jours(LocalDate d, LocalDate f, PeriodeJour p, PeriodeJour q, Set<LocalDate> feries) {
		Periodes periodes = DecompteJoursOuvres.normaliser(d, f, p, q);
		DecompteJoursOuvres.verifierDemiJournees(periodes, feries);
		return DecompteJoursOuvres.jours(periodes, feries);
	}

	private static void assertJours(BigDecimal reel, String attendu) {
		assertThat(reel).isEqualByComparingTo(attendu);
	}

	@Test
	@DisplayName("C1-01 : lundi à vendredi = 5 ; lundi à lundi suivant = 6 ; vendredi à lundi = 2")
	void semaines() {
		assertJours(jours(LUN, VEN, null, null, AUCUN), "5");
		assertJours(jours(LUN, LUN_SUIVANT, null, null, AUCUN), "6");
		assertJours(jours(VEN, LUN_SUIVANT, null, null, AUCUN), "2");
	}

	@Test
	@DisplayName("C1-01 : lundi après-midi au mardi matin = 1 ; mercredi matin ou après-midi = 0,5 ; mercredi journée = 1")
	void demiJournees() {
		assertJours(jours(LUN, MAR, PeriodeJour.APRES_MIDI, PeriodeJour.MATIN, AUCUN), "1");
		assertJours(jours(MER, MER, PeriodeJour.MATIN, null, AUCUN), "0.5");
		assertJours(jours(MER, MER, PeriodeJour.APRES_MIDI, null, AUCUN), "0.5");
		assertJours(jours(MER, MER, PeriodeJour.JOURNEE, null, AUCUN), "1");
	}

	@Test
	@DisplayName("C1-02 : un jour férié actif retire un jour ; seul, il ne laisse aucun jour ouvré (0)")
	void feries() {
		assertJours(jours(LUN, VEN, null, null, Set.of(MER)), "4");
		assertJours(jours(MER, MER, null, null, Set.of(MER)), "0");
	}

	@Test
	@DisplayName("C1-02 : un férié tombant un samedi est sans effet ; un week-end seul donne 0 jour")
	void ferieUnSamediEtWeekEnd() {
		assertJours(jours(LUN, VEN, null, null, Set.of(SAM)), "5");
		assertJours(jours(SAM, DIM, null, null, AUCUN), "0");
	}

	@Test
	@DisplayName("C1-02 : un jour férié inactif (absent des dates actives) compte comme ouvré")
	void ferieInactif() {
		assertJours(jours(LUN, VEN, null, null, Set.of()), "5");
	}

	@Test
	@DisplayName("C1-02 : demi-journée sur un samedi ou un férié -> 400 DEMI_JOURNEE_JOUR_NON_OUVRE (même sans jour ouvré)")
	void demiJourneeJourNonOuvre() {
		assertThatThrownBy(() -> jours(SAM, SAM, PeriodeJour.MATIN, null, AUCUN)).isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("DEMI_JOURNEE_JOUR_NON_OUVRE"));
		assertThatThrownBy(() -> jours(MER, MER, PeriodeJour.APRES_MIDI, null, Set.of(MER)))
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("DEMI_JOURNEE_JOUR_NON_OUVRE"));
		assertThatThrownBy(() -> jours(SAM, LUN_SUIVANT, PeriodeJour.APRES_MIDI, null, AUCUN))
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("DEMI_JOURNEE_JOUR_NON_OUVRE"));
	}

	@Test
	@DisplayName("C1-02 : périodes mal formées -> 400 CORPS_INVALIDE")
	void corpsInvalide() {
		// deux périodes différentes pour un seul jour
		assertCorpsInvalide(() -> DecompteJoursOuvres.normaliser(MER, MER, PeriodeJour.JOURNEE, PeriodeJour.MATIN));
		// dernier jour après-midi
		assertCorpsInvalide(() -> DecompteJoursOuvres.normaliser(LUN, MER, PeriodeJour.JOURNEE, PeriodeJour.APRES_MIDI));
		// premier jour matin sur plusieurs jours
		assertCorpsInvalide(() -> DecompteJoursOuvres.normaliser(LUN, MER, PeriodeJour.MATIN, PeriodeJour.JOURNEE));
		// fin avant début
		assertCorpsInvalide(() -> DecompteJoursOuvres.normaliser(VEN, LUN, null, null));
		// période inconnue
		assertCorpsInvalide(() -> DecompteJoursOuvres.lirePeriode("SOIR"));
	}

	@Test
	@DisplayName("chevauchement à la demi-journée : après-midi sur après-midi oui, matin de J+1 après un congé finissant J non")
	void intervalles() {
		long[] apresMidi = DecompteJoursOuvres.intervalle(MER, MER, PeriodeJour.APRES_MIDI, PeriodeJour.APRES_MIDI);
		long[] matin = DecompteJoursOuvres.intervalle(MER, MER, PeriodeJour.MATIN, PeriodeJour.MATIN);
		assertThat(chevauche(apresMidi, matin)).isFalse();
		assertThat(chevauche(apresMidi, apresMidi)).isTrue();
		long[] journeeMer = DecompteJoursOuvres.intervalle(LUN, MER, PeriodeJour.JOURNEE, PeriodeJour.JOURNEE);
		long[] matinJeudi = DecompteJoursOuvres.intervalle(MER.plusDays(1), MER.plusDays(1), PeriodeJour.MATIN,
				PeriodeJour.MATIN);
		assertThat(chevauche(journeeMer, matinJeudi)).isFalse();
	}

	private static boolean chevauche(long[] a, long[] b) {
		return a[0] < b[1] && b[0] < a[1];
	}

	private static void assertCorpsInvalide(Runnable r) {
		assertThatThrownBy(r::run).isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("CORPS_INVALIDE"));
	}
}
