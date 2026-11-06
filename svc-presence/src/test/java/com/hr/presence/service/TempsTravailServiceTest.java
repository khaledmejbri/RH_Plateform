package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.dto.TempsTravailDtos.LigneTempsTravail;
import com.hr.presence.dto.TempsTravailDtos.TempsTravailResponse;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-P17 : orchestration de la vue « Temps de travail » (période, filtres, fusion fiche / compte, totaux sur tout le
 * résultat, noms avec repli). « Aujourd'hui » = jeudi 5 novembre 2026, 10:00 (Africa/Tunis).
 */
class TempsTravailServiceTest {

	private final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceHeuresSupJourRepository heuresSupJourRepository = mock(PresenceHeuresSupJourRepository.class);
	private final PresenceHeuresSupDemandeRepository demandeRepository = mock(PresenceHeuresSupDemandeRepository.class);
	private final ReferentielClient referentiel = mock(ReferentielClient.class);
	private final PresenceProperties properties = new PresenceProperties();
	private final Clock clock = Clock.fixed(
			LocalDateTime.of(2026, 11, 5, 10, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant(), ClockConfig.ZONE_TUNIS);
	private final TempsTravailService service = new TempsTravailService(pointageRepository, siteRepository,
			heuresSupJourRepository, demandeRepository, referentiel, properties, clock);

	private final UUID siteA = UUID.randomUUID();
	private final UUID siteB = UUID.randomUUID();
	private final UUID ficheSarra = UUID.randomUUID();
	private final UUID compteSarra = UUID.randomUUID();
	private final UUID ficheAhmed = UUID.randomUUID();
	private List<PresencePointage> pointages;

	private static Instant t(int mois, int jour, int heure, int minute) {
		return LocalDateTime.of(2026, mois, jour, heure, minute).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private PresencePointage p(UUID collab, UUID site, PointageType type, Instant ts) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId(collab.toString());
		p.setSiteId(site);
		p.setType(type);
		p.setStatut(PointageStatut.VALIDE);
		p.setServerTs(ts);
		return p;
	}

	private void journee(UUID collab, UUID site, int mois, int jour, int hDebut, int hFin) {
		pointages.add(p(collab, site, PointageType.ENTREE, t(mois, jour, hDebut, 0)));
		pointages.add(p(collab, site, PointageType.SORTIE, t(mois, jour, hFin, 0)));
	}

	@BeforeEach
	void setUp() {
		pointages = new ArrayList<>();
		when(pointageRepository.findAll(anySpec(), any(Sort.class))).thenAnswer(i -> pointages);
		PresenceSite a = new PresenceSite();
		a.setId(siteA);
		a.setCode("SIEGE");
		a.setLibelle("Siège");
		PresenceSite b = new PresenceSite();
		b.setId(siteB);
		b.setCode("ATELIER");
		b.setLibelle("Atelier");
		when(siteRepository.findAllById(any())).thenReturn(List.of(a, b));
		when(heuresSupJourRepository.findAll(anyJourSpec())).thenReturn(List.<PresenceHeuresSupJour>of());
		when(referentiel.affectations(any())).thenReturn(List.of(
				new Affectation(ficheSarra, compteSarra, "COL-WEB", "Sarra Khemiri", "Direction Technique", "Logiciel", "Web"),
				new Affectation(ficheAhmed, UUID.randomUUID(), "COL-WEB2", "Ahmed Nasri", "Direction Technique", "Logiciel", "Web")));
	}

	@SuppressWarnings("unchecked")
	private static Specification<PresencePointage> anySpec() {
		return any(Specification.class);
	}

	@SuppressWarnings("unchecked")
	private static Specification<PresenceHeuresSupJour> anyJourSpec() {
		return any(Specification.class);
	}

	private TempsTravailResponse consulter(LocalDate du, LocalDate au) {
		return service.consulter(du, au, null, null, null, null, 0, 50);
	}

	// ---- Période (D-P17.g) ---------------------------------------------------------------------------------------

	@Test
	void periodeDe32Jours_400PeriodeTropLongue_31Jours_acceptee_bornesComprises() {
		assertThatThrownBy(() -> consulter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
				.isInstanceOfSatisfying(PresenceMetierException.class, ex -> {
					assertThat(ex.getHttpStatus()).isEqualTo(400);
					assertThat(ex.getCode()).isEqualTo("PERIODE_TROP_LONGUE");
				});
		assertThat(consulter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).lignes().content()).isEmpty();
	}

	@Test
	void finAvantDebut_400PeriodeInvalide() {
		assertThatThrownBy(() -> consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2)))
				.isInstanceOfSatisfying(PresenceMetierException.class, ex -> {
					assertThat(ex.getHttpStatus()).isEqualTo(400);
					assertThat(ex.getCode()).isEqualTo("PERIODE_INVALIDE");
				});
	}

	@Test
	void plafondDePeriodeConfigurable_parPropriete() {
		properties.getTempsTravail().setPeriodeMaxJours(10);

		assertThatThrownBy(() -> consulter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11)))
				.isInstanceOfSatisfying(PresenceMetierException.class, ex -> assertThat(ex.getCode()).isEqualTo("PERIODE_TROP_LONGUE"));
	}

	@Test
	void sansDates_les7DerniersJoursAujourdhuiCompris() {
		journee(ficheSarra, siteA, 11, 5, 8, 12);  // aujourd'hui
		journee(ficheSarra, siteA, 11, 30 - 24, 8, 12); // 6 novembre : hors période (futur)
		journee(ficheSarra, siteA, 10, 30, 8, 12);  // 30/10 : hors des 7 jours (29/10 à 05/11 = 8 jours, 30/10 = jour -6)
		journee(ficheSarra, siteA, 10, 29, 8, 12);  // jour -7 : hors période

		List<LocalDate> jours = consulter(null, null).lignes().content().stream().map(LigneTempsTravail::jour).toList();

		assertThat(jours).containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 10, 30));
	}

	@Test
	void filtreDeDates_porteSurLaJourneeDeTravail_pasSurLHeureServeur() {
		// SORTIE du 04/11 à 05:00 rattachée à la journée du 03/11 : la période du 04/11 seul ne la ramène pas
		pointages.add(p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 22, 0)));
		pointages.add(p(ficheSarra, siteA, PointageType.SORTIE, t(11, 4, 5, 0)));

		assertThat(consulter(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4)).lignes().content()).isEmpty();
		assertThat(consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)).lignes().content()).hasSize(1);
	}

	// ---- Lignes, fusion, noms ------------------------------------------------------------------------------------

	@Test
	void pointagesSousFicheEtSousCompte_uneSeuleLigne() {
		pointages.add(p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 8, 0)));
		pointages.add(p(compteSarra, siteA, PointageType.SORTIE, t(11, 3, 12, 0)));

		List<LigneTempsTravail> lignes = consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)).lignes().content();

		assertThat(lignes).hasSize(1);
		LigneTempsTravail l = lignes.get(0);
		assertThat(l.collaborateurId()).isEqualTo(ficheSarra.toString());
		assertThat(l.collaborateurNom()).isEqualTo("Sarra Khemiri");
		assertThat(l.collaborateurMatricule()).isEqualTo("COL-WEB");
		assertThat(l.siteCode()).isEqualTo("SIEGE");
		assertThat(l.minutesTravaillees()).isEqualTo(240);
		assertThat(l.etat()).isEqualTo(EtatJourHeuresSup.COMPLET);
		assertThat(l.paires()).singleElement().satisfies(pa -> {
			assertThat(pa.entree()).isEqualTo("2026-11-03T08:00:00+01:00");
			assertThat(pa.sortie()).isEqualTo("2026-11-03T12:00:00+01:00");
			assertThat(pa.minutes()).isEqualTo(240);
		});
		assertThat(l.premiereEntree()).isEqualTo("2026-11-03T08:00:00+01:00");
		assertThat(l.directionLibelle()).isEqualTo("Direction Technique");
		assertThat(l.departementLibelle()).isEqualTo("Logiciel");
		assertThat(l.serviceLibelle()).isEqualTo("Web");
	}

	@Test
	void deuxSitesLeMemeJour_deuxLignes() {
		journee(ficheSarra, siteA, 11, 3, 8, 10);
		journee(ficheSarra, siteB, 11, 3, 11, 13);

		assertThat(consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)).lignes().content())
				.extracting(LigneTempsTravail::siteCode).containsExactlyInAnyOrder("SIEGE", "ATELIER");
	}

	@Test
	void referentielEnPanne_laListeResteServieSansNom() {
		when(referentiel.affectations(any())).thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "KO"));
		journee(ficheSarra, siteA, 11, 3, 8, 12);

		List<LigneTempsTravail> lignes = consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)).lignes().content();

		assertThat(lignes).hasSize(1);
		assertThat(lignes.get(0).collaborateurNom()).isNull();
		assertThat(lignes.get(0).directionLibelle()).isNull();
		assertThat(lignes.get(0).serviceLibelle()).isNull();
		assertThat(lignes.get(0).collaborateurId()).isEqualTo(ficheSarra.toString());
	}

	@Test
	void tri_jourDecroissantPuisNom() {
		journee(ficheSarra, siteA, 11, 3, 8, 12);
		journee(ficheAhmed, siteA, 11, 3, 8, 12);
		journee(ficheSarra, siteA, 11, 4, 8, 12);

		List<LigneTempsTravail> lignes = consulter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)).lignes().content();

		assertThat(lignes).extracting(LigneTempsTravail::jour, LigneTempsTravail::collaborateurNom).containsExactly(
				org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 4), "Sarra Khemiri"),
				org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 3), "Ahmed Nasri"),
				org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 3), "Sarra Khemiri"));
	}

	// ---- Filtres -------------------------------------------------------------------------------------------------

	@Test
	void filtreEtat_incompletSeulement() {
		journee(ficheSarra, siteA, 11, 3, 8, 12);
		pointages.add(p(ficheAhmed, siteA, PointageType.ENTREE, t(11, 3, 8, 0))); // sans sortie, il y a plus de 16 h

		TempsTravailResponse r = service.consulter(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null, null, null,
				EtatJourHeuresSup.INCOMPLET, 0, 50);

		assertThat(r.lignes().content()).singleElement().satisfies(l -> {
			assertThat(l.collaborateurNom()).isEqualTo("Ahmed Nasri");
			assertThat(l.etat()).isEqualTo(EtatJourHeuresSup.INCOMPLET);
			assertThat(l.minutesTravaillees()).isZero();
			assertThat(l.orphelins()).singleElement().satisfies(o -> assertThat(o.type()).isEqualTo("ENTREE"));
		});
		assertThat(r.nbIncompletes()).isEqualTo(1);
	}

	@Test
	void filtreUnite_sansMembre_listeVide_etAucunePointageLue() {
		when(referentiel.membresDeUnite(any())).thenReturn(List.of());

		TempsTravailResponse r = service.consulter(null, null, null, null, UUID.randomUUID(), null, 0, 50);

		assertThat(r.lignes().content()).isEmpty();
		assertThat(r.totalMinutes()).isZero();
		verifyNoInteractions(pointageRepository);
	}

	@Test
	void filtreUnite_referentielEnPanne_503() {
		when(referentiel.membresDeUnite(any())).thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "KO"));

		assertThatThrownBy(() -> service.consulter(null, null, null, null, UUID.randomUUID(), null, 0, 50))
				.isInstanceOfSatisfying(PresenceMetierException.class, ex -> assertThat(ex.getHttpStatus()).isEqualTo(503));
	}

	@Test
	void filtreUniteEtCollaborateur_sansIntersection_listeVide() {
		when(referentiel.membresDeUnite(any())).thenReturn(List.of(new ReferentielClient.MembreUnite(ficheAhmed, null)));
		when(referentiel.affectations(List.of(ficheSarra))).thenReturn(List.of(
				new Affectation(ficheSarra, compteSarra, "COL-WEB", "Sarra Khemiri", null, null, null)));

		TempsTravailResponse r = service.consulter(null, null, ficheSarra, null, UUID.randomUUID(), null, 0, 50);

		assertThat(r.lignes().content()).isEmpty();
		verifyNoInteractions(pointageRepository);
	}

	// ---- Totaux et synthèse (D-P17.h) ----------------------------------------------------------------------------

	@Test
	void troisJournees8h_syntheseTroisJoursEt24h_totauxIdentiquesQuelleQueSoitLaPage() {
		journee(ficheSarra, siteA, 11, 2, 8, 16);
		journee(ficheSarra, siteA, 11, 3, 8, 16);
		journee(ficheSarra, siteA, 11, 4, 8, 16);
		LocalDate du = LocalDate.of(2026, 11, 1);
		LocalDate au = LocalDate.of(2026, 11, 5);

		TempsTravailResponse page0 = service.consulter(du, au, null, null, null, null, 0, 1);
		TempsTravailResponse page2 = service.consulter(du, au, null, null, null, null, 2, 1);

		for (TempsTravailResponse r : List.of(page0, page2)) {
			assertThat(r.totalMinutes()).isEqualTo(24 * 60);
			assertThat(r.nbLignes()).isEqualTo(3);
			assertThat(r.synthese()).singleElement().satisfies(sy -> {
				assertThat(sy.nbJours()).isEqualTo(3);
				assertThat(sy.minutes()).isEqualTo(24 * 60);
				assertThat(sy.collaborateurMatricule()).isEqualTo("COL-WEB");
			});
		}
		assertThat(page0.lignes().content()).hasSize(1);
		assertThat(page0.lignes().totalElements()).isEqualTo(3);
		assertThat(page0.lignes().totalPages()).isEqualTo(3);
		assertThat(page2.lignes().number()).isEqualTo(2);
		assertThat(consulterPageHorsLimites(du, au)).isEmpty();
	}

	private List<LigneTempsTravail> consulterPageHorsLimites(LocalDate du, LocalDate au) {
		journee(ficheSarra, siteA, 11, 2, 8, 16);
		return service.consulter(du, au, null, null, null, null, 9, 50).lignes().content();
	}

	@Test
	void totalPeutDepasser24h_enMinutes() {
		for (int jour = 1; jour <= 4; jour++) {
			journee(ficheSarra, siteA, 11, jour, 8, 16);
		}

		assertThat(consulter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)).totalMinutes()).isEqualTo(4 * 8 * 60);
	}

	// ---- Pointages retenus (D-P17.a) -----------------------------------------------------------------------------

	@Test
	void seulsLesStatutsValideEtValideManuelSontRetenus() {
		assertThat(TempsTravailService.STATUTS_RETENUS)
				.isEqualTo(EnumSet.of(PointageStatut.VALIDE, PointageStatut.VALIDE_MANUEL));
		assertThat(TempsTravailService.STATUTS_RETENUS).noneMatch(s -> s.name().startsWith("REJETE"));
	}

	// ---- Lot 0 UAT : D-P18.d (TPS-2) et D-P18.b (PTG-4) -----------------------------------------------------------

	private TempsTravailService serviceA(int mois, int jour, int heure, int minute) {
		Clock c = Clock.fixed(t(mois, jour, heure, minute), ClockConfig.ZONE_TUNIS);
		return new TempsTravailService(pointageRepository, siteRepository, heuresSupJourRepository, demandeRepository,
				referentiel, properties, c);
	}

	private LigneTempsTravail uniqueLigne(TempsTravailService s) {
		TempsTravailResponse r = s.consulter(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5), null, null, null, null, 0, 50);
		assertThat(r.lignes().content()).hasSize(1);
		return r.lignes().content().get(0);
	}

	@Test
	void enCoursExposeLInstantDeBasculeEtIncompletUneMinuteApres() {
		// ENTREE orpheline à J 19:13 (4/11) sur site sans horaire : fenêtre fermée à J+1 11:13
		pointages.add(p(ficheSarra, siteA, PointageType.ENTREE, t(11, 4, 19, 13)));

		LigneTempsTravail avant = uniqueLigne(serviceA(11, 5, 11, 12));
		assertThat(avant.etat()).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(avant.enCoursJusquA()).isEqualTo("2026-11-05T11:13:00+01:00");

		LigneTempsTravail pile = uniqueLigne(serviceA(11, 5, 11, 13));
		assertThat(pile.etat()).isEqualTo(EtatJourHeuresSup.EN_COURS);

		LigneTempsTravail apres = uniqueLigne(serviceA(11, 5, 11, 14));
		assertThat(apres.etat()).isEqualTo(EtatJourHeuresSup.INCOMPLET);
		assertThat(apres.enCoursJusquA()).isNull();
	}

	@Test
	void journeeCompleteSansInstantDeBascule() {
		journee(ficheSarra, siteA, 11, 4, 8, 17);

		LigneTempsTravail l = uniqueLigne(service);

		assertThat(l.etat()).isEqualTo(EtatJourHeuresSup.COMPLET);
		assertThat(l.enCoursJusquA()).isNull();
	}

	private com.hr.presence.dto.PageResponse<com.hr.presence.dto.PointageResponse> page(PresencePointage... ps) {
		List<com.hr.presence.dto.PointageResponse> contenu = new ArrayList<>();
		for (PresencePointage p : ps) {
			contenu.add(new com.hr.presence.dto.PointageResponse(p.getId(), p.getStatut(), p.getType(), p.getServerTs(),
					p.getSiteId(), null, null, null, p.getCollaborateurId(), null, null, null, null, null, p.getJournee()));
		}
		return new com.hr.presence.dto.PageResponse<>(contenu, 0, 20, contenu.size(), 1);
	}

	private void contexte(PresencePointage... ps) {
		pointages.addAll(List.of(ps));
		when(pointageRepository.findAllById(any())).thenReturn(List.of(ps));
	}

	@Test
	void sequenceEntreeEntreeSortie_seulementLaPremiereEntreeEstSansSortie() {
		PresencePointage e1 = p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 8, 0));
		PresencePointage e2 = p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 9, 0));
		PresencePointage s = p(ficheSarra, siteA, PointageType.SORTIE, t(11, 3, 17, 0));
		contexte(e1, e2, s);

		var annotee = service.annoterSequences(page(e1, e2, s));

		assertThat(annotee.content().get(0).etatSequence())
				.isEqualTo(com.hr.presence.domain.EtatSequencePointage.ENTREE_SANS_SORTIE);
		assertThat(annotee.content().get(1).etatSequence()).isNull();
		assertThat(annotee.content().get(2).etatSequence()).isNull();
	}

	@Test
	void sequenceSortieSeule_sortieSansEntree() {
		PresencePointage s = p(ficheSarra, siteA, PointageType.SORTIE, t(11, 3, 17, 0));
		contexte(s);

		var annotee = service.annoterSequences(page(s));

		assertThat(annotee.content().get(0).etatSequence())
				.isEqualTo(com.hr.presence.domain.EtatSequencePointage.SORTIE_SANS_ENTREE);
	}

	@Test
	void sequenceEntreeOuverteDansSaFenetre_enCoursPuisOrphelineApres() {
		PresencePointage e = p(ficheSarra, siteA, PointageType.ENTREE, t(11, 4, 19, 13));
		contexte(e);

		// 5/11 10:00 : avant 11:13 → EN_COURS (pas une anomalie)
		assertThat(service.annoterSequences(page(e)).content().get(0).etatSequence())
				.isEqualTo(com.hr.presence.domain.EtatSequencePointage.EN_COURS);
		// 5/11 11:14 : fenêtre fermée → orpheline
		assertThat(serviceA(11, 5, 11, 14).annoterSequences(page(e)).content().get(0).etatSequence())
				.isEqualTo(com.hr.presence.domain.EtatSequencePointage.ENTREE_SANS_SORTIE);
	}

	@Test
	void sequenceLesPointagesRejetesNeportentJamaisDePastille() {
		PresencePointage rejete = p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 8, 0));
		rejete.setStatut(PointageStatut.REJETE_HORS_ZONE);
		contexte();

		var annotee = service.annoterSequences(page(rejete));

		assertThat(annotee.content().get(0).etatSequence()).isNull();
	}

	@Test
	void sequencePaireApprieeSansPastille() {
		PresencePointage e = p(ficheSarra, siteA, PointageType.ENTREE, t(11, 3, 8, 0));
		PresencePointage s = p(ficheSarra, siteA, PointageType.SORTIE, t(11, 3, 12, 0));
		contexte(e, s);

		var annotee = service.annoterSequences(page(e, s));

		assertThat(annotee.content()).allSatisfy(r -> assertThat(r.etatSequence()).isNull());
	}
}
