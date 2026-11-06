package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.service.TempsTravailCalcul.JourTravail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-P17.b à D-P17.e : journée de travail, appariement, total tronqué à la minute, état du jour. Mardi 3 novembre 2026,
 * Africa/Tunis. Un site « avec horaire » porte la journée et la fin d'horaire sur le pointage ; « sans horaire » non.
 */
class TempsTravailCalculTest {

	private static final LocalDate J3 = LocalDate.of(2026, 11, 3);
	private static final UUID SITE_A = UUID.randomUUID();
	private static final UUID SITE_B = UUID.randomUUID();
	private static final int DUREE_MAX_H = 16;

	private static Instant t(int jour, int heure, int minute, int seconde) {
		return LocalDateTime.of(2026, 11, jour, heure, minute, seconde).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private static PresencePointage p(String collab, UUID site, PointageType type, PointageStatut statut, Instant ts) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId(collab);
		p.setSiteId(site);
		p.setType(type);
		p.setStatut(statut);
		p.setServerTs(ts);
		return p;
	}

	private static PresencePointage e(Instant ts) {
		return p("fiche-1", SITE_A, PointageType.ENTREE, PointageStatut.VALIDE, ts);
	}

	private static PresencePointage s(Instant ts) {
		return p("fiche-1", SITE_A, PointageType.SORTIE, PointageStatut.VALIDE, ts);
	}

	private static PresencePointage avecHoraire(PresencePointage p, LocalDate journee, Instant fin) {
		p.setJournee(journee);
		p.setHoraireFinTs(fin);
		return p;
	}

	private static List<JourTravail> regrouper(PresencePointage... pointages) {
		return TempsTravailCalcul.regrouper(List.of(pointages), id -> id, DUREE_MAX_H);
	}

	@Test
	void deuxPairesLeMemeJour_pauseNonComptee_premiereEntreeEtDerniereSortie() {
		List<JourTravail> jours = regrouper(e(t(3, 8, 2, 0)), s(t(3, 12, 1, 0)), e(t(3, 13, 0, 0)), s(t(3, 17, 5, 0)));

		assertThat(jours).hasSize(1);
		JourTravail j = jours.get(0);
		assertThat(j.paires()).hasSize(2);
		assertThat(j.minutes()).isEqualTo(8 * 60 + 4);
		assertThat(j.premiereEntree()).isEqualTo(t(3, 8, 2, 0));
		assertThat(j.derniereSortie()).isEqualTo(t(3, 17, 5, 0));
		assertThat(j.orphelins()).isEmpty();
		assertThat(j.jour()).isEqualTo(J3);
	}

	@Test
	void pointagesFournisDansLeDesordre_sontTriesParHeureServeur() {
		JourTravail j = regrouper(s(t(3, 12, 0, 0)), e(t(3, 8, 0, 0))).get(0);

		assertThat(j.minutes()).isEqualTo(240);
		assertThat(j.orphelins()).isEmpty();
	}

	@Test
	void troisHeures59m59s_tronqueAMinute_pasArrondiAuQuartDHeure() {
		JourTravail j = regrouper(e(t(3, 8, 0, 0)), s(t(3, 11, 59, 59))).get(0);

		assertThat(j.secondes()).isEqualTo(3 * 3600 + 59 * 60 + 59);
		assertThat(j.minutes()).isEqualTo(3 * 60 + 59);
	}

	@Test
	void secondesDesPairesAdditionneesAvantTroncature() {
		// 2 x 1 h 00 min 30 s = 2 h 01 min : la troncature porte sur le total, pas sur chaque paire
		JourTravail j = regrouper(e(t(3, 8, 0, 0)), s(t(3, 9, 0, 30)), e(t(3, 10, 0, 0)), s(t(3, 11, 0, 30))).get(0);

		assertThat(j.minutes()).isEqualTo(121);
	}

	@Test
	void entreeEntreeSortie_laDeuxiemeEntreeFormeLaPaire_laPremiereEstOrpheline() {
		PresencePointage premiere = e(t(3, 8, 0, 0));
		JourTravail j = regrouper(premiere, e(t(3, 8, 30, 0)), s(t(3, 12, 0, 0))).get(0);

		assertThat(j.paires()).hasSize(1);
		assertThat(j.minutes()).isEqualTo(210);
		assertThat(j.orphelins()).containsExactly(premiere);
	}

