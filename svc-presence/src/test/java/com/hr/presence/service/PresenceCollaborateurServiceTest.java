package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.dto.PresenceCollaborateurDtos.JourneePresence;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceEquipe;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresence;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-N01.b, D-N02, D-P19.g : présence d'un collaborateur (journées, retards, motif en clair, fiche d'équipe).
 * « Aujourd'hui » = jeudi 5 novembre 2026, 10:00 (Africa/Tunis), horloge injectée.
 */
class PresenceCollaborateurServiceTest {

	private final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceHeuresSupJourRepository heuresSupJourRepository = mock(PresenceHeuresSupJourRepository.class);
	private final PresenceHeuresSupDemandeRepository heuresSupDemandeRepository = mock(PresenceHeuresSupDemandeRepository.class);
	private final PresenceDemandeManuelleRepository demandeRepository = mock(PresenceDemandeManuelleRepository.class);
	private final ReferentielClient referentiel = mock(ReferentielClient.class);
	private final CalculHeuresSupService calculHeuresSup = mock(CalculHeuresSupService.class);
	private final PresenceDemandeManuelleService demandeManuelleService = mock(PresenceDemandeManuelleService.class);
	private final FacialIdentiteService identiteService = mock(FacialIdentiteService.class);
	private final PresenceProperties properties = new PresenceProperties();
	private final Clock clock = Clock.fixed(
			LocalDateTime.of(2026, 11, 5, 10, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant(), ClockConfig.ZONE_TUNIS);
	private PresenceCollaborateurService service;

	private final UUID siteId = UUID.randomUUID();
	private final UUID fiche = UUID.randomUUID();
	private final UUID compte = UUID.randomUUID();
	private PresenceSite site;

	private static Instant t(int jour, int h, int min, int s) {
		return LocalDateTime.of(2026, 11, jour, h, min, s).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private PresencePointage p(PointageType type, PointageStatut statut, Instant ts, Instant debut) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId(fiche.toString());
		p.setSiteId(siteId);
		p.setType(type);
		p.setStatut(statut);
		p.setServerTs(ts);
		p.setHoraireDebutTs(debut);
		return p;
	}

	@SuppressWarnings("unchecked")
	private static Specification<PresencePointage> anySpec() {
		return any(Specification.class);
	}

	@SuppressWarnings("unchecked")
	private static Specification<PresenceHeuresSupJour> anyJourSpec() {
		return any(Specification.class);
	}

	@BeforeEach
	void setUp() {
		TempsTravailService tempsTravail = new TempsTravailService(pointageRepository, siteRepository,
				heuresSupJourRepository, heuresSupDemandeRepository, referentiel, properties,
				ParametresTestSupport.depuis(properties), clock);
		service = new PresenceCollaborateurService(tempsTravail, pointageRepository, siteRepository, demandeRepository,
				heuresSupJourRepository, calculHeuresSup, demandeManuelleService, identiteService, referentiel,
				properties, ParametresTestSupport.depuis(properties), clock);
		site = new PresenceSite();
		site.setId(siteId);
		site.setCode("SIEGE");
		site.setLibelle("Siège");
		site.setRayonMetres(50);
		when(siteRepository.findAllById(any())).thenReturn(List.of(site));
		when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
		when(heuresSupJourRepository.findAll(anyJourSpec())).thenReturn(List.of());
		when(calculHeuresSup.compter(any())).thenReturn(List.of());
		when(demandeRepository.findByPointageRejeteIdIn(any())).thenReturn(List.of());
		when(demandeManuelleService.pourIdentites(any(), any(), any())).thenReturn(List.of());
		when(referentiel.affectations(any())).thenReturn(List.of(
				new Affectation(fiche, compte, "COL-WEB", "Sarra Khemiri", "Direction Technique", "Logiciel", "Web")));
		ValideurPointage moi = new ValideurPointage(fiche, compte, "Sarra Khemiri", null, null, null);
		when(identiteService.resoudre()).thenReturn(new Identite(compte.toString(), moi));
	}

	/** 1er appel : pointages retenus (vue Temps de travail) ; 2e appel : tous statuts (journées). */
	private void pointages(List<PresencePointage> retenus, List<PresencePointage> tous) {
		when(pointageRepository.findAll(anySpec(), any(Sort.class))).thenReturn(retenus, tous);
	}

	// ---- Retards et journées -----------------------------------------------------------------------------------

	@Test
	void mesPointages_retardDe12Minutes_motifHorsZoneEnClair_etRefusEligible() {
		PresencePointage refus = p(PointageType.ENTREE, PointageStatut.REJETE_HORS_ZONE, t(5, 7, 50, 0), null);
		refus.setDistanceMetres(179.8);
		PresencePointage entree = p(PointageType.ENTREE, PointageStatut.VALIDE, t(5, 8, 12, 40), t(5, 8, 0, 0));
		PresencePointage sortie = p(PointageType.SORTIE, PointageStatut.VALIDE, t(5, 9, 12, 40), t(5, 8, 0, 0));
		pointages(List.of(entree, sortie), List.of(refus, entree, sortie));

		List<JourneePresence> journees = service.mesJournees(null, null);

		assertThat(journees).hasSize(1);
		JourneePresence j = journees.get(0);
		assertThat(j.journee()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(j.siteLibelle()).isEqualTo("Siège");
		assertThat(j.retardMinutes()).isEqualTo(12);
		assertThat(j.tempsMinutes()).isEqualTo(60);
		assertThat(j.pointages()).hasSize(3);
		assertThat(j.pointages().get(0).motifRejet()).isEqualTo("Hors zone de pointage (179,8 m pour une zone de 50 m)");
		assertThat(j.pointages().get(0).statutLibelle()).isEqualTo("Hors zone de pointage");
		assertThat(j.pointages().get(0).eligiblePointageManuel()).isTrue();
		assertThat(j.pointages().get(1).motifRejet()).isNull();
		assertThat(j.pointages().get(1).eligiblePointageManuel()).isFalse();
	}

	@Test
	void refusHorsHoraire_nonEligibleAuPointageManuel() {
		PresencePointage refus = p(PointageType.ENTREE, PointageStatut.REJETE_HORS_HORAIRE, t(5, 6, 0, 0), null);
		pointages(List.of(), List.of(refus));

		JourneePresence j = service.mesJournees(null, null).get(0);

		assertThat(j.etat()).isNull();
		assertThat(j.retardMinutes()).isNull();
		assertThat(j.pointages().get(0).eligiblePointageManuel()).isFalse();
	}

	@Test
	void refusDejaDemande_ouTropAncien_nonEligible() {
		PresencePointage deja = p(PointageType.ENTREE, PointageStatut.REJETE_PRECISION_GPS, t(5, 7, 0, 0), null);
		PresencePointage ancien = p(PointageType.ENTREE, PointageStatut.REJETE_PRECISION_GPS, t(2, 7, 0, 0), null);
		pointages(List.of(), List.of(ancien, deja));
		PresenceDemandeManuelle demande = new PresenceDemandeManuelle();
		demande.setPointageRejeteId(deja.getId());
		when(demandeRepository.findByPointageRejeteIdIn(any())).thenReturn(List.of(demande));

		// "ancien" date de 3 jours : on resserre la limite d'ancienneté à 2 jours pour le rendre trop vieux
		properties.getManuel().setAncienneteMaxJours(2);
		List<JourneePresence> journees = service.mesJournees(LocalDate.of(2026, 10, 25), null);

		assertThat(journees).flatExtracting(JourneePresence::pointages)
				.hasSize(2)
				.allSatisfy(pt -> assertThat(pt.eligiblePointageManuel()).isFalse());
	}

	@Test
	void collaborateurSansPointage_listeVide_etSyntheseAZeroRetards() {
		pointages(List.of(), List.of());

		assertThat(service.mesJournees(null, null)).isEmpty();
		pointages(List.of(), List.of());
		SynthesePresence s = service.maSynthese(null, null);
		assertThat(s.retardsNombre()).isZero();
		assertThat(s.retardsCumulMinutes()).isZero();
		assertThat(s.journeesPointees()).isZero();
		assertThat(s.du()).isEqualTo(LocalDate.of(2026, 10, 6));
		assertThat(s.au()).isEqualTo(LocalDate.of(2026, 11, 5));
	}

	@Test
	void synthese_compteRetardsEtCumul() {
		PresencePointage e1 = p(PointageType.ENTREE, PointageStatut.VALIDE, t(3, 8, 12, 0), t(3, 8, 0, 0));
		PresencePointage s1 = p(PointageType.SORTIE, PointageStatut.VALIDE, t(3, 17, 0, 0), t(3, 8, 0, 0));
		PresencePointage e2 = p(PointageType.ENTREE, PointageStatut.VALIDE, t(4, 8, 0, 0), t(4, 8, 0, 0));
		PresencePointage s2 = p(PointageType.SORTIE, PointageStatut.VALIDE, t(4, 17, 0, 0), t(4, 8, 0, 0));
		PresencePointage e3 = p(PointageType.ENTREE, PointageStatut.VALIDE, t(5, 8, 29, 0), t(5, 8, 0, 0));
		pointages(List.of(e1, s1, e2, s2, e3), List.of(e1, s1, e2, s2, e3));

		SynthesePresence s = service.syntheseAdmin(fiche, null, null);

		assertThat(s.collaborateurNom()).isEqualTo("Sarra Khemiri");
		assertThat(s.collaborateurMatricule()).isEqualTo("COL-WEB");
		assertThat(s.journeesPointees()).isEqualTo(3);
		assertThat(s.retardsNombre()).isEqualTo(2);
		assertThat(s.retardsCumulMinutes()).isEqualTo(41);
		assertThat(s.tempsTotalMinutes()).isEqualTo(528 + 540);
	}

	@Test
	void periodeDe32Jours_400PeriodeTropLongue_avantToutAppelDIdentite() {
		assertThatThrownBy(() -> service.mesJournees(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("PERIODE_TROP_LONGUE");
				});
		verifyNoInteractions(identiteService);
	}

	@Test
	void periodeInversee_400PeriodeInvalide() {
		assertThatThrownBy(() -> service.syntheseAdmin(fiche, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 2)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("PERIODE_INVALIDE"));
	}

	@Test
	void syntheseDUnCollaborateurInconnu_404() {
		when(referentiel.affectations(any())).thenReturn(List.of());

		assertThatThrownBy(() -> service.syntheseAdmin(UUID.randomUUID(), null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void syntheseDUnSite_comptePersonnesEtRetards() {
		PresencePointage e1 = p(PointageType.ENTREE, PointageStatut.VALIDE, t(3, 8, 12, 0), t(3, 8, 0, 0));
		PresencePointage s1 = p(PointageType.SORTIE, PointageStatut.VALIDE, t(3, 17, 0, 0), t(3, 8, 0, 0));
		pointages(List.of(e1, s1), List.of());

		var s = service.syntheseSite(siteId, null, null);

		assertThat(s.siteLibelle()).isEqualTo("Siège");
		assertThat(s.collaborateurs()).isEqualTo(1);
		assertThat(s.retardsNombre()).isEqualTo(1);
		assertThat(s.retardsCumulMinutes()).isEqualTo(12);
	}

	@Test
	void syntheseDUnSiteInconnu_404() {
		when(siteRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.syntheseSite(UUID.randomUUID(), null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	// ---- Fiche d'équipe (D-N02) ----------------------------------------------------------------------------------

	@Test
	void equipe_valideurDuReferentiel_200_avecSyntheseEtNom() {
		UUID membre = UUID.randomUUID();
		when(referentiel.estValideurDe(membre, compte.toString())).thenReturn(true);
		when(referentiel.affectations(any())).thenReturn(List.of(
				new Affectation(membre, UUID.randomUUID(), "COL-WEB2", "Ahmed Nasri", null, null, "Web")));
		pointages(List.of(), List.of());

		PresenceEquipe e = service.equipe(membre, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5));

		assertThat(e.collaborateur().nomComplet()).isEqualTo("Ahmed Nasri");
		assertThat(e.synthese().retardsNombre()).isZero();
		assertThat(e.journees()).isEmpty();
	}

	@Test
	void equipe_pasLeValideur_403HorsPerimetre_etAucuneDonneeLue() {
		UUID membre = UUID.randomUUID();
		when(referentiel.estValideurDe(membre, compte.toString())).thenReturn(false);

		assertThatThrownBy(() -> service.equipe(membre, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(403);
					assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE");
				});
		verify(pointageRepository, never()).findAll(anySpec(), any(Sort.class));
	}

	@Test
	void equipe_referentielCoupe_503_aucuneDonnee() {
		UUID membre = UUID.randomUUID();
		when(referentiel.estValideurDe(any(), anyString())).thenThrow(
				PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "indisponible"));

		assertThatThrownBy(() -> service.equipe(membre, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(503));
		verify(pointageRepository, never()).findAll(anySpec(), any(Sort.class));
	}

	@Test
	void equipe_periodeDe32Jours_400_avantLeControleDePerimetre() {
		assertThatThrownBy(() -> service.equipe(UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("PERIODE_TROP_LONGUE"));
		verify(referentiel, never()).estValideurDe(any(), anyString());
	}

	@Test
	void identiteIncoherente_403_propagee() {
		when(identiteService.resoudre()).thenThrow(new PresenceMetierException("IDENTITE_INCOHERENTE", "x", 403));

		assertThatThrownBy(() -> service.mesJournees(null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE"));
	}

	private com.hr.presence.dto.DemandeManuelleResponse demande(com.hr.presence.domain.StatutDemandeManuelle statut, String collab) {
		return new com.hr.presence.dto.DemandeManuelleResponse(UUID.randomUUID(), "PM-202611-0001", statut, PointageType.ENTREE,
				t(4, 8, 0, 0), "GPS", collab, "Sarra Khemiri", "COL-WEB", siteId, null, null, null, false, null, null, null,
				null, null, null, null);
	}

	@Test
	void syntheseMobileEgaleLaSyntheseAdmin_demandesRattacheesAuCompteEtALaFiche() {
		PresencePointage e1 = p(PointageType.ENTREE, PointageStatut.VALIDE, t(3, 8, 12, 0), t(3, 8, 0, 0));
		PresencePointage s1 = p(PointageType.SORTIE, PointageStatut.VALIDE, t(3, 17, 0, 0), t(3, 8, 0, 0));
		// la fiche porte 3 demandes validées, le compte 2 validées et 1 en attente : 5 et 1 au total, sous les deux identités
		when(demandeManuelleService.pourIdentites(any(), any(), any())).thenAnswer(inv -> {
			java.util.Set<String> ids = inv.getArgument(0);
			List<com.hr.presence.dto.DemandeManuelleResponse> out = new java.util.ArrayList<>();
			if (ids.contains(fiche.toString())) {
				for (int k = 0; k < 3; k++) {
					out.add(demande(com.hr.presence.domain.StatutDemandeManuelle.VALIDEE, fiche.toString()));
				}
			}
			if (ids.contains(compte.toString())) {
				for (int k = 0; k < 2; k++) {
					out.add(demande(com.hr.presence.domain.StatutDemandeManuelle.VALIDEE, compte.toString()));
				}
				out.add(demande(com.hr.presence.domain.StatutDemandeManuelle.EN_ATTENTE, compte.toString()));
			}
			return out;
		});
		pointages(List.of(e1, s1), List.of(e1, s1));
		SynthesePresence admin = service.syntheseAdmin(fiche, null, null);
		pointages(List.of(e1, s1), List.of(e1, s1));
		SynthesePresence mobile = service.maSynthese(null, null);

		assertThat(admin.pointagesManuelsValides()).isEqualTo(5);
		assertThat(admin.pointagesManuelsEnAttente()).isEqualTo(1);
		assertThat(mobile).isEqualTo(admin);
		assertThat(mobile.collaborateurMatricule()).isEqualTo("COL-WEB");
	}

	@Test
	void syntheseMobile_referentielCoupePourLeMatricule_resteServieSansMatricule() {
		pointages(List.of(), List.of());
		when(referentiel.affectations(any())).thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "x"));

		SynthesePresence s = service.maSynthese(null, null);

		assertThat(s.collaborateurNom()).isEqualTo("Sarra Khemiri");
		assertThat(s.collaborateurMatricule()).isNull();
	}
}
