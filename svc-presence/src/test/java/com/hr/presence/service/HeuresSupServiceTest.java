package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.BeneficiairePossibleResponse;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-P13 (SCRUM-50) : décisions, annulations, expiration et anti-auto-validation des heures supplémentaires.
 * « Maintenant » = lundi 2 novembre 2026, 09:00 (Africa/Tunis).
 */
@ExtendWith(MockitoExtension.class)
class HeuresSupServiceTest {

	private static final Instant MAINTENANT = LocalDateTime.of(2026, 11, 2, 9, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	private static final Instant DEMAIN_03H = LocalDateTime.of(2026, 11, 3, 3, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant();

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
	private CalculHeuresSupService calculService;
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

	private HeuresSupService service;
	private PresenceHeuresSupDemande demande;
	private PresenceHeuresSupBeneficiaire beneficiaire;
	private final UUID compteRo = UUID.randomUUID();
	private final UUID ficheRo = UUID.randomUUID();
	private final UUID compteRh = UUID.randomUUID();
	private final UUID ficheRh = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new HeuresSupService(demandeRepository, beneficiaireRepository, jourRepository, siteRepository,
				fenetreService, calculService, referentielClient, accessService, acteurResolver, notifier, mapper,
				Clock.fixed(MAINTENANT, ClockConfig.ZONE_TUNIS));
		PresenceSite site = new PresenceSite();
		site.setId(UUID.randomUUID());
		site.setLibelle("Siège");
		demande = new PresenceHeuresSupDemande();
		demande.setId(UUID.randomUUID());
		demande.setReference("HS-0000ABCD");
		demande.setSiteId(site.getId());
		demande.setDateDebut(LocalDate.of(2026, 11, 3));
		demande.setDateFin(LocalDate.of(2026, 11, 3));
		demande.setOuvertureAvantMinutes(120);
		demande.setStatut(StatutHeuresSup.EN_ATTENTE);
		demande.setDebutPlageTs(DEMAIN_03H);
		demande.setDemandeurCompteId(compteRo.toString());
		demande.setDemandeurFicheId(ficheRo.toString());
		demande.setDemandeurNom("Omar Belhadj");
		beneficiaire = new PresenceHeuresSupBeneficiaire();
		beneficiaire.setCollaborateurId(UUID.randomUUID());
		beneficiaire.setCompteId(UUID.randomUUID());
		lenient().when(demandeRepository.findById(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(demandeRepository.save(any(PresenceHeuresSupDemande.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(beneficiaireRepository.findByDemandeId(demande.getId())).thenReturn(List.of(beneficiaire));
		lenient().when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		lenient().when(acteurResolver.avecFiche())
				.thenReturn(new Acteur(compteRh.toString(), ficheRh.toString(), "DRH01", "Amel Trabelsi"));
		lenient().when(acteurResolver.depuisJwt()).thenReturn(new Acteur(compteRo.toString(), null, "CS-WEB", null));
		lenient().when(accessService.currentCompteId()).thenReturn(compteRo.toString());
	}

	private static void codeErreur(Runnable action, String code, int statut) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getHttpStatus()).isEqualTo(statut);
		});
	}

	// --- Validation ---

	@Test
	void rhValide_demandeValidee_demandeurEtBeneficiairesNotifies() {
		service.valider(demande.getId());

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		assertThat(demande.getDecideParNom()).isEqualTo("Amel Trabelsi");
		assertThat(demande.getDecideParCompteId()).isEqualTo(compteRh.toString());
		assertThat(demande.getDecideLe()).isEqualTo(MAINTENANT);
		verify(notifier).validee(any(), anyString(), any());
		verify(jourRepository, never()).deleteByDemandeId(any());
	}

	@Test
	void validerUneDemandeRefuseeOuAnnulee_409() {
		for (StatutHeuresSup statut : List.of(StatutHeuresSup.REFUSEE, StatutHeuresSup.ANNULEE, StatutHeuresSup.VALIDEE)) {
			demande.setStatut(statut);
			codeErreur(() -> service.valider(demande.getId()), "STATUT_INCOMPATIBLE", 409);
		}
		verify(notifier, never()).validee(any(), anyString(), any());
	}

	@Test
	void demandeInconnue_404() {
		codeErreur(() -> service.valider(UUID.randomUUID()), "DEMANDE_INTROUVABLE", 404);
	}

	@Test
	void rhBeneficiaire_parLeCompte_nePeutPasValider_403() {
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
	}

	@Test
	void rhBeneficiaire_parLaFiche_nePeutPasValider_403() {
		beneficiaire.setCompteId(null);
		beneficiaire.setCollaborateurId(ficheRh);

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
	}

	@Test
	void rhDemandeur_nePeutPasValiderSaPropreDemande_403() {
		demande.setDemandeurCompteId(compteRh.toString());

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
	}

	@Test
	void adminSansFiche_estControleParLeCompteSeul() {
		when(acteurResolver.avecFiche()).thenReturn(new Acteur(compteRh.toString(), null, "ADM01", null));
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
	}

	// --- Refus ---

	@Test
	void refusSansMotif_400_avecMotif_refusee_etLeDemandeurSeulEstNotifie() {
		codeErreur(() -> service.refuser(demande.getId(), " "), "MOTIF_OBLIGATOIRE", 400);
		codeErreur(() -> service.refuser(demande.getId(), null), "MOTIF_OBLIGATOIRE", 400);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);

		service.refuser(demande.getId(), "Budget épuisé");

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.REFUSEE);
		assertThat(demande.getMotifRefus()).isEqualTo("Budget épuisé");
		verify(jourRepository).deleteByDemandeId(demande.getId());
		verify(notifier).refusee(any(), anyString());
		verify(notifier, never()).validee(any(), anyString(), any());
	}

