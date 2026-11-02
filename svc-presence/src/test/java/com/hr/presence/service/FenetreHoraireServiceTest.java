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
import com.hr.presence.service.FenetreHoraireService.DansFenetre;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.service.FenetreHoraireService.HorsFenetre;
import com.hr.presence.service.FenetreHoraireService.Resultat;
import com.hr.presence.service.FenetreHoraireService.SansHoraire;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * D-P11 / D-P12 : fenêtre de pointage du site (horaire ± tolérance, élargie par les heures sup validées).
 * Site de référence : lundi à vendredi 06:00-15:00, tolérance 60 / 60 ; fuseau Africa/Tunis.
 * Le mardi 3 novembre 2026 est ouvert, le samedi 7 novembre 2026 est fermé.
 */
@ExtendWith(MockitoExtension.class)
class FenetreHoraireServiceTest {

	private static final LocalDate MARDI = LocalDate.of(2026, 11, 3);
	private static final LocalDate MERCREDI = LocalDate.of(2026, 11, 4);
	private static final LocalDate SAMEDI = LocalDate.of(2026, 11, 7);
	private static final String COLLABORATEUR = "fiche-1";

	@Mock
	private PresenceHoraireSiteRepository horaireRepository;
	@Mock
	private PresenceHeuresSupJourRepository jourRepository;
	@Mock
	private PresenceHeuresSupDemandeRepository demandeRepository;

	private FenetreHoraireService service;
	private PresenceSite site;

	@BeforeEach
	void setUp() {
		service = new FenetreHoraireService(horaireRepository, jourRepository, demandeRepository);
		site = new PresenceSite();
		site.setId(UUID.randomUUID());
		lenient().when(jourRepository.findPourPointeur(any(), anyCollection(), anyString())).thenReturn(List.of());
		horaires(LocalTime.of(6, 0), LocalTime.of(15, 0), DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
				DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
	}

	private void horaires(LocalTime debut, LocalTime fin, DayOfWeek... jours) {
		List<PresenceHoraireSite> lignes = new ArrayList<>();
		for (DayOfWeek jour : jours) {
			PresenceHoraireSite l = new PresenceHoraireSite();
			l.setSiteId(site.getId());
			l.setJourSemaine(jour.getValue());
			l.setHeureDebut(debut);
			l.setHeureFin(fin);
			lignes.add(l);
		}
		lenient().when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(lignes);
	}

	private static Instant a(LocalDate jour, int heure, int minute, int seconde) {
		return LocalDateTime.of(jour, LocalTime.of(heure, minute, seconde)).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private static Instant a(LocalDate jour, int heure, int minute) {
		return a(jour, heure, minute, 0);
	}

	private Resultat evaluer(Instant t, PointageType type) {
		return service.evaluer(site, COLLABORATEUR, t, type);
	}

	private void heuresSup(StatutHeuresSup statut, int avant, int apres, LocalDate journee) {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setReference("HS-TEST0001");
		d.setStatut(statut);
		d.setOuvertureAvantMinutes(avant);
		d.setOuvertureApresMinutes(apres);
		PresenceHeuresSupJour j = new PresenceHeuresSupJour();
		j.setDemandeId(d.getId());
		j.setSiteId(site.getId());
		j.setJournee(journee);
		j.setCollaborateurId(COLLABORATEUR);
		when(jourRepository.findPourPointeur(eq(site.getId()), anyCollection(), eq(COLLABORATEUR))).thenReturn(List.of(j));
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(d));
	}

	// --- Bornes de la fenêtre (D-P12) ---

	@Test
	void entreeALOuvertureDeLaFenetre_valide_bornesIncluses() {
		Resultat r = evaluer(a(MARDI, 5, 0), PointageType.ENTREE);

		assertThat(r).isInstanceOfSatisfying(DansFenetre.class, d -> {
			assertThat(d.journee()).isEqualTo(MARDI);
			assertThat(d.horaireDebut()).isEqualTo(a(MARDI, 6, 0));
			assertThat(d.horaireFin()).isEqualTo(a(MARDI, 15, 0));
		});
	}

	@Test
	void entreeUneSecondeAvantLOuverture_rejetee_avecLaFenetreDansLeMessage() {
		Resultat r = evaluer(a(MARDI, 4, 59, 59), PointageType.ENTREE);

		assertThat(r).isInstanceOfSatisfying(HorsFenetre.class,
				h -> assertThat(h.message()).isEqualTo("Pointage ouvert de 05:00 à 16:00"));
	}

	@Test
	void sortieA16h00_valide_16h01_rejetee() {
		assertThat(evaluer(a(MARDI, 16, 0), PointageType.SORTIE)).isInstanceOf(DansFenetre.class);
		assertThat(evaluer(a(MARDI, 16, 1), PointageType.SORTIE)).isInstanceOf(HorsFenetre.class);
	}

	@Test
	void comparaisonALaSeconde_les_millisecondes_sont_ignorees() {
		Instant a16h00etDemie = a(MARDI, 16, 0).plusMillis(500);

		assertThat(evaluer(a16h00etDemie, PointageType.SORTIE)).isInstanceOf(DansFenetre.class);
	}

	@Test
	void jourFerme_rejete_siteFermeAujourdhui() {
		Resultat r = evaluer(a(SAMEDI, 10, 0), PointageType.ENTREE);

		assertThat(r).isInstanceOfSatisfying(HorsFenetre.class,
				h -> assertThat(h.message()).isEqualTo("Site fermé aujourd'hui"));
	}

	@Test
	void siteSansHoraire_aucunControle() {
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());

		assertThat(evaluer(a(MARDI, 3, 0), PointageType.ENTREE)).isInstanceOf(SansHoraire.class);
	}

