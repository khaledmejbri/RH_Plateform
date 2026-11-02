package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.CreationHeuresSupRequest;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-P13 (SCRUM-50) : création d'une demande d'heures sup. Site 06:00-15:00 du lundi au vendredi, tolérance 60 / 60 ;
 * « maintenant » = lundi 2 novembre 2026, 09:00 (Africa/Tunis).
 */
@ExtendWith(MockitoExtension.class)
class HeuresSupCreationServiceTest {

	private static final Instant MAINTENANT = LocalDateTime.of(2026, 11, 2, 9, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	private static final LocalDate DEMAIN = LocalDate.of(2026, 11, 3);
	private static final LocalDate SAMEDI = LocalDate.of(2026, 11, 7);
	private static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 8);

	@Mock
	private PresenceHeuresSupDemandeRepository demandeRepository;
	@Mock
	private PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	@Mock
	private PresenceHeuresSupJourRepository jourRepository;
	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private FenetreHoraireService fenetreService;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceAccessService accessService;
	@Mock
	private ActeurResolver acteurResolver;
	@Mock
	private HeuresSupNotifier notifier;
	@Mock
	private HeuresSupReponseMapper mapper;

	private HeuresSupCreationService service;
	private PresenceSite site;
	private final UUID ficheRo = UUID.randomUUID();
	private final UUID compteRo = UUID.randomUUID();
	private final UUID ficheA = UUID.randomUUID();
	private final UUID ficheB = UUID.randomUUID();
	private Map<DayOfWeek, Horaire> horaires;

