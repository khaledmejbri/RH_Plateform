package com.hr.presence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.QrCredentialStatut;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.PointageResponse;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceQrCredential;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceQrCredentialRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencePointageServiceTest {

	@Mock
	private PresencePointageRepository pointageRepository;
	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private PresenceQrCredentialRepository qrRepository;
	@Mock
	private PresenceQrService qrService;
	@Mock
	private VerificationFacialePointageService verificationFaciale;

	private QrTokenService qrTokenService;
	private QrDynamiqueService qrDynamique;
	private PresencePointageService pointageService;

	private PresenceSite site;
	private PresenceQrCredential credential;
	private String validToken;
	private Instant now;

	@BeforeEach
	void setUp() {
		PresenceProperties props = new PresenceProperties();
		props.getQr().setHmacSecret("test-secret-hmac-presence");
		props.getGeofence().setDefaultRadiusMetres(50);
		props.getGeofence().setMaxAccuracyMetres(100);
		qrTokenService = new QrTokenService(props, new ObjectMapper());
		qrDynamique = new QrDynamiqueService(props);

		pointageService = new PresencePointageService(
				pointageRepository,
				siteRepository,
				qrRepository,
				qrTokenService,
				new HaversineService(),
				props,
				qrService,
				qrDynamique,
				verificationFaciale,
				java.time.Clock.systemUTC());

		now = Instant.now();
		UUID siteId = UUID.randomUUID();
		site = new PresenceSite();
		site.setId(siteId);
		site.setCode("HQ");
		site.setLibelle("HQ");
		site.setLatitude(36.8065);
		site.setLongitude(10.1815);
		site.setRayonMetres(50);
		site.setActif(true);

		UUID jti = UUID.randomUUID();
		credential = new PresenceQrCredential();
		credential.setId(UUID.randomUUID());
		credential.setSite(site);
		credential.setQrVersion(1);
		credential.setJti(jti);
		credential.setValidFrom(now.minus(1, ChronoUnit.HOURS));
		credential.setValidUntil(now.plus(90, ChronoUnit.DAYS));
		credential.setStatut(QrCredentialStatut.ACTIF);

		validToken = qrTokenService.buildToken(siteId, 1, jti, credential.getValidFrom(), credential.getValidUntil());

		// Réglage INACTIVE : l'étape visage n'a aucun effet (comportement antérieur).
		org.mockito.Mockito.lenient().when(verificationFaciale.preparer(any(), any(), any())).thenAnswer(inv ->
				VerificationFacialePointageService.Preparation.inactive(inv.getArgument(0)));
		org.mockito.Mockito.lenient().when(verificationFaciale.decider(any(), any(), any()))
				.thenReturn(VerificationFacialePointageService.Decision.sansControle());
		org.mockito.Mockito.lenient().when(pointageRepository.save(any(PresencePointage.class))).thenAnswer(inv -> {
			PresencePointage p = inv.getArgument(0);
			if (p.getId() == null) {
				p.setId(UUID.randomUUID());
			}
			return p;
		});
	}

	@Test
	void happyPath_valide() {
		when(qrRepository.findByJti(credential.getJti())).thenReturn(Optional.of(credential));
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));

		CreatePointageRequest req = new CreatePointageRequest(
				validToken, "ENTREE", 36.8065, 10.1815, 10.0, now, "device-1", null, null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(resp.siteId()).isEqualTo(site.getId());
		assertThat(resp.distanceMetres()).isNotNull().isLessThanOrEqualTo(50.0);
	}

	@Test
	void horsZone_50_1m() {
		when(qrRepository.findByJti(credential.getJti())).thenReturn(Optional.of(credential));
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));

		double latFar = 36.8065 + (55.0 / 111_320.0);
		CreatePointageRequest req = new CreatePointageRequest(
				validToken, "ENTREE", latFar, 10.1815, 10.0, now, null, null, null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_HORS_ZONE);
		assertThat(resp.distanceMetres()).isGreaterThan(50.0);
	}

	@Test
	void qrInvalide_signature() {
		CreatePointageRequest req = new CreatePointageRequest(
				"p1.abc.def", "ENTREE", 36.8065, 10.1815, 10.0, now, null, null, null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_INVALIDE);
		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getStatut()).isEqualTo(PointageStatut.REJETE_QR_INVALIDE);
	}

	@Test
	void qrExpire() {
		credential.setValidUntil(now.minus(1, ChronoUnit.DAYS));
		credential.setStatut(QrCredentialStatut.EXPIRE);
		String expiredToken = qrTokenService.buildToken(
				site.getId(), 1, credential.getJti(),
				now.minus(100, ChronoUnit.DAYS), credential.getValidUntil());

		when(qrRepository.findByJti(credential.getJti())).thenReturn(Optional.of(credential));

		CreatePointageRequest req = new CreatePointageRequest(
				expiredToken, "SORTIE", 36.8065, 10.1815, 5.0, now, null, null, null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_EXPIRE);
	}

	@Test
	void idempotence_rejoueMemeReponse() {
		PresencePointage existing = new PresencePointage();
		existing.setId(UUID.randomUUID());
		existing.setCollaborateurId("collab-1");
		existing.setStatut(PointageStatut.VALIDE);
		existing.setType(com.hr.presence.domain.PointageType.ENTREE);
		existing.setServerTs(now);
		existing.setSiteId(site.getId());
		existing.setLatitude(36.8065);
		existing.setLongitude(10.1815);
		existing.setIdempotencyKey("idem-1");

		when(pointageRepository.findByCollaborateurIdAndIdempotencyKey("collab-1", "idem-1"))
				.thenReturn(Optional.of(existing));

		CreatePointageRequest req = new CreatePointageRequest(
				validToken, "ENTREE", 36.8065, 10.1815, 10.0, now, null, "idem-1", null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.id()).isEqualTo(existing.getId());
		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
	}

	// --- D-P01 (SCRUM-35) ---

	private CreatePointageRequest requete(double lat, Double precision, Boolean simulee) {
		return new CreatePointageRequest(validToken, "ENTREE", lat, 10.1815, precision, now, "device-1", null, simulee);
	}

	private void qrEtSiteValides() {
		when(qrRepository.findByJti(credential.getJti())).thenReturn(Optional.of(credential));
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
	}

	@Test
	void precisionAbsente_rejetee() {
		qrEtSiteValides();

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, null, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_PRECISION_GPS);
		assertThat(resp.motifRejet()).contains("précision non fournie");
	}

	@Test
	void precisionSuperieureAuRayon_rejetee() {
		site.setTypeLieu(com.hr.presence.domain.TypeLieu.EXTERIEUR);
		site.setRayonMetres(20);
		qrEtSiteValides();

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 35.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_PRECISION_GPS);
		assertThat(resp.accuracyMetres()).isEqualTo(35.0);
	}

	@Test
	void precisionEgaleAuRayon_acceptee() {
		site.setRayonMetres(20);
		qrEtSiteValides();

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 20.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
	}

	@Test
	void horsZone_rayonExterieur20m() {
		site.setRayonMetres(20);
		qrEtSiteValides();
		double lat25m = 36.8065 + (25.0 / 111_320.0);

		PointageResponse resp = pointageService.pointer("collab-1", requete(lat25m, 8.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_HORS_ZONE);
	}

	@Test
	void positionSimulee_rejeteeEtTracee() {
		qrEtSiteValides();

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 5.0, true));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_POSITION_SIMULEE);
		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getPositionSimulee()).isTrue();
	}

	// --- D-P05 (SCRUM-39) : borne écran ---

	private void siteEnModeEcran(int version) {
		site.setModeQr(com.hr.presence.domain.ModeQr.ECRAN);
		site.setBorneJetonHash("hash");
		site.setBorneVersion(version);
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
	}

	private CreatePointageRequest requeteQr(String qr) {
		return new CreatePointageRequest(qr, "ENTREE", 36.8065, 10.1815, 8.0, now, "device-1", null, false);
	}

	@Test
	void borneEcran_qrCourant_valide() {
		siteEnModeEcran(2);
		String qr = qrDynamique.construire(site.getId(), 2, QrDynamiqueService.fenetre(Instant.now()));

		PointageResponse resp = pointageService.pointer("collab-1", requeteQr(qr));

		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
	}

	@Test
	void borneEcran_photoDeDeuxMinutes_expire() {
		siteEnModeEcran(2);
		String qr = qrDynamique.construire(site.getId(), 2, QrDynamiqueService.fenetre(Instant.now().minusSeconds(120)));

		PointageResponse resp = pointageService.pointer("collab-1", requeteQr(qr));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_EXPIRE);
	}

	@Test
	void borneRemplacee_qrDeLAncienneBorne_revoque() {
		siteEnModeEcran(3);
		String qr = qrDynamique.construire(site.getId(), 2, QrDynamiqueService.fenetre(Instant.now()));

		PointageResponse resp = pointageService.pointer("collab-1", requeteQr(qr));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_REVOQUE);
	}

	@Test
	void qrPapier_surSiteEnModeEcran_refuse() {
		when(qrRepository.findByJti(credential.getJti())).thenReturn(Optional.of(credential));
		siteEnModeEcran(1);

		PointageResponse resp = pointageService.pointer("collab-1", requeteQr(validToken));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_INVALIDE);
		assertThat(resp.motifRejet()).contains("borne écran");
	}

	@Test
	void qrDynamique_surSitePapier_refuse() {
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		String qr = qrDynamique.construire(site.getId(), 0, QrDynamiqueService.fenetre(Instant.now()));

		PointageResponse resp = pointageService.pointer("collab-1", requeteQr(qr));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_REVOQUE);
	}

	// --- D-P08 / D-P10 : intégration de l'étape visage (la table de décision est testée à part) ---

	@Test
	void blocageFacial_409_aucunPointagePersiste() {
		Instant fin = now.plusSeconds(600);
		org.mockito.Mockito.doThrow(new com.hr.presence.web.PointageBloqueException("bloqué", fin))
				.when(verificationFaciale).controlerBlocage(any(), any());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> pointageService.pointer("collab-1", requete(36.8065, 5.0, false)))
				.isInstanceOf(com.hr.presence.web.PointageBloqueException.class)
				.satisfies(e -> assertThat(((com.hr.presence.web.PointageBloqueException) e).getBloqueJusqua()).isEqualTo(fin));
		org.mockito.Mockito.verify(pointageRepository, org.mockito.Mockito.never()).save(any(PresencePointage.class));
		org.mockito.Mockito.verifyNoInteractions(qrRepository);
	}

	@Test
	void rejeuIdempotent_renvoieLaReponseDOrigine_memePendantLeBlocage() {
		PresencePointage existing = new PresencePointage();
		existing.setId(UUID.randomUUID());
		existing.setCollaborateurId("collab-1");
		existing.setStatut(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		existing.setType(com.hr.presence.domain.PointageType.ENTREE);
		existing.setServerTs(now);
		existing.setIdempotencyKey("idem-2");
		when(pointageRepository.findByCollaborateurIdAndIdempotencyKey("collab-1", "idem-2")).thenReturn(Optional.of(existing));

		CreatePointageRequest req = new CreatePointageRequest(
				validToken, "ENTREE", 36.8065, 10.1815, 10.0, now, "device-1", "idem-2", null);
		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.id()).isEqualTo(existing.getId());
		org.mockito.Mockito.verify(verificationFaciale, org.mockito.Mockito.never()).controlerBlocage(any(), any());
	}

	@Test
	void identiteIncoherente_avantLeRejeuIdempotent() {
		when(verificationFaciale.preparer(any(), any(), any()))
				.thenThrow(new com.hr.presence.web.PresenceMetierException("IDENTITE_INCOHERENTE", "x", 403));
		CreatePointageRequest req = new CreatePointageRequest(
				validToken, "ENTREE", 36.8065, 10.1815, 10.0, now, "device-1", "idem-3", null);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> pointageService.pointer("collab-1", req))
				.isInstanceOf(com.hr.presence.web.PresenceMetierException.class);
		org.mockito.Mockito.verifyNoInteractions(pointageRepository);
	}

	@Test
	void rejetQr_neOuvrePasLEtapeVisage() {
		CreatePointageRequest req = new CreatePointageRequest(
				"p1.abc.def", "ENTREE", 36.8065, 10.1815, 10.0, now, "device-1", null, null);

		PointageResponse resp = pointageService.pointer("collab-1", req);

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_QR_INVALIDE);
		org.mockito.Mockito.verify(verificationFaciale, org.mockito.Mockito.never()).decider(any(), any(), any());
	}

	@Test
	void rejetGps_neOuvrePasLEtapeVisage() {
		site.setRayonMetres(20);
		qrEtSiteValides();
		double lat25m = 36.8065 + (25.0 / 111_320.0);

		PointageResponse resp = pointageService.pointer("collab-1", requete(lat25m, 8.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_HORS_ZONE);
		org.mockito.Mockito.verify(verificationFaciale, org.mockito.Mockito.never()).decider(any(), any(), any());
	}

	@Test
	void rejetVisage_persisteEtRenvoieLeCompteur() {
		qrEtSiteValides();
		Instant fin = now.plusSeconds(900);
		when(verificationFaciale.decider(any(), any(), any())).thenReturn(new VerificationFacialePointageService.Decision(
				PointageStatut.REJETE_VISAGE_NON_RECONNU, "Visage non reconnu", null, (short) 60, Boolean.TRUE, "mfn-1",
				com.hr.presence.domain.PlateformeMobile.ANDROID, 3, fin));

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 5.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		assertThat(resp.scoreFacial()).isEqualTo(60);
		assertThat(resp.echecsConsecutifs()).isEqualTo(3);
		assertThat(resp.bloqueJusqua()).isEqualTo(fin);
		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getStatut()).isEqualTo(PointageStatut.REJETE_VISAGE_NON_RECONNU);
		assertThat(captor.getValue().getDistanceMetres()).isNotNull();
	}

	@Test
	void pointageVerifie_indicateurEtScoreStockes() {
		qrEtSiteValides();
		when(verificationFaciale.decider(any(), any(), any())).thenReturn(new VerificationFacialePointageService.Decision(
				PointageStatut.VALIDE, null, com.hr.presence.domain.VerificationFaciale.VERIFIEE, (short) 97, Boolean.TRUE,
				"mfn-1", com.hr.presence.domain.PlateformeMobile.IOS, 0, null));

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 5.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(resp.verificationFaciale()).isEqualTo(com.hr.presence.domain.VerificationFaciale.VERIFIEE);
		assertThat(resp.scoreFacial()).isEqualTo(97);
		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getScoreFacial()).isEqualTo((short) 97);
		assertThat(captor.getValue().getPlateforme()).isEqualTo(com.hr.presence.domain.PlateformeMobile.IOS);
	}

	@Test
	void reglageInactif_reponseSansChampFacial() {
		qrEtSiteValides();

		PointageResponse resp = pointageService.pointer("collab-1", requete(36.8065, 5.0, false));

		assertThat(resp.statut()).isEqualTo(PointageStatut.VALIDE);
		assertThat(resp.verificationFaciale()).isNull();
		assertThat(resp.scoreFacial()).isNull();
		assertThat(resp.echecsConsecutifs()).isNull();
		assertThat(resp.bloqueJusqua()).isNull();
	}
}