	@Test
	void toleranceDuSite_nulleLueCommeSoixante_puisValeurPropre() {
		assertThat(evaluer(a(MARDI, 5, 0), PointageType.ENTREE)).isInstanceOf(DansFenetre.class);

		site.setToleranceAvantMinutes(15);
		site.setToleranceApresMinutes(90);

		assertThat(evaluer(a(MARDI, 5, 0), PointageType.ENTREE)).isInstanceOf(HorsFenetre.class);
		assertThat(evaluer(a(MARDI, 5, 45), PointageType.ENTREE)).isInstanceOf(DansFenetre.class);
		assertThat(evaluer(a(MARDI, 16, 30), PointageType.SORTIE)).isInstanceOf(DansFenetre.class);
	}

	// --- Horaire de nuit (D-P11.a) ---

	@Test
	void horaireDeNuit_entreeLaVeille_sortieLeLendemain_rattacheeALaVeille() {
		horaires(LocalTime.of(22, 0), LocalTime.of(6, 0), DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
				DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		assertThat(evaluer(a(MARDI, 21, 0), PointageType.ENTREE))
				.isInstanceOfSatisfying(DansFenetre.class, d -> assertThat(d.journee()).isEqualTo(MARDI));
		assertThat(evaluer(a(MERCREDI, 6, 50), PointageType.SORTIE))
				.isInstanceOfSatisfying(DansFenetre.class, d -> {
					assertThat(d.journee()).isEqualTo(MARDI);
					assertThat(d.horaireFin()).isEqualTo(a(MERCREDI, 6, 0));
				});
	}

	@Test
	void fenetresQuiSeTouchent_entreeJourneeLaPlusTardive_sortieLaPlusAncienne() {
		site.setToleranceAvantMinutes(0);
		site.setToleranceApresMinutes(0);
		Map<DayOfWeek, Horaire> jours = new EnumMap<>(DayOfWeek.class);
		List<PresenceHoraireSite> lignes = new ArrayList<>();
		for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)) {
			PresenceHoraireSite l = new PresenceHoraireSite();
			l.setSiteId(site.getId());
			l.setJourSemaine(jour.getValue());
			l.setHeureDebut(jour == DayOfWeek.MONDAY ? LocalTime.of(22, 0) : LocalTime.of(6, 0));
			l.setHeureFin(jour == DayOfWeek.MONDAY ? LocalTime.of(6, 0) : LocalTime.of(14, 0));
			lignes.add(l);
		}
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(lignes);
		Instant instantCommun = a(MARDI, 6, 0);