	@Test
	void sortieSansEntree_orpheline_totalZero() {
		JourTravail j = regrouper(s(t(3, 12, 0, 0))).get(0);

		assertThat(j.paires()).isEmpty();
		assertThat(j.orphelins()).hasSize(1);
		assertThat(j.minutes()).isZero();
		assertThat(j.derniereSortie()).isEqualTo(t(3, 12, 0, 0));
	}

	@Test
	void entreeSurSiteAEtSortieSurSiteB_deuxLignesChacuneAvecUnOrphelin() {
		PresencePointage entree = e(t(3, 8, 0, 0));
		PresencePointage sortie = p("fiche-1", SITE_B, PointageType.SORTIE, PointageStatut.VALIDE, t(3, 12, 0, 0));

		List<JourTravail> jours = regrouper(entree, sortie);

		assertThat(jours).hasSize(2);
		assertThat(jours).allSatisfy(j -> {
			assertThat(j.orphelins()).hasSize(1);
			assertThat(j.minutes()).isZero();
		});
		assertThat(jours).extracting(JourTravail::siteId).containsExactlyInAnyOrder(SITE_A, SITE_B);
	}

	@Test
	void pointageManuelValide_formeLaPaire_estCompteEtMarqueManuel() {
		PresencePointage sortie = p("fiche-1", SITE_A, PointageType.SORTIE, PointageStatut.VALIDE_MANUEL, t(3, 12, 0, 0));

		JourTravail j = regrouper(e(t(3, 8, 0, 0)), sortie).get(0);

		assertThat(j.minutes()).isEqualTo(240);
		assertThat(j.manuel()).isTrue();
		assertThat(TempsTravailCalcul.estManuel(sortie)).isTrue();
	}

	@Test
	void pointageSansManuel_nEstPasMarqueManuel() {
		assertThat(regrouper(e(t(3, 8, 0, 0)), s(t(3, 9, 0, 0))).get(0).manuel()).isFalse();
	}

	@Test
	void collaborateursDistincts_lignesDistinctes_etFicheEtCompteFusionnesParLaCle() {
		PresencePointage parFiche = p("fiche-1", SITE_A, PointageType.ENTREE, PointageStatut.VALIDE, t(3, 8, 0, 0));
		PresencePointage parCompte = p("compte-1", SITE_A, PointageType.SORTIE, PointageStatut.VALIDE, t(3, 12, 0, 0));
		PresencePointage autre = p("fiche-2", SITE_A, PointageType.ENTREE, PointageStatut.VALIDE, t(3, 9, 0, 0));

		List<JourTravail> jours = TempsTravailCalcul.regrouper(List.of(parFiche, parCompte, autre),
				id -> id.equals("compte-1") ? "fiche-1" : id, DUREE_MAX_H);

		assertThat(jours).hasSize(2);
		JourTravail fusionne = jours.stream().filter(j -> j.collaborateur().equals("fiche-1")).findFirst().orElseThrow();
		assertThat(fusionne.paires()).hasSize(1);
		assertThat(fusionne.minutes()).isEqualTo(240);
	}

	@Test
	void posteDeNuitAvecHoraire_journeeEnregistreeSurLePointage_uneSeuleLigne() {
		PresencePointage entree = avecHoraire(e(t(3, 21, 45, 0)), J3, t(4, 6, 0, 0));
		PresencePointage sortie = avecHoraire(s(t(4, 6, 10, 0)), J3, t(4, 6, 0, 0));

		List<JourTravail> jours = regrouper(entree, sortie);

		assertThat(jours).hasSize(1);
		assertThat(jours.get(0).jour()).isEqualTo(J3);
		assertThat(jours.get(0).minutes()).isEqualTo(8 * 60 + 25);
		assertThat(jours.get(0).derniereSortie()).isEqualTo(t(4, 6, 10, 0));
	}

	@Test
	void siteSansHoraire_sortieApresMinuitDansLes16h_rattacheeALEntreeDeLaVeille() {
		JourTravail j = regrouper(e(t(3, 22, 0, 0)), s(t(4, 5, 0, 0))).get(0);

		assertThat(j.jour()).isEqualTo(J3);
		assertThat(j.minutes()).isEqualTo(7 * 60);
		assertThat(j.orphelins()).isEmpty();
	}

	@Test
	void siteSansHoraire_sortieExactement16hApres_estRattachee() {
		List<JourTravail> jours = regrouper(e(t(3, 12, 0, 0)), s(t(4, 4, 0, 0)));

		assertThat(jours).hasSize(1);
		assertThat(jours.get(0).minutes()).isEqualTo(16 * 60);
	}

