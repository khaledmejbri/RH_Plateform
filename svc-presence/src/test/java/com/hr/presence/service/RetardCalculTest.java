package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresencePointage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D-P19 : retard d'une journée, calcul pur sur l'instantané d'horaire du pointage (aucune horloge, aucune base). */
class RetardCalculTest {

	private static Instant t(int mois, int jour, int h, int min, int s) {
		return LocalDateTime.of(2026, mois, jour, h, min, s).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private static PresencePointage p(PointageType type, Instant ts, Instant debutHoraire) {
		PresencePointage p = new PresencePointage();
		p.setType(type);
		p.setStatut(PointageStatut.VALIDE);
		p.setServerTs(ts);
		p.setHoraireDebutTs(debutHoraire);
		return p;
	}

	@Test
	void entreeA0812h40_horaire8h_graceZero_retardDe12Minutes() {
		var e = p(PointageType.ENTREE, t(11, 5, 8, 12, 40), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isEqualTo(12);
	}

	@Test
	void entreeA0800h59_pasDeRetard_tronqueALaMinute() {
		var e = p(PointageType.ENTREE, t(11, 5, 8, 0, 59), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isZero();
	}

	@Test
	void entreeEnAvance_pasDeRetard() {
		var e = p(PointageType.ENTREE, t(11, 5, 7, 50, 0), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isZero();
	}

	@Test
	void siteSansHoraire_retardNonCalculable() {
		var e = p(PointageType.ENTREE, t(11, 5, 8, 30, 0), null);
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isNull();
	}

	@Test
	void entreeManuelleDeclaree0830_retardDe30Minutes() {
		var e = p(PointageType.ENTREE, t(11, 5, 8, 30, 0), t(11, 5, 8, 0, 0));
		e.setStatut(PointageStatut.VALIDE_MANUEL);
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isEqualTo(30);
	}

	@Test
	void horaireChangeLe04_laEntreeDu03GardeLHoraireDe8h() {
		// l'instantané porté par le pointage du 03/11 est 08:00 même si l'horaire vaut 09:00 depuis le 04/11
		var e = p(PointageType.ENTREE, t(11, 3, 8, 30, 0), t(11, 3, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ZERO)).isEqualTo(30);
	}

	@Test
	void premierPointageDuJourEstUneSortie_retardNonCalculable() {
		var s = p(PointageType.SORTIE, t(11, 5, 8, 0, 0), t(11, 5, 8, 0, 0));
		var e = p(PointageType.ENTREE, t(11, 5, 9, 0, 0), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e, s), Duration.ZERO)).isNull();
	}

	@Test
	void aucunPointage_retardNonCalculable() {
		assertThat(RetardCalcul.calculer(List.of(), Duration.ZERO)).isNull();
	}

	@Test
	void delaiDeGraceRetireDuRetard() {
		var e = p(PointageType.ENTREE, t(11, 5, 8, 12, 40), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ofMinutes(10))).isEqualTo(2);
		assertThat(RetardCalcul.calculer(List.of(e), Duration.ofMinutes(15))).isZero();
	}

	@Test
	void seulLePremierPointageDuJourCompte() {
		var tot = p(PointageType.ENTREE, t(11, 5, 8, 5, 0), t(11, 5, 8, 0, 0));
		var tard = p(PointageType.ENTREE, t(11, 5, 13, 30, 0), t(11, 5, 8, 0, 0));
		assertThat(RetardCalcul.calculer(List.of(tard, tot), Duration.ZERO)).isEqualTo(5);
	}
}
