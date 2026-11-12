package com.hr.presence.service;

import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static com.hr.presence.service.ManuelTestSupport.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** D-P21 à D-P24 : éligibilité d'un refus (statuts, position, plafond, délai, fenêtre) et signaux de vigilance. */
class EligibiliteManuelServiceTest {

	private ManuelTestSupport t;
	private EligibiliteManuelService eligibilite;

	@BeforeEach
	void setUp() {
		t = new ManuelTestSupport();
		eligibilite = t.eligibilite;
		t.siteSansHoraire();
	}

	private EligibiliteManuelService.Verdict verdict() {
		return eligibilite.evaluer(t.rejet, t.site, null, MAINTENANT);
	}

	// ---- D-P21 : statuts ------------------------------------------------------------------------------------------

	@ParameterizedTest
	@EnumSource(value = PointageStatut.class, names = {"REJETE_HORS_ZONE", "REJETE_PRECISION_GPS", "REJETE_VISAGE_NON_RECONNU",
			"REJETE_VIVACITE", "REJETE_VERIFICATION_FACIALE_ABSENTE"})
	void refusRattrapables_sontEligibles(PointageStatut statut) {
		t.rejet.setStatut(statut);

		EligibiliteManuelService.Verdict v = verdict();

		assertThat(v.possible()).isTrue();
		assertThat(v.motif()).isNull();
		assertThat(v.mesures().distance()).isEqualTo(80.0);
	}

	@ParameterizedTest
	@EnumSource(value = PointageStatut.class, names = {"REJETE_POSITION_SIMULEE", "REJETE_HORS_HORAIRE", "REJETE_QR_INVALIDE",
			"REJETE_QR_EXPIRE", "REJETE_QR_REVOQUE", "REJETE_SITE_INACTIF", "REJETE_EMPLACEMENT_ABSENT"})
	void refusDefinitifs_422PointageNonRattrapable(PointageStatut statut) {
		t.rejet.setStatut(statut);

		EligibiliteManuelService.Verdict v = verdict();

		assertThat(v.code()).isEqualTo("POINTAGE_NON_RATTRAPABLE");
		assertThat(v.httpStatus()).isEqualTo(422);
		assertThat(v.message()).startsWith("Ce refus ne peut pas faire l'objet d'un pointage manuel : ");
		assertThat(v.message()).endsWith(v.motif() + ".");
	}

	@Test
	void motifsEnClair_parStatutDefinitif() {
		assertThat(motif(PointageStatut.REJETE_POSITION_SIMULEE)).isEqualTo("position simulée détectée");
		assertThat(motif(PointageStatut.REJETE_HORS_HORAIRE)).isEqualTo("pointage hors de l'horaire du site");
		assertThat(motif(PointageStatut.REJETE_QR_INVALIDE)).isEqualTo("code QR non reconnu, pointez avec le code QR du site");
		assertThat(motif(PointageStatut.REJETE_QR_EXPIRE)).isEqualTo("code QR expiré, prévenez les RH");
		assertThat(motif(PointageStatut.REJETE_QR_REVOQUE)).isEqualTo("code QR révoqué, prévenez les RH");
		assertThat(motif(PointageStatut.REJETE_SITE_INACTIF)).isEqualTo("site inactif, prévenez les RH");
		assertThat(motif(PointageStatut.REJETE_EMPLACEMENT_ABSENT))
				.isEqualTo("emplacement du site non défini, prévenez les RH");
	}

	private String motif(PointageStatut statut) {
		t.rejet.setStatut(statut);
		return verdict().motif();
	}

	@Test
	void pointageAccepte_422PointageNonRejete() {
		for (PointageStatut statut : List.of(PointageStatut.VALIDE, PointageStatut.VALIDE_MANUEL)) {
			t.rejet.setStatut(statut);
			assertThat(verdict().code()).isEqualTo("POINTAGE_NON_REJETE");
		}
	}