	@BeforeEach
	void setUp() {
		service = new HeuresSupCreationService(demandeRepository, beneficiaireRepository, jourRepository, siteRepository,
				fenetreService, referentielClient, accessService, acteurResolver, notifier, mapper,
				Clock.fixed(MAINTENANT, ClockConfig.ZONE_TUNIS));
		site = new PresenceSite();
		site.setId(UUID.randomUUID());
		site.setLibelle("Siège");
		horaires = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			horaires.put(jour, new Horaire(LocalTime.of(6, 0), LocalTime.of(15, 0)));
		}
		lenient().when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		lenient().when(fenetreService.horaires(site.getId())).thenReturn(horaires);
		lenient().when(accessService.currentCompteId()).thenReturn(compteRo.toString());
		lenient().when(referentielClient.monValideurPointage())
				.thenReturn(new ValideurPointage(ficheRo, compteRo, "Omar Belhadj", UUID.randomUUID(), UUID.randomUUID(), "Chef"));
		lenient().when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(
				List.of(resolu(ficheA, true, ficheRo), resolu(ficheB, true, ficheRo)));
		lenient().when(jourRepository.findBySiteIdAndJourneeBetweenAndCollaborateurIdIn(any(), any(), any(), anyCollection()))
				.thenReturn(List.of());
		lenient().when(demandeRepository.saveAndFlush(any(PresenceHeuresSupDemande.class))).thenAnswer(inv -> {
			PresenceHeuresSupDemande d = inv.getArgument(0);
			d.setId(UUID.randomUUID());
			d.setReference("HS-TEST0001");
			return d;
		});
		lenient().when(beneficiaireRepository.save(any(PresenceHeuresSupBeneficiaire.class))).thenAnswer(inv -> {
			PresenceHeuresSupBeneficiaire b = inv.getArgument(0);
			b.setId(UUID.randomUUID());
			return b;
		});
	}

	private static CollaborateurResolu resolu(UUID fiche, boolean actif, UUID valideur) {
		return new CollaborateurResolu(fiche, UUID.randomUUID(), "M-" + fiche.toString().substring(0, 4),
				"Nom " + fiche.toString().substring(0, 4), actif, UUID.randomUUID(), "Direction Technique", valideur,
				UUID.randomUUID());
	}

	private CreationHeuresSupRequest requete(List<UUID> ids, LocalDate debut, LocalDate fin, Integer avant, Integer apres,
											 String motif) {
		return new CreationHeuresSupRequest(ids, site.getId(), debut, fin, avant, apres, motif);
	}

	private CreationHeuresSupRequest requeteOk() {
		return requete(List.of(ficheA, ficheB), DEMAIN, null, 120, null, "Clôture mensuelle");
	}

	private static void codeErreur(Runnable action, String code, int statut) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getHttpStatus()).isEqualTo(statut);
		});
	}

	// --- Création par le RO ---

	@Test
	void roDemandePourSonEquipe_enAttente_ouvertureEtLignesParBeneficiaireEtJour() {
		service.creerParDemandeur(requeteOk());

		ArgumentCaptor<PresenceHeuresSupDemande> d = ArgumentCaptor.forClass(PresenceHeuresSupDemande.class);
		verify(demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
		assertThat(d.getValue().isCreeeValidee()).isFalse();
		assertThat(d.getValue().getDemandeurCompteId()).isEqualTo(compteRo.toString());
		assertThat(d.getValue().getDemandeurFicheId()).isEqualTo(ficheRo.toString());
		assertThat(d.getValue().getDateFin()).isEqualTo(DEMAIN);
		// 06:00 - 2 h d'ouverture - 60 min de tolérance = 03:00 (décision client n° 1)
		assertThat(d.getValue().getDebutPlageTs())
				.isEqualTo(LocalDateTime.of(DEMAIN, LocalTime.of(3, 0)).atZone(ClockConfig.ZONE_TUNIS).toInstant());
		ArgumentCaptor<PresenceHeuresSupJour> jours = ArgumentCaptor.forClass(PresenceHeuresSupJour.class);
		verify(jourRepository, times(2)).save(jours.capture());
		assertThat(jours.getAllValues()).extracting(PresenceHeuresSupJour::getJournee).containsOnly(DEMAIN);
		assertThat(jours.getAllValues()).extracting(PresenceHeuresSupJour::getCollaborateurId)
				.containsExactlyInAnyOrder(ficheA.toString(), ficheB.toString());
		verify(notifier).creeeEnAttente(any(), anyString());
		verify(notifier, never()).creeeValidee(any(), anyString(), any());
	}

	@Test
	void ouvertureApresSeulement_debutDePlage_dedLaToleranceAvantSeule() {
		service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, null, 60, "Inventaire"));

		ArgumentCaptor<PresenceHeuresSupDemande> d = ArgumentCaptor.forClass(PresenceHeuresSupDemande.class);
		verify(demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getDebutPlageTs())
				.isEqualTo(LocalDateTime.of(DEMAIN, LocalTime.of(5, 0)).atZone(ClockConfig.ZONE_TUNIS).toInstant());
	}

	@Test
	void motifVide_400() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 120, null, "  ")),
				"MOTIF_OBLIGATOIRE", 400);
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 120, null, null)),
				"MOTIF_OBLIGATOIRE", 400);
		verifyNoInteractions(referentielClient);
	}

	@Test
	void ouvertureDe20Minutes_4h30_ou1h15_400() {
		for (int minutes : new int[] {20, 270, 75, -30}) {
			codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, minutes, null, "m")),
					"DUREE_HEURES_SUP_INVALIDE", 400);
		}
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, null, 270, "m")),
				"DUREE_HEURES_SUP_INVALIDE", 400);
	}

	@Test
	void ouvertureMinimaleEtMaximale_acceptees() {
		service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 30, 240, "m"));

		verify(demandeRepository).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void aucunCoteRenseigne_400() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, null, null, "m")),
				"OUVERTURE_ABSENTE", 400);
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 0, 0, "m")),
				"OUVERTURE_ABSENTE", 400);
	}

	@Test
	void plageDe32Jours_400_31Jours_ok() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, DEMAIN.plusDays(31), 120, null, "m")),
				"PLAGE_TROP_LONGUE", 400);

		service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, DEMAIN.plusDays(30), 120, null, "m"));
		verify(demandeRepository).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void finAvantDebut_400() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, DEMAIN.minusDays(1), 120, null, "m")),
				"PERIODE_INVALIDE", 400);
	}

	@Test
	void siteInconnu_404_siteSansHoraire_422() {
		when(siteRepository.findById(site.getId())).thenReturn(Optional.empty());
		codeErreur(() -> service.creerParDemandeur(requeteOk()), "SITE_INTROUVABLE", 404);

		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		when(fenetreService.horaires(site.getId())).thenReturn(new EnumMap<>(DayOfWeek.class));
		codeErreur(() -> service.creerParDemandeur(requeteOk()), "SITE_SANS_HORAIRE", 422);
	}

	@Test
	void plageLimiteeAuSamediEtDimanche_aucunJourOuvert_422() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), SAMEDI, DIMANCHE, 120, null, "m")),
				"AUCUN_JOUR_OUVERT", 422);
	}

	@Test
	void joursFermesDeLaPlageIgnores_seulsLesJoursOuvertsSontCrees() {
		LocalDate vendredi = LocalDate.of(2026, 11, 6);

		service.creerParDemandeur(requete(List.of(ficheA), vendredi, LocalDate.of(2026, 11, 9), 120, null, "m"));

		ArgumentCaptor<PresenceHeuresSupJour> jours = ArgumentCaptor.forClass(PresenceHeuresSupJour.class);
		verify(jourRepository, times(2)).save(jours.capture());
		assertThat(jours.getAllValues()).extracting(PresenceHeuresSupJour::getJournee)
				.containsExactlyInAnyOrder(vendredi, LocalDate.of(2026, 11, 9));
	}

	@Test
	void plageDejaCommencee_fenetreElargieDejaOuverte_422() {
		// aujourd'hui lundi, il est 09:00 : la fenêtre élargie (03:00) est ouverte
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), LocalDate.of(2026, 11, 2), null, 120, null, "m")),
				"PLAGE_DEJA_COMMENCEE", 422);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void ouvertureAvantFermeeMaisFenetreDeBaseOuverte_aujourdhui_toujoursRefusee() {
		// même une demande « après » seulement : la fenêtre de base (05:00) est ouverte à 09:00
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), LocalDate.of(2026, 11, 2), null, null, 60, "m")),
				"PLAGE_DEJA_COMMENCEE", 422);
	}

	// --- Périmètre D-H03 ---

	@Test
	void beneficiaireHorsPerimetre_403_rienNEstCree() {
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(
				List.of(resolu(ficheA, true, ficheRo), resolu(ficheB, true, UUID.randomUUID())));

		codeErreur(() -> service.creerParDemandeur(requeteOk()), "HORS_PERIMETRE", 403);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
		verify(beneficiaireRepository, never()).save(any(PresenceHeuresSupBeneficiaire.class));
		verify(jourRepository, never()).save(any(PresenceHeuresSupJour.class));
	}

	@Test
	void leResponsableDuDemandeurEstHorsPerimetre() {
		// CS-WEB inclut CD-SW, son propre responsable : le valideur de CD-SW n'est pas CS-WEB
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(List.of(resolu(ficheA, true, UUID.randomUUID())));

		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 120, null, "m")),
				"HORS_PERIMETRE", 403);
	}

	@Test
	void leDemandeurNePeutPasSInclure_403() {
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(List.of(resolu(ficheRo, true, ficheRo)));

		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheRo), DEMAIN, null, 120, null, "m")),
				"HORS_PERIMETRE", 403);
	}

	@Test
	void beneficiaireInactifOuIntrouvable_422() {
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(List.of(resolu(ficheA, true, ficheRo), resolu(ficheB, false, ficheRo)));
		codeErreur(() -> service.creerParDemandeur(requeteOk()), "COLLABORATEUR_INVALIDE", 422);

		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(resolu(ficheA, true, ficheRo)));
		codeErreur(() -> service.creerParDemandeur(requeteOk()), "COLLABORATEUR_INVALIDE", 422);
	}

	@Test
	void listeDedoublonnee_unSeulBeneficiaireResolu() {
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(resolu(ficheA, true, ficheRo)));

		service.creerParDemandeur(requete(List.of(ficheA, ficheA, ficheA), DEMAIN, null, 120, null, "m"));

		ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
		verify(referentielClient).resoudreCollaborateurs(ids.capture());
		assertThat(ids.getValue()).containsExactly(ficheA);
		verify(beneficiaireRepository, times(1)).save(any(PresenceHeuresSupBeneficiaire.class));
	}

	@Test
	void referentielIndisponible_503_aucuneCreationEnModeDegrade() {
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenThrow(
				PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "indisponible"));

		codeErreur(() -> service.creerParDemandeur(requeteOk()), "REFERENTIEL_INDISPONIBLE", 503);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void identiteDuDemandeur_lueAuJwt_pasDansLeCorps() {
		service.creerParDemandeur(requeteOk());

		verify(referentielClient).monValideurPointage();
		verify(accessService, never()).collaborateurHeaderOuNull();
	}

	// --- Chevauchements ---

	@Test
	void deuxiemeDemandePourLeMemeJour_409() {
		PresenceHeuresSupJour existante = new PresenceHeuresSupJour();
		existante.setDemandeId(UUID.randomUUID());
		existante.setCollaborateurId(ficheA.toString());
		existante.setSiteId(site.getId());
		existante.setJournee(DEMAIN);
		when(jourRepository.findBySiteIdAndJourneeBetweenAndCollaborateurIdIn(any(), any(), any(), anyCollection()))
				.thenReturn(List.of(existante));
		PresenceHeuresSupDemande conflit = new PresenceHeuresSupDemande();
		conflit.setId(existante.getDemandeId());
		conflit.setReference("HS-CONFLIT1");
		conflit.setOuvertureAvantMinutes(30);
		when(demandeRepository.findAllById(anyCollection())).thenReturn(List.of(conflit));

		assertThatThrownBy(() -> service.creerParDemandeur(requeteOk()))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("CHEVAUCHEMENT_HEURES_SUP");
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getMessage()).contains("HS-CONFLIT1");
				});
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void deuxDemandesSimultanees_laContrainteUniqueTranche_409() {
		when(jourRepository.save(any(PresenceHeuresSupJour.class))).thenReturn(null);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_presence_hs_jour_collab_site_journee"))
				.when(jourRepository).flush();

		codeErreur(() -> service.creerParDemandeur(requeteOk()), "CHEVAUCHEMENT_HEURES_SUP", 409);
	}

	@Test
	void fenetresElargiesDeDeuxJoursConsecutifsQuiSeChevauchent_422() {
		site.setToleranceAvantMinutes(90);
		site.setToleranceApresMinutes(90);
		horaires.put(DayOfWeek.MONDAY, new Horaire(LocalTime.of(6, 0), LocalTime.of(20, 0)));
		horaires.put(DayOfWeek.TUESDAY, new Horaire(LocalTime.of(6, 0), LocalTime.of(15, 0)));
		// lundi 9 et mardi 10 : fin lundi 20:00 + 90 + 240 = 01:30 ; début mardi 06:00 - 90 - 240 = 00:30
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10),
				240, 240, "m")), "HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN", 422);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void fenetresQuiSeTouchentExactement_acceptees() {
		site.setToleranceAvantMinutes(60);
		site.setToleranceApresMinutes(60);
		horaires.put(DayOfWeek.MONDAY, new Horaire(LocalTime.of(6, 0), LocalTime.of(20, 0)));
		horaires.put(DayOfWeek.TUESDAY, new Horaire(LocalTime.of(6, 0), LocalTime.of(15, 0)));
		// fin lundi 20:00 + 60 + 240 = 01:00 ; début mardi 06:00 - 60 - 240 = 01:00 : égalité tolérée
		service.creerParDemandeur(requete(List.of(ficheA), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10),
				240, 240, "m"));

		verify(demandeRepository).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	// --- Création directe RH / ADMIN (D-P13.c) ---

	private void rhConnecte(UUID fiche, UUID compte) {
		when(acteurResolver.avecFiche()).thenReturn(new Acteur(compte.toString(), fiche != null ? fiche.toString() : null,
				"DRH01", "Amel Trabelsi"));
	}

	@Test
	void creationDirecte_valideeAvecTrace_beneficiairesNotifiesSeulement_aucunControleDePerimetre() {
		rhConnecte(UUID.randomUUID(), UUID.randomUUID());
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(
				List.of(resolu(ficheA, true, UUID.randomUUID())));

		service.creerDirecte(requete(List.of(ficheA), DEMAIN, null, 120, null, "Audit"));

		ArgumentCaptor<PresenceHeuresSupDemande> d = ArgumentCaptor.forClass(PresenceHeuresSupDemande.class);
		verify(demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		assertThat(d.getValue().isCreeeValidee()).isTrue();
		assertThat(d.getValue().getDecideParNom()).isEqualTo("Amel Trabelsi");
		assertThat(d.getValue().getDecideLe()).isEqualTo(MAINTENANT);
		verify(notifier).creeeValidee(any(), anyString(), any());
		verify(notifier, never()).creeeEnAttente(any(), anyString());
		verify(referentielClient, never()).monValideurPointage();
	}

	@Test
	void creationDirecte_beneficiaireEstLAuteur_parFiche_403() {
		rhConnecte(ficheA, UUID.randomUUID());
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(resolu(ficheA, true, ficheRo)));

		codeErreur(() -> service.creerDirecte(requete(List.of(ficheA), DEMAIN, null, 120, null, "m")),
				"AUTO_VALIDATION_INTERDITE", 403);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void creationDirecte_beneficiaireEstLAuteur_parCompte_403_ceQuiCouvreUnRhSansFiche() {
		UUID compteDuRh = UUID.randomUUID();
		rhConnecte(null, compteDuRh);
		CollaborateurResolu moi = new CollaborateurResolu(ficheA, compteDuRh, "M1", "Nizar", true, null, null, ficheRo, null);
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(moi));

		codeErreur(() -> service.creerDirecte(requete(List.of(ficheA), DEMAIN, null, 120, null, "m")),
				"AUTO_VALIDATION_INTERDITE", 403);
	}

	// --- Recette A6 ---

	@Test
	void notificationDeCreationDirecteValidee_fenetreEtHeuresSup() {
		rhConnecte(UUID.randomUUID(), UUID.randomUUID());
		when(referentielClient.resoudreCollaborateurs(org.mockito.ArgumentMatchers.anyList())).thenReturn(
				List.of(resolu(ficheA, true, UUID.randomUUID())));

		service.creerDirecte(requete(List.of(ficheA), DEMAIN, null, 120, 60, "Audit"));

		ArgumentCaptor<String> resume = ArgumentCaptor.forClass(String.class);
		verify(notifier).creeeValidee(any(), resume.capture(), any());
		assertThat(resume.getValue()).contains("pointage ouvert 03:00–17:00, dont heures sup 04:00–06:00 et 15:00–16:00");
	}

	@Test
	void notificationAuxRh_deLaCreationEnAttente_memeFormat() {
		service.creerParDemandeur(requeteOk());

		ArgumentCaptor<String> resume = ArgumentCaptor.forClass(String.class);
		verify(notifier).creeeEnAttente(any(), resume.capture());
		assertThat(resume.getValue()).contains("pointage ouvert 03:00–16:00, dont heures sup 04:00–06:00");
	}

	// --- Revue ---

	@Test
	void motifDe501Caracteres_400_500Caracteres_acceptes() {
		codeErreur(() -> service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 120, null, "x".repeat(501))),
				"MOTIF_TROP_LONG", 400);
		verify(demandeRepository, never()).saveAndFlush(any(PresenceHeuresSupDemande.class));

		service.creerParDemandeur(requete(List.of(ficheA), DEMAIN, null, 120, null, "x".repeat(500)));
		verify(demandeRepository).saveAndFlush(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void uneAutreViolationDIntegrite_n_estPasTraduiteEn409() {
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("null value in column \"motif\" violates not-null"))
				.when(jourRepository).flush();

		assertThatThrownBy(() -> service.creerParDemandeur(requeteOk())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laContrainteUniqueDesJours_estReconnueDansLaChaineDesCauses() {
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("erreur",
				new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_presence_hs_jour_collab_site_journee\"")))
				.when(jourRepository).flush();

		codeErreur(() -> service.creerParDemandeur(requeteOk()), "CHEVAUCHEMENT_HEURES_SUP", 409);
	}
}
