package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.dto.FacialAdminDtos.DeblocageRequest;
import com.hr.presence.dto.FacialAdminDtos.EnrolementDetail;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.AuditFacialRepository;
import com.hr.presence.repository.EnrolementFacialRepository;
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
import java.time.ZoneId;
import java.util.List;
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

/** D-P07.e : déblocage RH (motif, auto-déblocage interdit, ancien appareil invalidé, notifications, audit). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrolementFacialAdminServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");

	@Mock
	private EnrolementFacialRepository repository;
	@Mock
	private AuditFacialRepository auditRepository;
	@Mock
	private FacialAuditService auditService;
	@Mock
	private FacialIdentiteService identiteService;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceEventPublisher eventPublisher;

	private EnrolementFacialAdminService service;
	private PresenceEnrolementFacial ligne;
	private final String acteur = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		service = new EnrolementFacialAdminService(repository, auditRepository, auditService, identiteService,
				referentielClient, eventPublisher, Clock.fixed(MAINTENANT, ZoneId.of("Africa/Tunis")));
		when(identiteService.compteObligatoire()).thenReturn(acteur);
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.fromString(acteur), "Amel Trabelsi", null, null, null));

		ligne = new PresenceEnrolementFacial();
		ligne.setId(UUID.randomUUID());
		ligne.setCompteId(UUID.randomUUID().toString());
		ligne.setCollaborateurId(UUID.randomUUID().toString());
		ligne.setCollaborateurNom("Sarra Khemiri");
		ligne.setValideurCompteId("compte-responsable");
		ligne.setValideurId("fiche-responsable");
		ligne.setStatut(StatutEnrolement.A_REENROLER);
		ligne.setDeviceId("ancien-appareil-123456");
		ligne.setDeviceIdDemande("nouvel-appareil-abcdef");
		ligne.setEchecsConsecutifs(2);
		when(repository.findByIdForUpdate(ligne.getId())).thenReturn(Optional.of(ligne));
		when(repository.findById(ligne.getId())).thenReturn(Optional.of(ligne));
		when(auditRepository.findByEnrolementIdOrderByHorodatageDesc(any())).thenReturn(List.of());
	}

	private void assertErreur(String motif, String code, int statut) {
		assertThatThrownBy(() -> service.debloquer(ligne.getId(), new DeblocageRequest(motif)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo(code);
					assertThat(e.getHttpStatus()).isEqualTo(statut);
				});
		verify(repository, never()).save(any());
	}

	@Test
	void deblocage_aReenroler_devientNonEnrole_appareilsEtCompteurRemisAZero() {
		EnrolementDetail d = service.debloquer(ligne.getId(), new DeblocageRequest("Nouveau téléphone confirmé"));

		assertThat(d.statut()).isEqualTo(StatutEnrolement.NON_ENROLE);
		assertThat(ligne.getDeviceId()).isNull();
		assertThat(ligne.getDeviceIdDemande()).isNull();
		assertThat(ligne.getEchecsConsecutifs()).isZero();
		assertThat(ligne.getBloqueJusqua()).isNull();
	}

	@Test
	void deblocage_audite_avecMotifActeurEtStatuts() {
		service.debloquer(ligne.getId(), new DeblocageRequest("  Nouveau téléphone confirmé "));

		verify(auditService).tracer(eq(ActionAuditFacial.DEBLOCAGE_RH), eq(ligne.getId()), eq(StatutEnrolement.A_REENROLER),
				eq(StatutEnrolement.NON_ENROLE), eq("ancien-appareil-123456"), any(), eq("Nouveau téléphone confirmé"),
				eq("Amel Trabelsi"));
	}

	@Test
	void deblocage_notifieLeCollaborateurEtLeResponsable_pasLesAutres() {
		service.debloquer(ligne.getId(), new DeblocageRequest("Nouveau téléphone confirmé"));

		verify(eventPublisher).notifier(eq(ligne.getCompteId()), anyString(), anyString());
		verify(eventPublisher).notifier(eq("compte-responsable"), anyString(), anyString());
		verify(eventPublisher, org.mockito.Mockito.times(2)).notifier(anyString(), anyString(), anyString());
	}

	@Test
	void autoDeblocage_memeCompte_403() {
		ligne.setCompteId(acteur);
		assertErreur("Je me débloque moi-même", "AUTO_DEBLOCAGE_INTERDIT", 403);
	}

	@Test
	void autoDeblocage_memeFiche_403() {
		ligne.setCollaborateurId(UUID.randomUUID().toString());
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.fromString(ligne.getCollaborateurId()), UUID.randomUUID(), "Amel Trabelsi", null, null, null));
		assertErreur("Je me débloque moi-même", "AUTO_DEBLOCAGE_INTERDIT", 403);
	}

	@Test
	void unAdminSansFiche_peutDebloquer_unAutreRh() {
		when(referentielClient.monValideurPointage()).thenThrow(new SecurityException("pas de fiche"));

		assertThat(service.debloquer(ligne.getId(), new DeblocageRequest("Demande validée au téléphone")).statut())
				.isEqualTo(StatutEnrolement.NON_ENROLE);
	}

	@Test
	void deblocage_dejaEnrole_409() {
		ligne.setStatut(StatutEnrolement.ENROLE);
		assertErreur("Motif suffisant", "DEJA_ENROLE", 409);
	}

	@Test
	void deblocage_rienADebloquer_409() {
		ligne.setStatut(StatutEnrolement.REVOQUE);
		assertErreur("Motif suffisant", "RIEN_A_DEBLOQUER", 409);
		ligne.setStatut(StatutEnrolement.NON_ENROLE);
		assertErreur("Motif suffisant", "RIEN_A_DEBLOQUER", 409);
		ligne.setStatut(StatutEnrolement.REFUSE);
		assertErreur("Motif suffisant", "RIEN_A_DEBLOQUER", 409);
	}

	@Test
	void deblocage_enrolementIntrouvable_404() {
		UUID inconnu = UUID.randomUUID();
		when(repository.findByIdForUpdate(inconnu)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.debloquer(inconnu, new DeblocageRequest("Motif suffisant")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("ENROLEMENT_INTROUVABLE");
					assertThat(e.getHttpStatus()).isEqualTo(404);
				});
	}

	@Test
	void detail_masqueLesIdentifiantsAppareil_aucuneDonneeBiometrique() {
		EnrolementDetail d = service.detail(ligne.getId());

		assertThat(d.deviceIdMasque()).isEqualTo("123456");
		assertThat(d.deviceIdDemandeMasque()).isEqualTo("abcdef");
		assertThat(d.historique()).isEmpty();
	}

	@Test
	void detail_introuvable_404() {
		UUID inconnu = UUID.randomUUID();
		when(repository.findById(inconnu)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(inconnu)).isInstanceOfSatisfying(PresenceMetierException.class,
				e -> assertThat(e.getCode()).isEqualTo("ENROLEMENT_INTROUVABLE"));
	}
}