	@Test
	void demandeDejaFaite_409_avantLeStatutDefinitif() {
		t.rejet.setStatut(PointageStatut.REJETE_QR_EXPIRE);
		PresenceDemandeManuelle existante = t.demandeEnAttente();

		EligibiliteManuelService.Verdict v = eligibilite.evaluer(t.rejet, t.site, existante, MAINTENANT);

		assertThat(v.code()).isEqualTo("DEMANDE_DEJA_FAITE");
		assertThat(v.httpStatus()).isEqualTo(409);
		assertThat(v.motif()).isEqualTo("une demande a déjà été faite pour ce refus (PM-202611-0007)");
	}

	// ---- D-P21.a : position absente ------------------------------------------------------------------------------

	@Test
	void precisionAbsente_422PositionAbsente() {
		t.rejet.setStatut(PointageStatut.REJETE_PRECISION_GPS);
		t.rejet.setAccuracyMetres(null);

		EligibiliteManuelService.Verdict v = verdict();

		assertThat(v.code()).isEqualTo("POSITION_ABSENTE");
		assertThat(v.motif()).isEqualTo("position GPS absente");
		assertThat(v.message()).startsWith("Position GPS absente : un pointage manuel n'est pas possible.");
	}

	@Test
	void positionAbsente_vaut_aussiPourUnRefusFacial() {
		t.rejet.setStatut(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		t.rejet.setAccuracyMetres(null);

		assertThat(verdict().code()).isEqualTo("POSITION_ABSENTE");
	}

	@Test
	void longitudeNonNumerique_422PositionAbsente() {
		t.rejet.setLongitude(Double.NaN);

		assertThat(verdict().code()).isEqualTo("POSITION_ABSENTE");
	}

	@Test
	void distanceInconnueEtSiteSansEmplacement_422PositionAbsente() {
		t.rejet.setStatut(PointageStatut.REJETE_PRECISION_GPS);
		t.rejet.setDistanceMetres(null);
		t.site.setLatitude(null);
		t.site.setLongitude(null);

		assertThat(verdict().code()).isEqualTo("POSITION_ABSENTE");
	}

	@Test
	void refusDePrecision_distanceRecalculeeDepuisLaPositionEtLEmplacementDuSite() {
		t.rejet.setStatut(PointageStatut.REJETE_PRECISION_GPS);
		t.rejet.setDistanceMetres(null);
		t.rejet.setAccuracyMetres(120.0);
		// environ 55 m au nord du site
		t.rejet.setLatitude(36.8 + 55.0 / 111_320.0);

		EligibiliteManuelService.Verdict v = verdict();

		assertThat(v.possible()).isTrue();
		assertThat(v.mesures().distance()).isBetween(54.0, 56.0);
	}

	// ---- D-P22 : plafond (bornes incluses) et ordre précision -> distance ------------------------------------------

	@Test
	void distanceEgaleAuPlafond_acceptee_pasUnDixiemeDePlus() {
		t.rejet.setDistanceMetres(300.0);
		assertThat(verdict().possible()).isTrue();

		t.rejet.setDistanceMetres(300.1);
		EligibiliteManuelService.Verdict v = verdict();
		assertThat(v.code()).isEqualTo("DISTANCE_TROP_GRANDE");
		assertThat(v.httpStatus()).isEqualTo(422);
		assertThat(v.message()).isEqualTo("Vous étiez à 300,1 m du site : un pointage manuel n'est possible qu'à 300 m au plus. "
				+ "Rendez-vous sur le site et pointez à nouveau.");
		assertThat(v.motif()).isEqualTo("vous étiez à 300,1 m du site, 300 m au plus");
	}

	@Test
	void precisionEgaleAuPlafond_acceptee_pasUnDixiemeDePlus() {
		t.rejet.setAccuracyMetres(300.0);
		assertThat(verdict().possible()).isTrue();

		t.rejet.setAccuracyMetres(300.1);
		EligibiliteManuelService.Verdict v = verdict();
		assertThat(v.code()).isEqualTo("PRECISION_TROP_FAIBLE");
		assertThat(v.motif()).isEqualTo("précision de votre position insuffisante (± 300,1 m, 300 m au plus)");
	}

	@Test
	void precisionControleeAvantLaDistance() {
		t.rejet.setAccuracyMetres(650.0);
		t.rejet.setDistanceMetres(3450.0);

		assertThat(verdict().code()).isEqualTo("PRECISION_TROP_FAIBLE");
	}

	@Test
	void distanceDe3450Metres_formatFrancaisAvecEspace() {
		t.rejet.setDistanceMetres(3450.0);

		assertThat(verdict().motif()).isEqualTo("vous étiez à 3 450,0 m du site, 300 m au plus");
	}

	@Test
	void plafondReglable_100Metres() {
		t.properties.getManuel().setPlafondDistanceMetres(100);
		t.rejet.setDistanceMetres(100.0);
		assertThat(verdict().possible()).isTrue();
		t.rejet.setDistanceMetres(100.5);
		assertThat(verdict().code()).isEqualTo("DISTANCE_TROP_GRANDE");
	}

	// ---- D-P23.a : délai de demande (à la seconde, inclus) ---------------------------------------------------------

	@Test
	void delaiDeDeuxHeuresPile_accepte_uneSecondeDePlus_refuse() {
		t.rejet.setServerTs(MAINTENANT.minus(Duration.ofHours(2)));
		assertThat(verdict().possible()).isTrue();

		t.rejet.setServerTs(MAINTENANT.minus(Duration.ofHours(2)).minusSeconds(1));
		EligibiliteManuelService.Verdict v = verdict();
		assertThat(v.code()).isEqualTo("DELAI_DEMANDE_DEPASSE");
		assertThat(v.httpStatus()).isEqualTo(422);
		assertThat(v.message()).isEqualTo("La demande doit être faite dans les 2 heures qui suivent le pointage refusé "
				+ "(refus du 11/11 à 07:59).");
		assertThat(v.motif()).isEqualTo("délai de demande dépassé (2 heures après le pointage refusé du 11/11 à 07:59)");
	}

	@Test
	void delaiDeDemandeReglable_15MinutesInclus() {
		t.properties.getManuel().setDelaiDemandeMinutes(15);
		t.rejet.setServerTs(MAINTENANT.minus(Duration.ofMinutes(15)));
		assertThat(verdict().possible()).isTrue();
		t.rejet.setServerTs(MAINTENANT.minus(Duration.ofMinutes(15)).minusSeconds(1));
		assertThat(verdict().code()).isEqualTo("DELAI_DEMANDE_DEPASSE");
	}

	// ---- D-P12.c : fenêtre horaire ---------------------------------------------------------------------------------

	@Test
	void heureHorsFenetre_422_apresLeDelai() {
		when(t.fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.HorsFenetre("Site fermé aujourd'hui"));

		EligibiliteManuelService.Verdict v = verdict();

		assertThat(v.code()).isEqualTo("HEURE_HORS_FENETRE");
		assertThat(v.httpStatus()).isEqualTo(422);
	}

	// ---- D-P24.a / D-P24.c : signaux (seuils stricts) --------------------------------------------------------------

	private EligibiliteManuelService.Figees figer(long manuelsDuMois, Duration delai) {
		return eligibilite.figer(t.rejet, t.site, delai, manuelsDuMois);
	}

	@Test
	void aucunSignal_quandToutEstAuSeuil() {
		t.rejet.setDistanceMetres(100.0); // = 2 x rayon (50 m) : pas de signal
		t.rejet.setAccuracyMetres(50.0); // = rayon : pas de signal

		assertThat(figer(2, Duration.ofMinutes(30)).signaux()).isEmpty(); // 30 min pile, 2 < seuil 3
	}

	@Test
	void signalDistance_audessusDeDeuxFoisLeRayon() {
		t.rejet.setDistanceMetres(100.1);
		assertThat(figer(0, Duration.ZERO).signaux()).containsExactly(SignalVigilance.DISTANCE_SUP_2X_RAYON);
	}

	@Test
	void signalPrecision_audessusDuRayon() {
		t.rejet.setAccuracyMetres(50.1);
		assertThat(figer(0, Duration.ZERO).signaux()).containsExactly(SignalVigilance.PRECISION_SUP_RAYON);
	}

	@Test
	void signalSitePapier_etSiteSansModeTraiteCommePapier() {
		t.site.setModeQr(ModeQr.PAPIER);
		assertThat(figer(0, Duration.ZERO).signaux()).containsExactly(SignalVigilance.SITE_QR_PAPIER);

		PresenceSite ancien = new PresenceSite(); // modeQr nul : mode historique, papier
		ancien.setRayonMetres(50);
		assertThat(eligibilite.figer(t.rejet, ancien, Duration.ZERO, 0).modeQr()).isEqualTo(ModeQr.PAPIER);
	}

	@Test
	void signalManuelsDuMois_auSeuilInclus() {
		assertThat(figer(2, Duration.ZERO).signaux()).isEmpty();
		assertThat(figer(3, Duration.ZERO).signaux()).containsExactly(SignalVigilance.MANUELS_MOIS_SEUIL);
	}

	@ParameterizedTest
	@EnumSource(value = PointageStatut.class, names = {"REJETE_VISAGE_NON_RECONNU", "REJETE_VIVACITE",
			"REJETE_VERIFICATION_FACIALE_ABSENTE"})
	void signalRefusFacial(PointageStatut statut) {
		t.rejet.setStatut(statut);
		assertThat(figer(0, Duration.ZERO).signaux()).containsExactly(SignalVigilance.REFUS_FACIAL);
	}

	@Test
	void signalDemandeTardive_audessusDe30Minutes_strict() {
		assertThat(figer(0, Duration.ofMinutes(30)).signaux()).isEmpty();
		assertThat(figer(0, Duration.ofMinutes(30).plusSeconds(1)).signaux()).containsExactly(SignalVigilance.DEMANDE_TARDIVE);
	}

	@Test
	void signaux_calculesPourUnRefusDePositionComme_pourUnFacial() {
		t.rejet.setStatut(PointageStatut.REJETE_VIVACITE);
		t.rejet.setDistanceMetres(101.0);

		assertThat(figer(0, Duration.ZERO).signaux()).containsExactly(SignalVigilance.DISTANCE_SUP_2X_RAYON,
				SignalVigilance.REFUS_FACIAL);
	}

	@Test
	void delaiDemandeEnregistre_enMinutesEntieresTronquees() {
		assertThat(figer(0, Duration.ofSeconds(59 * 60 + 59)).delaiDemandeMinutes()).isEqualTo(59L);
	}

	// ---- Rayon par défaut ------------------------------------------------------------------------------------------

	@Test
	void siteSansRayon_rayonParDefautDuType() {
		PresenceSite s = new PresenceSite();
		s.setRayonMetres(0);
		s.setTypeLieu(com.hr.presence.domain.TypeLieu.EXTERIEUR);
		assertThat(eligibilite.rayonDe(s)).isEqualTo(20);
		s.setTypeLieu(com.hr.presence.domain.TypeLieu.INTERIEUR);
		assertThat(eligibilite.rayonDe(s)).isEqualTo(50);
		PresenceSite sansType = new PresenceSite();
		sansType.setRayonMetres(0);
		assertThat(eligibilite.rayonDe(sansType)).isEqualTo(50);
	}

	@Test
	void formatsDeDelai_D_P24_e() {
		assertThat(LibellesPresence.duree(40)).isEqualTo("40 min");
		assertThat(LibellesPresence.duree(60)).isEqualTo("1 h 00");
		assertThat(LibellesPresence.duree(100)).isEqualTo("1 h 40");
		assertThat(LibellesPresence.duree(26 * 60 + 5)).isEqualTo("26 h 05");
	}
}