	@Test
	void refuserParUnBeneficiaire_403() {
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.refuser(demande.getId(), "non"), "AUTO_VALIDATION_INTERDITE", 403);
	}

	// --- Annulation par RH / ADMIN ---

	@Test
	void rhAnnuleUneDemandeValideeAvantLaPlage_beneficiairesNotifies_joursLiberes() {
		demande.setStatut(StatutHeuresSup.VALIDEE);

		service.annulerParBackoffice(demande.getId(), "Chantier reporté");

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.ANNULEE);
		assertThat(demande.getAnnuleParNom()).isEqualTo("Amel Trabelsi");
		assertThat(demande.getMotifAnnulation()).isEqualTo("Chantier reporté");
		verify(jourRepository).deleteByDemandeId(demande.getId());
		verify(notifier).annulee(any(), anyString(), any());
	}

	@Test
	void rhNePeutPasAnnulerUneDemandeEnAttente_409_ellelaRefuse() {
		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "STATUT_INCOMPATIBLE", 409);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
	}

	@Test
	void annulationApresLeDebutDeLaPlage_409() {
		demande.setStatut(StatutHeuresSup.VALIDEE);
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(60));

		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "STATUT_INCOMPATIBLE", 409);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
	}

	@Test
	void rhBeneficiaireNePeutPasAnnulerUneDemandeQuiLeConcerne_403() {
		demande.setStatut(StatutHeuresSup.VALIDEE);
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "AUTO_VALIDATION_INTERDITE", 403);
	}

	// --- Annulation par le demandeur (mobile) ---

	@Test
	void leDemandeurAnnuleSaDemandeEnAttente_aucuneNotification() {
		service.annulerParDemandeur(demande.getId());

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.ANNULEE);
		assertThat(demande.getAnnuleParCompteId()).isEqualTo(compteRo.toString());
		verify(jourRepository).deleteByDemandeId(demande.getId());
		verifyNoInteractions(notifier);
	}

	@Test
	void leDemandeurNePeutPasAnnulerUneDemandeValidee_403() {
		demande.setStatut(StatutHeuresSup.VALIDEE);

		assertThatThrownBy(() -> service.annulerParDemandeur(demande.getId())).isInstanceOf(SecurityException.class);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
	}

	@Test
	void unAutreQueLeDemandeurNePeutPasAnnuler_403_antiIdor() {
		when(accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());

		assertThatThrownBy(() -> service.annulerParDemandeur(demande.getId())).isInstanceOf(SecurityException.class);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
	}

	@Test
	void sansCompteDansLeJwt_403() {
		when(accessService.currentCompteId()).thenReturn(null);

		assertThatThrownBy(() -> service.annulerParDemandeur(demande.getId())).isInstanceOf(SecurityException.class);
	}

	@Test
	void annulerUneDemandeDejaRefusee_409() {
		demande.setStatut(StatutHeuresSup.REFUSEE);

		codeErreur(() -> service.annulerParDemandeur(demande.getId()), "STATUT_INCOMPATIBLE", 409);
	}

	// --- Expiration (D-P13.d) ---

	@Test
	void decisionSurUneDemandeDontLaFenetreEstOuverte_passeEnExpireePuis409() {
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(1));

		codeErreur(() -> service.valider(demande.getId()), "STATUT_INCOMPATIBLE", 409);

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EXPIREE);
		verify(jourRepository).deleteByDemandeId(demande.getId());
		verify(notifier).expiree(any(), anyString());
		verify(notifier, never()).validee(any(), anyString(), any());
	}

	@Test
	void refusEtAnnulationSurUneDemandeEchue_memeExpirationParesseuse() {
		demande.setDebutPlageTs(MAINTENANT);

		codeErreur(() -> service.refuser(demande.getId(), "non"), "STATUT_INCOMPATIBLE", 409);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EXPIREE);
	}

	@Test
	void leDemandeurQuiAnnuleUneDemandeEchue_409_etElleExpire() {
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(5));

		codeErreur(() -> service.annulerParDemandeur(demande.getId()), "STATUT_INCOMPATIBLE", 409);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EXPIREE);
	}

	@Test
	void planificateur_expireLesDemandesEchues_etLesAutresNon() {
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(10));

		service.expirer(demande.getId());

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EXPIREE);
		verify(notifier).expiree(any(), anyString());
	}

	@Test
	void planificateur_neToucheNiUneDemandeNonEchueNiUneDemandeDejaDecidee() {
		service.expirer(demande.getId());
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);

		demande.setStatut(StatutHeuresSup.VALIDEE);
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(10));
		service.expirer(demande.getId());
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		verifyNoInteractions(notifier);
	}

	@Test
	void idsAExpirer_interrogeLeDepotAvecLHorlogeInjectee() {
		when(demandeRepository.findByStatutAndDebutPlageTsLessThanEqual(StatutHeuresSup.EN_ATTENTE, MAINTENANT))
				.thenReturn(List.of(demande));

		assertThat(service.idsAExpirer()).containsExactly(demande.getId());
	}

	// --- Lectures ---

	@Test
	void listeAdmin_periodeInversee_400() {
		codeErreur(() -> service.lister(null, null, null, null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), 0, 20),
				"PERIODE_INVALIDE", 400);
	}

	@Test
	void vueDuRo_uneDemandeDUnAutreDemandeur_403() {
		demande.setDemandeurCompteId(UUID.randomUUID().toString());
		when(calculService.periode(any(), any())).thenReturn(new LocalDate[] {LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 2)});

		assertThatThrownBy(() -> service.mesDemandesComptees(null, null, demande.getId())).isInstanceOf(SecurityException.class);
		verify(calculService, never()).compter(any());
	}

	@Test
	void vueDuRo_sesPropresDemandes_filtreSurLeCompteDuJwt() {
		when(calculService.periode(any(), any())).thenReturn(new LocalDate[] {LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 2)});
		when(calculService.compter(any())).thenReturn(List.of());

		assertThat(service.mesDemandesComptees(null, null, demande.getId())).isEmpty();
		verify(calculService).compter(any());
	}

	@Test
	void beneficiairesPossibles_perimetreDuValideurDuJwt_actifsSeulement() {
		UUID fiche = UUID.randomUUID();
		when(referentielClient.monValideurPointage()).thenReturn(
				new ValideurPointage(ficheRo, compteRo, "Omar", UUID.randomUUID(), UUID.randomUUID(), "Chef"));
		when(referentielClient.perimetreValidation(ficheRo)).thenReturn(List.of(
				new CollaborateurResolu(fiche, null, "M2", "Zied", true, null, null, ficheRo, null),
				new CollaborateurResolu(UUID.randomUUID(), null, "M1", "Ali", true, null, null, ficheRo, null),
				new CollaborateurResolu(UUID.randomUUID(), null, "M3", "Inactif", false, null, null, ficheRo, null)));

		List<BeneficiairePossibleResponse> r = service.beneficiairesPossibles();

		assertThat(r).extracting(BeneficiairePossibleResponse::nom).containsExactly("Ali", "Zied");
	}

	// --- Recette A4 : 400 avant 404 / 403 ---

	@Test
	void vueDuRo_periodeInvalideAvantLe404EtLe403() {
		when(calculService.periode(any(), any())).thenThrow(PresenceMetierException.badRequest("PERIODE_INVALIDE", "x"));

		codeErreur(() -> service.mesDemandesComptees(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), UUID.randomUUID()),
				"PERIODE_INVALIDE", 400);
		demande.setDemandeurCompteId(UUID.randomUUID().toString());
		codeErreur(() -> service.mesDemandesComptees(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), demande.getId()),
				"PERIODE_INVALIDE", 400);
	}

	// --- Recette A6 : la notification de validation mentionne la fenêtre ET les heures sup ---

	@Test
	void notificationDeValidation_fenetreEtHeuresSupAvant() {
		java.util.Map<java.time.DayOfWeek, FenetreHoraireService.Horaire> horaires = new java.util.EnumMap<>(java.time.DayOfWeek.class);
		horaires.put(java.time.DayOfWeek.TUESDAY, new FenetreHoraireService.Horaire(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(15, 0)));
		when(fenetreService.horaires(demande.getSiteId())).thenReturn(horaires);

		service.valider(demande.getId());

		org.mockito.ArgumentCaptor<String> resume = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(notifier).validee(any(), resume.capture(), any());
		// 06:00 - 2 h d'ouverture - 60 min de tolérance = 03:00 ; fin 15:00 + 60 = 16:00 ; heures sup 04:00-06:00
		assertThat(resume.getValue()).contains("pointage ouvert 03:00–16:00, dont heures sup 04:00–06:00");
	}

	@Test
	void notificationDeValidation_heuresSupApres_etAvantEtApres() {
		java.util.Map<java.time.DayOfWeek, FenetreHoraireService.Horaire> horaires = new java.util.EnumMap<>(java.time.DayOfWeek.class);
		horaires.put(java.time.DayOfWeek.TUESDAY, new FenetreHoraireService.Horaire(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(15, 0)));
		when(fenetreService.horaires(demande.getSiteId())).thenReturn(horaires);
		demande.setOuvertureAvantMinutes(0);
		demande.setOuvertureApresMinutes(60);

		service.valider(demande.getId());

		org.mockito.ArgumentCaptor<String> resume = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(notifier).validee(any(), resume.capture(), any());
		assertThat(resume.getValue()).contains("pointage ouvert 05:00–17:00, dont heures sup 15:00–16:00");
		assertThat(HeuresSupNotifier.fenetreEtHeuresSup(
				FenetreHoraireService.fenetre(LocalDate.of(2026, 11, 3), horaires.get(java.time.DayOfWeek.TUESDAY), 60, 60, 120, 60), 120, 60))
				.isEqualTo("03:00–17:00, dont heures sup 04:00–06:00 et 15:00–16:00");
	}

	// --- Revue : l'anti-auto-validation (403) passe avant l'expiration paresseuse et le statut (409) ---

	@Test
	void beneficiaireQuiValideUneDemandeEchue_403_etLaDemandeNEstPasExpiree() {
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(1));
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
		codeErreur(() -> service.refuser(demande.getId(), "non"), "AUTO_VALIDATION_INTERDITE", 403);

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
		verify(notifier, never()).expiree(any(), anyString());
	}

	@Test
	void beneficiaireQuiValideUneDemandeDejaRefusee_403_pasDeStatutIncompatible409() {
		demande.setStatut(StatutHeuresSup.REFUSEE);
		beneficiaire.setCompteId(compteRh);

		codeErreur(() -> service.valider(demande.getId()), "AUTO_VALIDATION_INTERDITE", 403);
	}

	@Test
	void beneficiaireQuiAnnuleUneDemandeEnAttenteOuCommencee_403_avantLes409() {
		beneficiaire.setCompteId(compteRh);
		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "AUTO_VALIDATION_INTERDITE", 403);

		demande.setStatut(StatutHeuresSup.VALIDEE);
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(60));
		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "AUTO_VALIDATION_INTERDITE", 403);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
	}

	// --- Motif plafonne a 500 caracteres (comme a la creation) ---

	@Test
	void motifDeRefusOuDAnnulationDePlusDe500Caracteres_400_avantTout_500Acceptes() {
		String trop = "x".repeat(501);

		codeErreur(() -> service.refuser(demande.getId(), trop), "MOTIF_TROP_LONG", 400);
		codeErreur(() -> service.annulerParBackoffice(demande.getId(), trop), "MOTIF_TROP_LONG", 400);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);

		service.refuser(demande.getId(), "x".repeat(500));
		assertThat(demande.getMotifRefus()).hasSize(500);
	}

	@Test
	void annulationRhApresLeDebutDeLaPlage_reste409_memeApresUnChangementDHeures() {
		demande.setStatut(StatutHeuresSup.VALIDEE);
		demande.setDebutPlageTs(MAINTENANT.minusSeconds(3600));

		codeErreur(() -> service.annulerParBackoffice(demande.getId(), null), "STATUT_INCOMPATIBLE", 409);
	}
}
