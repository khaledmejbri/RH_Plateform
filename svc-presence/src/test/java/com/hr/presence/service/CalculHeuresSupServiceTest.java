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
import com.hr.presence.service.CalculHeuresSupService.ResultatJour;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-P14 : heures comptées. Site de référence 06:00-15:00 (instantané porté par les pointages), le mardi 3 novembre 2026.
 * La tolérance n'est jamais comptée ; arrondi de 15 minutes vers le bas ; appariement par paires successives.
 */
class CalculHeuresSupServiceTest {

	private static final LocalDate JOUR = LocalDate.of(2026, 11, 3);

	private static Instant a(int heure, int minute) {
		return LocalDateTime.of(JOUR, LocalTime.of(heure, minute)).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private static PresencePointage p(PointageType type, int heure, int minute) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId("fiche-1");
		p.setType(type);
		p.setStatut(PointageStatut.VALIDE);
		p.setServerTs(a(heure, minute));
		p.setJournee(JOUR);
		p.setHoraireDebutTs(a(6, 0));
		p.setHoraireFinTs(a(15, 0));
		return p;
	}

	private static PresencePointage entree(int h, int m) {
		return p(PointageType.ENTREE, h, m);
	}

	private static PresencePointage sortie(int h, int m) {
		return p(PointageType.SORTIE, h, m);
	}

	private static ResultatJour calculer(int avant, int apres, PresencePointage... pointages) {
		return CalculHeuresSupService.calculerJour(List.of(pointages), avant, apres);
	}

	@Test
	void entree0410Sortie1540_deuxHeuresAvant_donneUneHeure45_laSortieDansLaToleranceNeComptePas() {
		ResultatJour r = calculer(120, 0, entree(4, 10), sortie(15, 40));

		// 04:10 à 06:00 = 1 h 50, arrondi vers le bas à 1 h 45
		assertThat(r.minutes()).isEqualTo(105);
		assertThat(r.orphelin()).isFalse();
	}

	@Test
	void entree0300Sortie1500_laToleranceAvantNeCompteJamais() {
		assertThat(calculer(120, 0, entree(3, 0), sortie(15, 0)).minutes()).isEqualTo(120);
	}

	@Test
	void uneHeureApresEnPlus_sortie1620_donneTroisHeures() {
		assertThat(calculer(120, 60, entree(3, 0), sortie(16, 20)).minutes()).isEqualTo(180);
	}

	@Test
	void entreeSansSortie_rienNeCompte_jourOrphelin() {
		ResultatJour r = calculer(120, 0, entree(4, 10));

		assertThat(r.minutes()).isZero();
		assertThat(r.orphelin()).isTrue();
	}

	@Test
	void sansHeuresSupValidees_entree0500_zeroCompte() {
		assertThat(calculer(0, 0, entree(5, 0), sortie(15, 0)).minutes()).isZero();
	}

	@Test
	void entreeManuelleA0430_suivieDUneSortieValide_uneHeure30() {
		PresencePointage manuelle = entree(4, 30);
		manuelle.setStatut(PointageStatut.VALIDE_MANUEL);

		assertThat(calculer(120, 0, manuelle, sortie(15, 0)).minutes()).isEqualTo(90);
	}

	@Test
	void entreeEntreeSortie_laDerniereEntreeFormeLaPaire_laPremiereEstOrpheline() {
		ResultatJour r = calculer(180, 0, entree(3, 0), entree(4, 30), sortie(6, 0));

		// seule la paire 04:30 - 06:00 compte : 1 h 30 ; la première ENTREE est ignorée
		assertThat(r.minutes()).isEqualTo(90);
		assertThat(r.orphelin()).isTrue();
		assertThat(r.premiereEntree()).isEqualTo(a(3, 0));
		assertThat(r.derniereSortie()).isEqualTo(a(6, 0));
	}

	@Test
	void sortieSansEntree_ignoree_etJourOrphelin() {
		ResultatJour r = calculer(120, 0, sortie(5, 0), entree(4, 0), sortie(6, 0));

		assertThat(r.minutes()).isEqualTo(120);
		assertThat(r.orphelin()).isTrue();
	}

	@Test
	void lesPairesSAdditionnent() {
		ResultatJour r = calculer(120, 0, entree(4, 0), sortie(5, 0), entree(5, 30), sortie(6, 30));

		// 04:00-05:00 = 60 min ; 05:30-06:00 = 30 min dans la plage avant
		assertThat(r.minutes()).isEqualTo(90);
		assertThat(r.orphelin()).isFalse();
	}