		assertThat(evaluer(instantCommun, PointageType.ENTREE))
				.isInstanceOfSatisfying(DansFenetre.class, d -> assertThat(d.journee()).isEqualTo(MARDI));
		assertThat(evaluer(instantCommun, PointageType.SORTIE))
				.isInstanceOfSatisfying(DansFenetre.class, d -> assertThat(d.journee()).isEqualTo(LocalDate.of(2026, 11, 2)));
	}

	// --- Heures sup validées (D-P13.f) ---

	@Test
	void heuresSupValidees_elargissentLaFenetreDuBeneficiaire() {
		heuresSup(StatutHeuresSup.VALIDEE, 120, 0, MARDI);

		assertThat(evaluer(a(MARDI, 3, 0), PointageType.ENTREE)).isInstanceOf(DansFenetre.class);
		assertThat(evaluer(a(MARDI, 2, 59), PointageType.ENTREE)).isInstanceOf(HorsFenetre.class);
	}

	@Test
	void sansDemande_memeHeure_rejetee() {
		assertThat(evaluer(a(MARDI, 3, 0), PointageType.ENTREE)).isInstanceOf(HorsFenetre.class);
	}

	@Test
	void heuresSupEnAttente_neLargissentPas() {
		heuresSup(StatutHeuresSup.EN_ATTENTE, 120, 0, MARDI);

		assertThat(evaluer(a(MARDI, 3, 0), PointageType.ENTREE)).isInstanceOf(HorsFenetre.class);
	}

	@Test
	void ouvertureApres_elargitLaSortie() {
		heuresSup(StatutHeuresSup.VALIDEE, 0, 60, MARDI);

		assertThat(evaluer(a(MARDI, 17, 0), PointageType.SORTIE)).isInstanceOf(DansFenetre.class);
		assertThat(evaluer(a(MARDI, 17, 1), PointageType.SORTIE)).isInstanceOf(HorsFenetre.class);
	}

	@Test
	void ouverturesRecherchees_surLIdentifiantDuPointeur() {
		service.evaluer(site, "compte-7", a(MARDI, 8, 0), PointageType.ENTREE);

		org.mockito.Mockito.verify(jourRepository).findPourPointeur(eq(site.getId()), anyCollection(), eq("compte-7"));
	}

	@Test
	void fenetreDuJour_videSiFerme_etElargieParLesHeuresSup() {
		assertThat(service.fenetreDuJour(site, COLLABORATEUR, SAMEDI)).isEqualTo(Optional.empty());
		heuresSup(StatutHeuresSup.VALIDEE, 120, 60, MARDI);

		FenetreHoraireService.Fenetre f = service.fenetreDuJour(site, COLLABORATEUR, MARDI).orElseThrow();

		assertThat(f.ouvertureDebut().toInstant()).isEqualTo(a(MARDI, 3, 0));
		assertThat(f.ouvertureFin().toInstant()).isEqualTo(a(MARDI, 17, 0));
	}

	// --- Chevauchement de l'horaire hebdomadaire (D-P11.a) ---

	private static Map<DayOfWeek, Horaire> semaine(Object... jourDebutFin) {
		Map<DayOfWeek, Horaire> m = new EnumMap<>(DayOfWeek.class);
		for (int i = 0; i < jourDebutFin.length; i += 3) {
			m.put((DayOfWeek) jourDebutFin[i], new Horaire((LocalTime) jourDebutFin[i + 1], (LocalTime) jourDebutFin[i + 2]));
		}
		return m;
	}

	@Test
	void nuitDuLundiEtMardiTropTot_chevauchement422() {
		Map<DayOfWeek, Horaire> jours = semaine(
				DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(6, 0),
				DayOfWeek.TUESDAY, LocalTime.of(6, 30), LocalTime.of(15, 0));

		assertThatThrownBy(() -> FenetreHoraireService.verifierSemaineSansChevauchement(jours, 60, 90))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("HORAIRES_CHEVAUCHANTS");
					assertThat(e.getHttpStatus()).isEqualTo(422);
				});
	}

	@Test
	void fenetresQuiSeTouchentExactement_toleree() {
		Map<DayOfWeek, Horaire> jours = semaine(
				DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(6, 0),
				DayOfWeek.TUESDAY, LocalTime.of(8, 30), LocalTime.of(15, 0));

		// fin lundi 06:00 + 90 = 07:30 ; début mardi 08:30 - 60 = 07:30
		assertThatCode(() -> FenetreHoraireService.verifierSemaineSansChevauchement(jours, 60, 90)).doesNotThrowAnyException();
	}

	@Test
	void dimancheEtLundi_ontVerifiesEnsemble() {
		Map<DayOfWeek, Horaire> jours = semaine(
				DayOfWeek.SUNDAY, LocalTime.of(22, 0), LocalTime.of(6, 0),
				DayOfWeek.MONDAY, LocalTime.of(6, 30), LocalTime.of(15, 0));

		assertThatThrownBy(() -> FenetreHoraireService.verifierSemaineSansChevauchement(jours, 60, 90))
				.isInstanceOfSatisfying(PresenceMetierException.class,
						e -> assertThat(e.getCode()).isEqualTo("HORAIRES_CHEVAUCHANTS"));
	}

	@Test
	void semaineDeJourClassique_pasDeChevauchement() {
		Map<DayOfWeek, Horaire> jours = semaine(
				DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(15, 0),
				DayOfWeek.TUESDAY, LocalTime.of(6, 0), LocalTime.of(15, 0));

		assertThatCode(() -> FenetreHoraireService.verifierSemaineSansChevauchement(jours, 90, 90)).doesNotThrowAnyException();
	}
}