	@Test
	void siteSansHoraire_sortie17hApres_deuxLignesIncompletes_totalZero() {
		List<JourTravail> jours = regrouper(e(t(3, 12, 0, 0)), s(t(4, 5, 0, 0)));

		assertThat(jours).hasSize(2);
		assertThat(jours).allSatisfy(j -> {
			assertThat(j.orphelins()).hasSize(1);
			assertThat(j.minutes()).isZero();
		});
		assertThat(jours).extracting(JourTravail::jour).containsExactlyInAnyOrder(J3, LocalDate.of(2026, 11, 4));
	}

	// ---- État (D-P17.e) ------------------------------------------------------------------------------------------

	@Test
	void aucunOrphelin_complet() {
		JourTravail j = regrouper(e(t(3, 8, 0, 0)), s(t(3, 12, 0, 0))).get(0);

		assertThat(TempsTravailCalcul.etat(j, t(10, 0, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.COMPLET);
	}

	@Test
	void entreeSansSortie_avecHoraire_enCoursAvantFinPlusTolerance_incompletApres() {
		PresencePointage entree = avecHoraire(e(t(3, 8, 0, 0)), J3, t(3, 17, 0, 0));
		JourTravail j = regrouper(entree).get(0);

		// fin 17:00 + 60 min = 18:00
		assertThat(TempsTravailCalcul.etat(j, t(3, 18, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(TempsTravailCalcul.etat(j, t(3, 18, 0, 1), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void heuresSupValideesApresLHoraire_repoussentLaFermetureDeLaFenetre() {
		PresencePointage entree = avecHoraire(e(t(3, 8, 0, 0)), J3, t(3, 17, 0, 0));
		JourTravail j = regrouper(entree).get(0);

		// fin 17:00 + 60 min de tolérance + 120 min d'ouverture = 20:00
		assertThat(TempsTravailCalcul.etat(j, t(3, 19, 59, 0), 60, 120, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(TempsTravailCalcul.etat(j, t(3, 20, 1, 0), 60, 120, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void entreeSansSortie_sansHoraire_enCoursMoinsDe16h_incompletAuDela() {
		JourTravail j = regrouper(e(t(3, 8, 0, 0))).get(0);

		assertThat(j.minutes()).isZero();
		assertThat(TempsTravailCalcul.etat(j, t(3, 23, 59, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(TempsTravailCalcul.etat(j, t(4, 0, 1, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void sortieOrphelineSeule_sansHoraire_incompletDEmblee() {
		JourTravail j = regrouper(s(t(3, 12, 0, 0))).get(0);

		assertThat(TempsTravailCalcul.etat(j, t(3, 12, 1, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void entreeEntreeSortie_incompletUneFoisLaFenetreFermee_pairesQuandMemeCompteesEnCours() {
		PresencePointage premiere = avecHoraire(e(t(3, 8, 0, 0)), J3, t(3, 17, 0, 0));
		PresencePointage deuxieme = avecHoraire(e(t(3, 8, 30, 0)), J3, t(3, 17, 0, 0));
		PresencePointage sortie = avecHoraire(s(t(3, 12, 0, 0)), J3, t(3, 17, 0, 0));
		JourTravail j = regrouper(premiere, deuxieme, sortie).get(0);

		assertThat(j.minutes()).isEqualTo(210);
		assertThat(TempsTravailCalcul.etat(j, t(3, 13, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(TempsTravailCalcul.etat(j, t(3, 19, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void entreeRemplaceeParUneSuivante_sansHoraire_nEstPasEnCoursToutelaNuit() {
		JourTravail j = regrouper(e(t(3, 8, 0, 0)), e(t(3, 8, 30, 0)), s(t(3, 12, 0, 0))).get(0);

		// l'ENTREE de 08:00 est orpheline mais remplacée : fenêtre fermée dès la SORTIE, pas ouverte 16 h
		assertThat(TempsTravailCalcul.etat(j, t(3, 13, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void entreeFinaleOrphelineApresUnePaire_sansHoraire_gardeLaFenetreOuverte16h() {
		JourTravail j = regrouper(e(t(3, 8, 0, 0)), s(t(3, 12, 0, 0)), e(t(3, 13, 0, 0))).get(0);

		assertThat(TempsTravailCalcul.etat(j, t(3, 20, 0, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(TempsTravailCalcul.etat(j, t(4, 5, 1, 0), 60, 0, DUREE_MAX_H)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
	}

	@Test
	void unJourSansPointage_neProduitAucuneLigne() {
		assertThat(new ArrayList<>(TempsTravailCalcul.regrouper(List.of(), id -> id, DUREE_MAX_H))).isEmpty();
	}
}