	@Test
	void sansInstantaneDHoraire_rienNeCompte() {
		PresencePointage e = entree(4, 0);
		e.setHoraireDebutTs(null);
		e.setHoraireFinTs(null);

		assertThat(calculer(120, 0, e, sortie(6, 0)).minutes()).isZero();
	}

	@Test
	void orphelinEtFenetreEncoreOuverte_enCours_sinonIncomplet() {
		Instant fermeture = a(16, 0);

		assertThat(CalculHeuresSupService.etat(true, a(10, 0), fermeture)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(CalculHeuresSupService.etat(true, fermeture, fermeture)).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(CalculHeuresSupService.etat(true, a(16, 1), fermeture)).isEqualTo(EtatJourHeuresSup.INCOMPLET);
		assertThat(CalculHeuresSupService.etat(false, a(18, 0), fermeture)).isEqualTo(EtatJourHeuresSup.COMPLET);
	}

	@Test
	void formatHhMm() {
		assertThat(CalculHeuresSupService.formaterHeures(180)).isEqualTo("03:00");
		assertThat(CalculHeuresSupService.formaterHeures(105)).isEqualTo("01:45");
	}

	// --- Chargement par lot, période et état du jour ---

	private final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceHoraireSiteRepository horaireRepository = mock(PresenceHoraireSiteRepository.class);
	private final PresenceHeuresSupDemandeRepository demandeRepository = mock(PresenceHeuresSupDemandeRepository.class);
	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository = mock(PresenceHeuresSupBeneficiaireRepository.class);

	private CalculHeuresSupService service(Instant maintenant) {
		return new CalculHeuresSupService(pointageRepository, siteRepository, horaireRepository, demandeRepository,
				beneficiaireRepository, new PresenceProperties(), Clock.fixed(maintenant, ClockConfig.ZONE_TUNIS));
	}

	private PresenceHeuresSupJour ligne(PresenceSite site, PresenceHeuresSupDemande d, PresenceHeuresSupBeneficiaire b) {
		PresenceHeuresSupJour l = new PresenceHeuresSupJour();
		l.setDemandeId(d.getId());
		l.setBeneficiaireId(b.getId());
		l.setCollaborateurId("fiche-1");
		l.setSiteId(site.getId());
		l.setJournee(JOUR);
		return l;
	}

	private PresenceSite site() {
		PresenceSite s = new PresenceSite();
		s.setId(UUID.randomUUID());
		s.setLibelle("Siège");
		return s;
	}

	private PresenceHeuresSupDemande demande(StatutHeuresSup statut) {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setReference("HS-0000ABCD");
		d.setStatut(statut);
		d.setOuvertureAvantMinutes(120);
		return d;
	}

	private PresenceHeuresSupBeneficiaire beneficiaire() {
		PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
		b.setId(UUID.randomUUID());
		b.setCollaborateurId(UUID.randomUUID());
		b.setMatricule("M001");
		b.setNom("Sarra Khemiri");
		return b;
	}

	@Test
	void compter_unePaireComplete_etatComplet_nomEtReferenceRenseignes() {
		PresenceSite site = site();
		PresenceHeuresSupDemande d = demande(StatutHeuresSup.VALIDEE);
		PresenceHeuresSupBeneficiaire b = beneficiaire();
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(d));
		when(beneficiaireRepository.findAllById(anyCollection())).thenReturn(List.of(b));
		when(siteRepository.findAllById(anyCollection())).thenReturn(List.of(site));
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());
		when(pointageRepository.findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(any(), any(), any(), any(), any()))
				.thenReturn(List.of(entree(3, 0), sortie(15, 0)));

		List<HeuresComptees> r = service(a(20, 0)).compter(List.of(ligne(site, d, b)));

