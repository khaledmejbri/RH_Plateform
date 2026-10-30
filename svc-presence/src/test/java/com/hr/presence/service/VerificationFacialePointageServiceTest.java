package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.PlateformeMobile;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.domain.StatutReglageFacial;
import com.hr.presence.domain.VerificationFaciale;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.EnrolementFacialRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.ReglageFacialService.Etat;
import com.hr.presence.service.VerificationFacialePointageService.Decision;
import com.hr.presence.service.VerificationFacialePointageService.Preparation;
import com.hr.presence.web.PointageBloqueException;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-P06 à D-P10 : table de décision de l'étape visage, compteur d'échecs, blocage de 15 min, alerte au responsable,
 * ordre des contrôles (400 puis 409), identité (403), compatibilité ascendante (réglage INACTIVE).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationFacialePointageServiceTest {

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
	/** 10/11/2026 10:00 à Tunis. */
	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");
	private static final String APPAREIL = UUID.randomUUID().toString();
	private static final String AUTRE_APPAREIL = UUID.randomUUID().toString();

	@Mock
	private ReglageFacialService reglageService;
	@Mock
	private EnrolementFacialRepository enrolementRepository;
	@Mock
	private PresenceAccessService accessService;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private FacialAuditService auditService;
	@Mock
	private PresenceEventPublisher eventPublisher;

	private final String compteId = UUID.randomUUID().toString();
	private final String ficheId = UUID.randomUUID().toString();
	private final String valideurCompte = UUID.randomUUID().toString();
	private final String valideurFiche = UUID.randomUUID().toString();

	private VerificationFacialePointageService service;
	private PresenceEnrolementFacial ligne;

	@BeforeEach
	void setUp() {
		Clock horloge = Clock.fixed(MAINTENANT, TUNIS);
		service = new VerificationFacialePointageService(reglageService, enrolementRepository, accessService,
				referentielClient, auditService, eventPublisher, new PresenceProperties(), horloge);
		when(reglageService.etat()).thenReturn(actifExige(92));
		when(accessService.currentCompteId()).thenReturn(compteId);
		when(accessService.collaborateurHeaderOuNull()).thenReturn(ficheId);

		ligne = new PresenceEnrolementFacial();
		ligne.setId(UUID.randomUUID());
		ligne.setCompteId(compteId);
		ligne.setCollaborateurId(ficheId);
		ligne.setCollaborateurNom("Sarra Khemiri");
		ligne.setValideurId(valideurFiche);
		ligne.setValideurCompteId(valideurCompte);
		ligne.setStatut(StatutEnrolement.ENROLE);
		ligne.setDeviceId(APPAREIL);
		when(enrolementRepository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.of(ligne));
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.fromString(ficheId), UUID.fromString(compteId), "Sarra Khemiri",
				UUID.fromString(valideurFiche), UUID.fromString(valideurCompte), "Omar Belhadj"));
	}

	private static Etat actifExige(int seuil) {
		return new Etat(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 11, 1), seuil, true);
	}

	private static CreatePointageRequest requete(String appareil, Integer score, Boolean vivacite, PlateformeMobile plateforme) {
		return requete("ENTREE", appareil, score, vivacite, plateforme);
	}

	private static CreatePointageRequest requete(
			String type, String appareil, Integer score, Boolean vivacite, PlateformeMobile plateforme) {
		return new CreatePointageRequest("qr", type, 36.8, 10.1, 5.0, MAINTENANT, appareil, null, false,
				score, vivacite, "mfn-1", plateforme);
	}

	private Decision decider(CreatePointageRequest requete) {
		Preparation p = service.preparer(ficheId, requete, MAINTENANT);
		service.controlerBlocage(p, MAINTENANT);
		return service.decider(p, requete, MAINTENANT);
	}

	/** Preparation puis controle du blocage, dans l'ordre du pointage. */
	private void preparerEtControler(CreatePointageRequest requete, Instant instant) {
		service.controlerBlocage(service.preparer(ficheId, requete, instant), instant);
	}

	// --- Compatibilité ascendante (D-P06, D-P06.a, D-P06.b) ---

	@Test
	void reglageInactif_aucunEffet_memeAvecUnePreuveAberrante() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		CreatePointageRequest req = requete(APPAREIL, 150, null, PlateformeMobile.WEB);

		Preparation p = service.preparer("collab-demande", req, MAINTENANT);
		Decision d = service.decider(p, req, MAINTENANT);

		assertThat(p.controleExige()).isFalse();
		assertThat(p.collaborateurId()).isEqualTo("collab-demande");
		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isNull();
		assertThat(d.score()).isNull();
		verifyNoInteractions(enrolementRepository, referentielClient, auditService, eventPublisher);
	}

	@Test
	void activeAvantLaDate_aucunControle() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, false));

		Decision d = decider(requete(null, null, null, null));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isNull();
		verifyNoInteractions(enrolementRepository);
	}

	// --- D-P08.b : requête mal formée (400, jamais comptée) ---

	@Test
	void scoreSuperieurA100_400_nonCompte() {
		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 101, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("PREUVE_FACIALE_INVALIDE");
				});
		assertThat(ligne.getEchecsConsecutifs()).isZero();
		verify(enrolementRepository, never()).save(any());
	}

	@Test
	void deviceIdNonUuid_400_quandLeControleEstExige_etIgnoreSinon() {
		assertThatThrownBy(() -> service.preparer(ficheId, requete("x", 95, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("PREUVE_FACIALE_INVALIDE");
				});
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		assertThat(service.preparer(ficheId, requete("device-1", null, null, null), MAINTENANT).controleExige()).isFalse();
	}

	@Test
	void scoreNegatif_400() {
		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, -1, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
	}

	@Test
	void scoreSansResultatDuVivant_400() {
		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 95, null, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("PREUVE_FACIALE_INVALIDE");
				});
	}

	@Test
	void vivantEchoueSansScore_estValide_etRejeteVivacite() {
		Decision d = decider(requete(APPAREIL, null, false, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VIVACITE);
		assertThat(d.echecs()).isEqualTo(1);
	}

	// --- Identité (JWT) ---

	@Test
	void enteteDifferentDeLEnrolement_403_identiteIncoherente() {
		when(accessService.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 95, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(403);
					assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE");
				});
	}

	@Test
	void sansLigne_ficheLueAuReferentiel_enteteDifferent403() {
		when(enrolementRepository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.empty());
		when(accessService.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 95, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE"));
		verify(referentielClient).monValideurPointage();
	}

	@Test
	void sansEntete_laFicheDeLEnrolementFaitFoi() {
		when(accessService.collaborateurHeaderOuNull()).thenReturn(null);

		Preparation p = service.preparer("identifiant-du-jeton", requete(APPAREIL, 95, true, PlateformeMobile.ANDROID), MAINTENANT);

		assertThat(p.collaborateurId()).isEqualTo(ficheId);
	}

	@Test
	void compteIntrouvableDansLeJwt_refuse() {
		when(accessService.currentCompteId()).thenReturn(null);

		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 95, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOf(SecurityException.class);
	}

	// --- Blocage (D-P10.1, D-P08.a) ---

	@Test
	void blocageEnCours_409_avecFinDuBlocage_avantTout() {
		Instant fin = MAINTENANT.plusSeconds(300);
		ligne.setBloqueJusqua(fin);

		assertThatThrownBy(() -> preparerEtControler(requete(APPAREIL, 99, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PointageBloqueException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("POINTAGE_BLOQUE_VISAGE");
					assertThat(e.getBloqueJusqua()).isEqualTo(fin);
				});
		verify(enrolementRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void blocageEnCours_aussiPourUneDemandeDeReenrolement() {
		ligne.setStatut(StatutEnrolement.A_REENROLER);
		ligne.setBloqueJusqua(MAINTENANT.plusSeconds(60));

		assertThatThrownBy(() -> preparerEtControler(requete(APPAREIL, 99, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOf(PointageBloqueException.class);
	}

	@Test
	void blocageTermine_lePointageRedevientPossible() {
		ligne.setBloqueJusqua(MAINTENANT.minusSeconds(1));

		Decision d = decider(requete(APPAREIL, 95, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
	}

	@Test
	void ordreDesControles_400AvantLe409() {
		ligne.setBloqueJusqua(MAINTENANT.plusSeconds(300));

		assertThatThrownBy(() -> service.preparer(ficheId, requete(APPAREIL, 500, true, PlateformeMobile.ANDROID), MAINTENANT))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
	}

	// --- Table de décision (D-P08.c) ---

	@Test
	void nonEnrole_appareilIncompatible_validNonDisponible() {
		ligne.setStatut(StatutEnrolement.NON_ENROLE);
		ligne.setDeviceId(null);
		ligne.setDeviceIdIncompatible(APPAREIL);

		Decision d = decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isEqualTo(VerificationFaciale.NON_DISPONIBLE);
	}

	@Test
	void nonEnrole_web_validNonDisponible_sansLigne() {
		when(enrolementRepository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.empty());

		Decision d = decider(requete(null, null, null, PlateformeMobile.WEB));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isEqualTo(VerificationFaciale.NON_DISPONIBLE);
	}

	@Test
	void nonEnrole_appareilCompatible_rejeteAbsente_neutre() {
		when(enrolementRepository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.empty());

		Decision d = decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
		assertThat(d.motif()).contains("enrôlement");
		assertThat(d.echecs()).isNull();
		verify(enrolementRepository, never()).save(any());
	}

	@Test
	void nonEnrole_autreAppareilQueLIncompatible_rejeteAbsente() {
		ligne.setStatut(StatutEnrolement.NON_ENROLE);
		ligne.setDeviceId(null);
		ligne.setDeviceIdIncompatible(AUTRE_APPAREIL);

		Decision d = decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
	}

	@Test
	void refuse_validNonConsentie_sansDonneeBiometrique() {
		ligne.setStatut(StatutEnrolement.REFUSE);
		ligne.setDeviceId(null);

		Decision d = decider(requete(APPAREIL, 40, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isEqualTo(VerificationFaciale.NON_CONSENTIE);
		assertThat(d.score()).isNull();
		assertThat(d.vivacite()).isNull();
	}

	@Test
	void revoque_validNonConsentie_memeAvecUnBlocageResiduel() {
		ligne.setStatut(StatutEnrolement.REVOQUE);
		ligne.setDeviceId(null);
		ligne.setBloqueJusqua(MAINTENANT.plusSeconds(600));

		Decision d = decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(d.indicateur()).isEqualTo(VerificationFaciale.NON_CONSENTIE);
	}

	@Test
	void enrole_autreAppareil_rejeteAbsente_neutre() {
		Decision d = decider(requete(AUTRE_APPAREIL, 99, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
		assertThat(ligne.getEchecsConsecutifs()).isZero();
	}

	@Test
	void enrole_appareilNul_rejeteAbsente() {
		Decision d = decider(requete(null, 99, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
	}

	@Test
	void enrole_web_rejeteAbsente() {
		Decision d = decider(requete(APPAREIL, 99, true, PlateformeMobile.WEB));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
	}

	@Test
	void enrole_sansPreuve_rejeteAbsente_neutre() {
		Decision d = decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
		assertThat(ligne.getEchecsConsecutifs()).isZero();
	}

	@Test
	void enrole_vivantReussiSansScore_rejeteAbsente() {
		Decision d = decider(requete(APPAREIL, null, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
	}

	@Test
	void enrole_vivantEchoue_rejeteVivacite_plusUn() {
		Decision d = decider(requete(APPAREIL, 99, false, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VIVACITE);
		assertThat(d.echecs()).isEqualTo(1);
		assertThat(ligne.getEchecsConsecutifs()).isEqualTo(1);
		assertThat(ligne.getDernierEchecLe()).isEqualTo(MAINTENANT);
	}

	@Test
	void enrole_scoreSousLeSeuil_rejeteVisageNonReconnu_plusUn() {
		Decision d = decider(requete(APPAREIL, 91, true, PlateformeMobile.IOS));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		assertThat(d.score()).isEqualTo((short) 91);
		assertThat(ligne.getEchecsConsecutifs()).isEqualTo(1);
	}

	@Test
	void enrole_scoreAuSeuil_valideVerifiee_scoreStocke() {
		Decision d = decider(requete(APPAREIL, 92, true, PlateformeMobile.IOS));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(d.indicateur()).isEqualTo(VerificationFaciale.VERIFIEE);
		assertThat(d.score()).isEqualTo((short) 92);
		assertThat(d.vivacite()).isTrue();
		assertThat(d.modeleVersion()).isEqualTo("mfn-1");
		assertThat(d.plateforme()).isEqualTo(PlateformeMobile.IOS);
	}

	@Test
	void seuilReglable_85_accepte85_refuse84() {
		when(reglageService.etat()).thenReturn(actifExige(85));

		assertThat(decider(requete(APPAREIL, 85, true, PlateformeMobile.ANDROID)).statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(decider(requete(APPAREIL, 84, true, PlateformeMobile.ANDROID)).statut())
				.isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
	}

	@Test
	void aReenroler_depuisLAncienAppareil_controleNormal() {
		ligne.setStatut(StatutEnrolement.A_REENROLER);
		ligne.setDeviceIdDemande(AUTRE_APPAREIL);

		assertThat(decider(requete(APPAREIL, 95, true, PlateformeMobile.ANDROID)).statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID)).statut())
				.isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
	}

	@Test
	void aReenroler_depuisUnAutreAppareil_rejeteAbsente() {
		ligne.setStatut(StatutEnrolement.A_REENROLER);

		Decision d = decider(requete(AUTRE_APPAREIL, 99, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);
	}

	// --- Compteur d'échecs, blocage, alerte (D-P10) ---

	@Test
	void troisiemeEchec_bloque15Minutes_alerteLeResponsable_auditee_compteurRemisAZero() {
		Decision d1 = decider(requete("ENTREE", APPAREIL, 50, true, PlateformeMobile.ANDROID));
		Decision d2 = decider(requete("SORTIE", APPAREIL, null, false, PlateformeMobile.ANDROID));
		assertThat(d1.echecs()).isEqualTo(1);
		assertThat(d2.echecs()).isEqualTo(2);
		verifyNoInteractions(eventPublisher);

		Decision d3 = decider(requete("ENTREE", APPAREIL, 50, true, PlateformeMobile.ANDROID));

		Instant fin = MAINTENANT.plusSeconds(15 * 60);
		assertThat(d3.statut()).isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		assertThat(d3.echecs()).isEqualTo(3);
		assertThat(d3.bloqueJusqua()).isEqualTo(fin);
		assertThat(ligne.getBloqueJusqua()).isEqualTo(fin);
		assertThat(ligne.getEchecsConsecutifs()).isZero();
		verify(auditService).tracer(eq(ActionAuditFacial.BLOCAGE_ECHECS), eq(ligne.getId()), any(), any(), any(), any(), anyString());
		verify(eventPublisher).notifier(eq(valideurCompte), anyString(), anyString());
		verify(eventPublisher, org.mockito.Mockito.times(1)).notifier(anyString(), anyString(), anyString());
	}

	@Test
	void alerte_sansResponsable_repliSurLeDestinataireRH_jamaisAdmin() {
		ligne.setValideurId(null);
		ligne.setValideurCompteId(null);
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.fromString(ficheId), UUID.fromString(compteId), "Sarra Khemiri", null, null, null));
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(MAINTENANT.minusSeconds(60));

		decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID));

		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString());
		verify(eventPublisher, org.mockito.Mockito.times(1)).notifier(anyString(), anyString(), anyString());
	}

	@Test
	void alerte_referentielIndisponible_repliSurLInstantane() {
		when(referentielClient.monValideurPointage()).thenThrow(new IllegalStateException("référentiel hors ligne"));
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(MAINTENANT.minusSeconds(60));

		decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID));

		verify(eventPublisher).notifier(eq(valideurCompte), anyString(), anyString());
	}

	@Test
	void alerte_uneSeuleFois_lesTentativesBloqueesFont409SansCompter() {
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(MAINTENANT.minusSeconds(60));
		decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID));

		assertThatThrownBy(() -> preparerEtControler(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID), MAINTENANT.plusSeconds(30)))
				.isInstanceOf(PointageBloqueException.class);

		verify(eventPublisher, org.mockito.Mockito.times(1)).notifier(anyString(), anyString(), anyString());
		assertThat(ligne.getEchecsConsecutifs()).isZero();
	}

	@Test
	void pointageReussi_remetLeCompteurAZero() {
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(MAINTENANT.minusSeconds(120));

		Decision d = decider(requete(APPAREIL, 96, true, PlateformeMobile.ANDROID));

		assertThat(d.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(ligne.getEchecsConsecutifs()).isZero();
		assertThat(ligne.getDernierEchecLe()).isNull();
	}

	@Test
	void rejetsNeutres_nIncremententNiNeRemettentAZero() {
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(MAINTENANT.minusSeconds(120));

		decider(requete(AUTRE_APPAREIL, 99, true, PlateformeMobile.ANDROID));
		decider(requete(APPAREIL, null, null, PlateformeMobile.ANDROID));

		assertThat(ligne.getEchecsConsecutifs()).isEqualTo(2);
	}

	@Test
	void changementDeJourTunis_leCompteurRepartDeZero() {
		// Deux échecs la veille (heure de Tunis) : le nouvel échec est le premier du jour.
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(Instant.parse("2026-11-09T21:30:00Z")); // 09/11 22:30 à Tunis

		Decision d = decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID));

		assertThat(d.echecs()).isEqualTo(1);
		assertThat(d.bloqueJusqua()).isNull();
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void memeJourTunis_leCompteurContinue() {
		// 23:30 UTC la veille = 00:30 à Tunis le jour même : même jour calendaire que MAINTENANT (10:00 Tunis).
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(Instant.parse("2026-11-09T23:30:00Z"));

		Decision d = decider(requete(APPAREIL, 10, true, PlateformeMobile.ANDROID));

		assertThat(d.echecs()).isEqualTo(3);
		assertThat(d.bloqueJusqua()).isNotNull();
	}
}
