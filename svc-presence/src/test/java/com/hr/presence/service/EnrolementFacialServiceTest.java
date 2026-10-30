package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.RaisonIncompatibilite;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.domain.StatutReglageFacial;
import com.hr.presence.dto.FacialMobileDtos.AppareilIncompatibleRequest;
import com.hr.presence.dto.FacialMobileDtos.ConsentementDecisionRequest;
import com.hr.presence.dto.FacialMobileDtos.DemandeReenrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EnrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EtatFacialMoi;
import com.hr.presence.entity.PresenceConsentementVersion;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.EnrolementFacialRepository;
import com.hr.presence.service.EnrolementFacialService.ResultatEnrolement;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.service.ReglageFacialService.Etat;
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
import static org.mockito.Mockito.when;

/** D-P07 : états, consentement, enrôlement, retrait, appareil incompatible, demande de ré-enrôlement. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrolementFacialServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");
	private static final String V1 = "v1";
	private static final String V2 = "v2";

	@Mock
	private EnrolementFacialRepository repository;
	@Mock
	private FacialIdentiteService identiteService;
	@Mock
	private ReglageFacialService reglageService;
	@Mock
	private ConsentementFacialService consentementService;
	@Mock
	private FacialAuditService auditService;
	@Mock
	private PresenceEventPublisher eventPublisher;

	private final String compteId = UUID.randomUUID().toString();
	private final UUID ficheId = UUID.randomUUID();
	private final String appareil = UUID.randomUUID().toString();
	private final String autreAppareil = UUID.randomUUID().toString();

	private EnrolementFacialService service;
	private PresenceEnrolementFacial ligne;

	@BeforeEach
	void setUp() {
		service = new EnrolementFacialService(repository, identiteService, reglageService, consentementService,
				auditService, eventPublisher, Clock.fixed(MAINTENANT, ZoneId.of("Africa/Tunis")));
		when(identiteService.resoudre()).thenReturn(new Identite(compteId, new ValideurPointage(
				ficheId, UUID.fromString(compteId), "Sarra Khemiri", UUID.randomUUID(), UUID.randomUUID(), "Omar")));
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, false));
		publier(V1);
		when(repository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any(PresenceEnrolementFacial.class))).thenAnswer(inv -> {
			PresenceEnrolementFacial e = inv.getArgument(0);
			if (e.getId() == null) {
				e.setId(UUID.randomUUID());
			}
			return e;
		});
		when(repository.save(any(PresenceEnrolementFacial.class))).thenAnswer(inv -> {
			PresenceEnrolementFacial e = inv.getArgument(0);
			if (e.getId() == null) {
				e.setId(UUID.randomUUID());
			}
			return e;
		});
	}

	private void publier(String version) {
		when(consentementService.courante()).thenReturn(
				Optional.of(new PresenceConsentementVersion(version, "texte", "rh", MAINTENANT)));
	}

	private void ligne(StatutEnrolement statut, String device, String versionAcceptee) {
		ligne = new PresenceEnrolementFacial();
		ligne.setId(UUID.randomUUID());
		ligne.setCompteId(compteId);
		ligne.setCollaborateurId(ficheId.toString());
		ligne.setStatut(statut);
		ligne.setDeviceId(device);
		ligne.setVersionConsentement(versionAcceptee);
		when(repository.findByCompteIdForUpdate(compteId)).thenReturn(Optional.of(ligne));
	}

	private EnrolementRequest enrolement(String device, String version, boolean vivant, String cle) {
		return new EnrolementRequest(device, version, vivant, "mfn-1", cle);
	}

	private static PresenceMetierException erreur(Throwable t) {
		return (PresenceMetierException) t;
	}

	private void assertErreur(Runnable action, String code, int statut) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getHttpStatus()).isEqualTo(statut);
		});
	}

	// --- /moi ---

	@Test
	void moi_sansLigne_nonEnrole_ecranDeConsentementRequis_surReglageActif() {
		EtatFacialMoi etat = service.moi(appareil);

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.NON_ENROLE);
		assertThat(etat.verificationActive()).isTrue();
		assertThat(etat.controleExige()).isFalse();
		assertThat(etat.ecranConsentementRequis()).isTrue();
		assertThat(etat.versionConsentementCourante()).isEqualTo(V1);
		assertThat(etat.appareilCorrespond()).isNull();
		assertThat(etat.gabaritAEffacer()).isTrue();
	}

	@Test
	void moi_deviceIdNonUuid_400_parametreInvalide() {
		assertErreur(() -> service.moi("x"), "PARAMETRE_INVALIDE", 400);
	}

	@Test
	void moi_reglageInactif_aucunEcran_memeNonEnrole() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));

		EtatFacialMoi etat = service.moi(appareil);

		assertThat(etat.verificationActive()).isFalse();
		assertThat(etat.ecranConsentementRequis()).isFalse();
	}

	@Test
	void moi_enroleSurLAppareil_appareilCorrespond_gabaritConserve() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		EtatFacialMoi etat = service.moi(appareil);

		assertThat(etat.appareilCorrespond()).isTrue();
		assertThat(etat.gabaritAEffacer()).isFalse();
		assertThat(etat.versionConsentementAcceptee()).isEqualTo(V1);
		assertThat(etat.reconsentementRequis()).isFalse();
	}

	@Test
	void moi_enroleSurUnAutreAppareil_appareilNeCorrespondPas_gabaritAEffacer() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		EtatFacialMoi etat = service.moi(autreAppareil);

		assertThat(etat.appareilCorrespond()).isFalse();
		assertThat(etat.gabaritAEffacer()).isTrue();
	}

	@Test
	void moi_nouvelleVersionDuTexte_reconsentementRequis() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		publier(V2);

		EtatFacialMoi etat = service.moi(appareil);

		assertThat(etat.reconsentementRequis()).isTrue();
		assertThat(etat.ecranConsentementRequis()).isTrue();
	}

	@Test
	void moi_refuse_aucunEcran_gabaritAEffacer() {
		ligne(StatutEnrolement.REFUSE, null, V1);

		EtatFacialMoi etat = service.moi(appareil);

		assertThat(etat.ecranConsentementRequis()).isFalse();
		assertThat(etat.gabaritAEffacer()).isTrue();
	}

	@Test
	void moi_blocageEnCours_renvoieLaFinDuBlocage_passe_nul() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		ligne.setBloqueJusqua(MAINTENANT.plusSeconds(60));
		assertThat(service.moi(appareil).bloqueJusqua()).isEqualTo(MAINTENANT.plusSeconds(60));

		ligne.setBloqueJusqua(MAINTENANT.minusSeconds(60));
		assertThat(service.moi(appareil).bloqueJusqua()).isNull();
	}

	@Test
	void moi_echecDUnJourPrecedent_compteurAffichePeuZero() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		ligne.setEchecsConsecutifs(2);
		ligne.setDernierEchecLe(Instant.parse("2026-11-08T09:00:00Z"));

		assertThat(service.moi(appareil).echecsConsecutifs()).isZero();
	}

	@Test
	void moi_appareilMarqueIncompatible() {
		ligne(StatutEnrolement.NON_ENROLE, null, null);
		ligne.setDeviceIdIncompatible(appareil);

		assertThat(service.moi(appareil).appareilIncompatible()).isTrue();
		assertThat(service.moi(autreAppareil).appareilIncompatible()).isFalse();
	}

	// --- enrôlement ---

	@Test
	void enrolement_premierEnrolementLibre_201_ligneCreee_auditee() {
		String cle = UUID.randomUUID().toString();

		ResultatEnrolement r = service.enroler(enrolement(appareil, V1, true, cle));

		assertThat(r.cree()).isTrue();
		assertThat(r.etat().statut()).isEqualTo(StatutEnrolement.ENROLE);
		assertThat(r.etat().appareilCorrespond()).isTrue();
		verify(repository).saveAndFlush(any(PresenceEnrolementFacial.class));
		verify(auditService).tracer(eq(ActionAuditFacial.ENROLEMENT), any(), eq(StatutEnrolement.NON_ENROLE),
				eq(StatutEnrolement.ENROLE), eq(appareil), eq(V1), anyString());
	}

	@Test
	void enrolement_depuisRefuse_libre() {
		ligne(StatutEnrolement.REFUSE, null, V1);

		ResultatEnrolement r = service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString()));

		assertThat(r.etat().statut()).isEqualTo(StatutEnrolement.ENROLE);
		assertThat(ligne.getDeviceId()).isEqualTo(appareil);
	}

	@Test
	void enrolement_memeCleIdempotence_200_identique_sansNouvelAudit() {
		String cle = UUID.randomUUID().toString();
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		ligne.setIdempotencyKeyEnrolement(cle);

		ResultatEnrolement r = service.enroler(enrolement(appareil, V1, true, cle));

		assertThat(r.cree()).isFalse();
		assertThat(r.etat().statut()).isEqualTo(StatutEnrolement.ENROLE);
		verify(auditService, never()).tracer(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void enrolement_dejaEnroleMemeAppareil_409() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())), "DEJA_ENROLE", 409);
	}

	@Test
	void enrolement_enroleSurUnAutreAppareil_409_reenrolementADebloquer() {
		ligne(StatutEnrolement.ENROLE, autreAppareil, V1);
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())),
				"REENROLEMENT_A_DEBLOQUER", 409);
	}

	@Test
	void enrolement_revoqueOuAReenroler_409_reenrolementADebloquer() {
		ligne(StatutEnrolement.REVOQUE, null, V1);
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())),
				"REENROLEMENT_A_DEBLOQUER", 409);
		ligne(StatutEnrolement.A_REENROLER, autreAppareil, V1);
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())),
				"REENROLEMENT_A_DEBLOQUER", 409);
	}

	@Test
	void enrolement_appareilMarqueIncompatible_reussi_effaceLeMarquage_etAudite() {
		ligne(StatutEnrolement.NON_ENROLE, null, null);
		ligne.setDeviceIdIncompatible(appareil);

		ResultatEnrolement r = service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString()));

		assertThat(r.cree()).isTrue();
		assertThat(r.etat().statut()).isEqualTo(StatutEnrolement.ENROLE);
		assertThat(r.etat().appareilIncompatible()).isFalse();
		assertThat(ligne.getDeviceIdIncompatible()).isNull();
		verify(auditService).tracer(eq(ActionAuditFacial.ENROLEMENT), any(), eq(StatutEnrolement.NON_ENROLE),
				eq(StatutEnrolement.ENROLE), eq(appareil), eq(V1), org.mockito.ArgumentMatchers.contains("marquage appareil incompatible"));
	}

	@Test
	void enrolement_marquageDUnAutreAppareil_conserve() {
		ligne(StatutEnrolement.NON_ENROLE, null, null);
		ligne.setDeviceIdIncompatible(autreAppareil);

		service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString()));

		assertThat(ligne.getDeviceIdIncompatible()).isEqualTo(autreAppareil);
	}

	@Test
	void creationConcurrenteDeLaLigne_409_decisionDejaEnregistree_jamais500() {
		when(repository.saveAndFlush(any(PresenceEnrolementFacial.class)))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_presence_enrolement_compte"));

		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())), "DECISION_DEJA_ENREGISTREE", 409);
		assertErreur(() -> service.refuserConsentement(new ConsentementDecisionRequest(V1)), "DECISION_DEJA_ENREGISTREE", 409);
		assertErreur(() -> service.signalerAppareilIncompatible(
				new AppareilIncompatibleRequest(appareil, RaisonIncompatibilite.SANS_CAMERA_FRONTALE)), "DECISION_DEJA_ENREGISTREE", 409);
	}

	@Test
	void enrolement_reglageInactif_409() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())),
				"VERIFICATION_FACIALE_INACTIVE", 409);
	}

	@Test
	void enrolement_versionObsolete_422() {
		publier(V2);
		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())),
				"VERSION_CONSENTEMENT_OBSOLETE", 422);
	}

	@Test
	void enrolement_vivantEchoue_422() {
		assertErreur(() -> service.enroler(enrolement(appareil, V1, false, UUID.randomUUID().toString())),
				"VIVACITE_ECHOUEE", 422);
		verify(repository, never()).save(any());
	}

	// --- reconsentement (D-P07.f) ---

	@Test
	void acceptation_nouvelleVersion_resteEnrole_versionMiseAJour() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		publier(V2);

		EtatFacialMoi etat = service.accepterConsentement(new ConsentementDecisionRequest(V2));

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.ENROLE);
		assertThat(ligne.getVersionConsentement()).isEqualTo(V2);
		verify(auditService).tracer(eq(ActionAuditFacial.CONSENTEMENT_ACCEPTE), any(), any(), any(), any(), eq(V2), anyString());
	}

	@Test
	void acceptation_versionDejaAcceptee_409_reconsentementNonAttendu() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.accepterConsentement(new ConsentementDecisionRequest(V1)), "RECONSENTEMENT_NON_ATTENDU", 409);
	}

	@Test
	void acceptation_statutNonEnrole_409() {
		ligne(StatutEnrolement.REFUSE, null, V1);
		assertErreur(() -> service.accepterConsentement(new ConsentementDecisionRequest(V1)), "RECONSENTEMENT_NON_ATTENDU", 409);
	}

	@Test
	void acceptation_ancienneVersion_422() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		publier(V2);
		assertErreur(() -> service.accepterConsentement(new ConsentementDecisionRequest(V1)), "VERSION_CONSENTEMENT_OBSOLETE", 422);
	}

	@Test
	void refus_nonEnrole_devientRefuse() {
		EtatFacialMoi etat = service.refuserConsentement(new ConsentementDecisionRequest(V1));

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.REFUSE);
		verify(auditService).tracer(eq(ActionAuditFacial.CONSENTEMENT_REFUSE), any(), eq(StatutEnrolement.NON_ENROLE),
				eq(StatutEnrolement.REFUSE), any(), eq(V1), any());
	}

	@Test
	void refus_reconsentementEnrole_devientRevoque_appareilInvalide() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		publier(V2);

		EtatFacialMoi etat = service.refuserConsentement(new ConsentementDecisionRequest(V2));

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.REVOQUE);
		assertThat(ligne.getDeviceId()).isNull();
		assertThat(etat.gabaritAEffacer()).isTrue();
	}

	@Test
	void refus_decisionDejaEnregistree_409() {
		ligne(StatutEnrolement.REFUSE, null, V1);
		assertErreur(() -> service.refuserConsentement(new ConsentementDecisionRequest(V1)), "DECISION_DEJA_ENREGISTREE", 409);
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.refuserConsentement(new ConsentementDecisionRequest(V1)), "DECISION_DEJA_ENREGISTREE", 409);
	}

	// --- retrait (D-P06.b : permis même INACTIVE) ---

	@Test
	void retrait_enrole_devientRevoque_appareilInvalide_audite() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		EtatFacialMoi etat = service.retirerConsentement();

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.REVOQUE);
		assertThat(ligne.getDeviceId()).isNull();
		verify(auditService).tracer(eq(ActionAuditFacial.RETRAIT), any(), eq(StatutEnrolement.ENROLE),
				eq(StatutEnrolement.REVOQUE), eq(appareil), any(), any());
	}

	@Test
	void retrait_reglageInactif_permis() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		assertThat(service.retirerConsentement().statut()).isEqualTo(StatutEnrolement.REVOQUE);
	}

	@Test
	void retrait_dejaRevoque_200_sansChangement_sansAudit() {
		ligne(StatutEnrolement.REVOQUE, null, V1);

		assertThat(service.retirerConsentement().statut()).isEqualTo(StatutEnrolement.REVOQUE);
		verify(auditService, never()).tracer(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void retrait_nonEnroleOuRefuse_409_aucunConsentementActif() {
		assertErreur(service::retirerConsentement, "AUCUN_CONSENTEMENT_ACTIF", 409);
		ligne(StatutEnrolement.REFUSE, null, V1);
		assertErreur(service::retirerConsentement, "AUCUN_CONSENTEMENT_ACTIF", 409);
	}

	// --- appareil incompatible (D-P07.g) ---

	@Test
	void appareilIncompatible_resteNonEnrole_marqueEtAudite() {
		EtatFacialMoi etat = service.signalerAppareilIncompatible(
				new AppareilIncompatibleRequest(appareil, RaisonIncompatibilite.SANS_CAMERA_FRONTALE));

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.NON_ENROLE);
		assertThat(etat.appareilIncompatible()).isTrue();
		verify(auditService).tracer(eq(ActionAuditFacial.APPAREIL_INCOMPATIBLE), any(), any(), any(), eq(appareil), any(),
				eq("SANS_CAMERA_FRONTALE"));
	}

	@Test
	void appareilIncompatible_dejaEnrole_409_statutIncompatible() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.signalerAppareilIncompatible(
				new AppareilIncompatibleRequest(appareil, RaisonIncompatibilite.MODELE_NON_CHARGEABLE)), "STATUT_INCOMPATIBLE", 409);
	}

	@Test
	void appareilIncompatible_reglageInactif_409() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		assertErreur(() -> service.signalerAppareilIncompatible(
				new AppareilIncompatibleRequest(appareil, RaisonIncompatibilite.MODELE_NON_CHARGEABLE)),
				"VERIFICATION_FACIALE_INACTIVE", 409);
	}

	// --- demande de ré-enrôlement (D-P07.c) ---

	@Test
	void demande_nouvelAppareil_aReenroler_ancienAppareilConserve_notifieRH() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		EtatFacialMoi etat = service.demanderReenrolement(new DemandeReenrolementRequest(autreAppareil, null));

		assertThat(etat.statut()).isEqualTo(StatutEnrolement.A_REENROLER);
		assertThat(ligne.getDeviceId()).isEqualTo(appareil);
		assertThat(ligne.getDeviceIdDemande()).isEqualTo(autreAppareil);
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString());
		verify(auditService).tracer(eq(ActionAuditFacial.DEMANDE_REENROLEMENT), any(), eq(StatutEnrolement.ENROLE),
				eq(StatutEnrolement.A_REENROLER), eq(autreAppareil), any(), any());
	}

	@Test
	void demande_depuisLAppareilEnroleSansGabarit_acceptee() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);

		assertThat(service.demanderReenrolement(new DemandeReenrolementRequest(appareil, false)).statut())
				.isEqualTo(StatutEnrolement.A_REENROLER);
	}

	@Test
	void demande_appareilEnroleAvecGabarit_409_dejaEnrole() {
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.demanderReenrolement(new DemandeReenrolementRequest(appareil, true)), "DEJA_ENROLE", 409);
	}

	@Test
	void demande_revoque_aReenroler() {
		ligne(StatutEnrolement.REVOQUE, null, V1);

		assertThat(service.demanderReenrolement(new DemandeReenrolementRequest(appareil, null)).statut())
				.isEqualTo(StatutEnrolement.A_REENROLER);
	}

	@Test
	void demande_dejaEnCours_409() {
		ligne(StatutEnrolement.A_REENROLER, appareil, V1);
		assertErreur(() -> service.demanderReenrolement(new DemandeReenrolementRequest(autreAppareil, null)),
				"DEMANDE_DEJA_EN_COURS", 409);
	}

	@Test
	void demande_nonEnroleOuRefuse_409_statutIncompatible() {
		assertErreur(() -> service.demanderReenrolement(new DemandeReenrolementRequest(appareil, null)), "STATUT_INCOMPATIBLE", 409);
		ligne(StatutEnrolement.REFUSE, null, V1);
		assertErreur(() -> service.demanderReenrolement(new DemandeReenrolementRequest(appareil, null)), "STATUT_INCOMPATIBLE", 409);
	}

	@Test
	void demande_reglageInactif_409() {
		when(reglageService.etat()).thenReturn(new Etat(StatutReglageFacial.INACTIVE, null, 92, false));
		ligne(StatutEnrolement.ENROLE, appareil, V1);
		assertErreur(() -> service.demanderReenrolement(new DemandeReenrolementRequest(autreAppareil, null)),
				"VERIFICATION_FACIALE_INACTIVE", 409);
	}

	@Test
	void identiteIncoherente_propagee_aucuneEcriture() {
		when(identiteService.resoudre()).thenThrow(new PresenceMetierException("IDENTITE_INCOHERENTE", "x", 403));

		assertErreur(() -> service.enroler(enrolement(appareil, V1, true, UUID.randomUUID().toString())), "IDENTITE_INCOHERENTE", 403);
		verify(repository, never()).save(any());
	}
}