		assertThat(r).singleElement().satisfies(h -> {
			assertThat(h.heuresComptees()).isEqualTo("02:00");
			assertThat(h.etat()).isEqualTo(EtatJourHeuresSup.COMPLET);
			assertThat(h.referenceDemande()).isEqualTo("HS-0000ABCD");
			assertThat(h.matricule()).isEqualTo("M001");
			assertThat(h.plageAvant()).isEqualTo("04:00-06:00");
			assertThat(h.horaireDebut()).isEqualTo("06:00");
		});
	}

	@Test
	void compter_orphelinPendantLaFenetre_enCours_puisIncompletUneFoisFermee() {
		PresenceSite site = site();
		PresenceHeuresSupDemande d = demande(StatutHeuresSup.VALIDEE);
		PresenceHeuresSupBeneficiaire b = beneficiaire();
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(d));
		when(beneficiaireRepository.findAllById(anyCollection())).thenReturn(List.of(b));
		when(siteRepository.findAllById(anyCollection())).thenReturn(List.of(site));
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());
		when(pointageRepository.findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(any(), any(), any(), any(), any()))
				.thenReturn(List.of(entree(4, 10)));
		PresenceHeuresSupJour l = ligne(site, d, b);

		assertThat(service(a(10, 0)).compter(List.of(l)).get(0).etat()).isEqualTo(EtatJourHeuresSup.EN_COURS);
		assertThat(service(a(17, 0)).compter(List.of(l)).get(0).etat()).isEqualTo(EtatJourHeuresSup.INCOMPLET);
		assertThat(service(a(17, 0)).compter(List.of(l)).get(0).minutesComptees()).isZero();
	}

	@Test
	void compter_ignoreLesDemandesNonValidees() {
		PresenceSite site = site();
		PresenceHeuresSupDemande d = demande(StatutHeuresSup.ANNULEE);
		PresenceHeuresSupBeneficiaire b = beneficiaire();
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(d));
		when(beneficiaireRepository.findAllById(anyCollection())).thenReturn(List.of(b));
		when(siteRepository.findAllById(anyCollection())).thenReturn(List.of(site));
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());
		when(pointageRepository.findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(any(), any(), any(), any(), any()))
				.thenReturn(List.of());

		assertThat(service(a(20, 0)).compter(List.of(ligne(site, d, b)))).isEmpty();
	}

	@Test
	void periode_parDefautTrenteEtUnJours_finAvantDebut400_auDela93Jours400() {
		CalculHeuresSupService s = service(a(12, 0));

		LocalDate[] defaut = s.periode(null, null);
		assertThat(defaut[1]).isEqualTo(JOUR);
		assertThat(defaut[0]).isEqualTo(JOUR.minusDays(30));
		assertThatThrownBy(() -> s.periode(JOUR, JOUR.minusDays(1)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("PERIODE_INVALIDE");
					assertThat(e.getHttpStatus()).isEqualTo(400);
				});
		assertThatThrownBy(() -> s.periode(JOUR.minusDays(93), JOUR))
				.isInstanceOfSatisfying(PresenceMetierException.class,
						e -> assertThat(e.getCode()).isEqualTo("PERIODE_TROP_LONGUE"));
		assertThat(s.periode(JOUR.minusDays(92), JOUR)[0]).isEqualTo(JOUR.minusDays(92));
	}

	@Test
	void compter_chargeSeulementLesPointagesDesCollaborateursDemandes_ficheEtCompte_etFusionneLesDeux() {
		PresenceSite site = site();
		PresenceHeuresSupDemande d = demande(StatutHeuresSup.VALIDEE);
		PresenceHeuresSupBeneficiaire b = beneficiaire();
		PresenceHeuresSupJour l = ligne(site, d, b);
		l.setCompteId("compte-1");
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(d));
		when(beneficiaireRepository.findAllById(anyCollection())).thenReturn(List.of(b));
		when(siteRepository.findAllById(anyCollection())).thenReturn(List.of(site));
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());
		// l'entrée a été pointée avec la fiche (en-tête), la sortie avec le compte (JWT) : la paire se forme quand même
		PresencePointage e = entree(3, 0);
		PresencePointage sortie = sortie(15, 0);
		sortie.setCollaborateurId("compte-1");
		PresencePointage autre = entree(4, 0);
		autre.setCollaborateurId("inconnu");
		when(pointageRepository.findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(any(), any(), any(), any(), any()))
				.thenReturn(List.of(e, sortie, autre));

		List<HeuresComptees> r = service(a(20, 0)).compter(List.of(l));

		org.mockito.ArgumentCaptor<java.util.Collection<String>> ids = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
		org.mockito.Mockito.verify(pointageRepository).findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(
				any(), any(), any(), any(), ids.capture());
		assertThat(ids.getValue()).containsExactlyInAnyOrder("fiche-1", "compte-1");
		assertThat(r.get(0).minutesComptees()).isEqualTo(120);
		assertThat(r.get(0).etat()).isEqualTo(EtatJourHeuresSup.COMPLET);
	}
}
