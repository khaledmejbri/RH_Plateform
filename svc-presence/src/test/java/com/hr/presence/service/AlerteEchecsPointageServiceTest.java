package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlerteEchecsPointageServiceTest {

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
	/** 14:32 à Tunis (UTC+1) le 10/11/2026. */
	private static final Instant MAINTENANT = Instant.parse("2026-11-10T13:32:00Z");

	@Mock
	private PresencePointageRepository pointageRepository;
	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceEventPublisher eventPublisher;

	private AlerteEchecsPointageService service;
	private final UUID ficheId = UUID.randomUUID();
	private final UUID responsableCompte = UUID.randomUUID();
	private PresenceSite site;

	@BeforeEach
	void setUp() {
		service = new AlerteEchecsPointageService(pointageRepository, siteRepository, referentielClient,
				eventPublisher, new PresenceProperties(), Clock.fixed(MAINTENANT, TUNIS));
		site = new PresenceSite();
		site.setLibelle("Agence Lac");
		site.setRayonMetres(50);
	}

	private PresencePointage refus(PointageStatut statut) {
		PresencePointage p = new PresencePointage();
		p.setCollaborateurId(ficheId.toString());
		p.setStatut(statut);
		p.setServerTs(MAINTENANT);
		p.setSiteId(UUID.randomUUID());
		p.setDistanceMetres(79.9);
		p.setMotifRejet("Hors zone de pointage (79.9 m > 50 m)");
		return p;
	}

	private void compte(long total) {
		when(pointageRepository.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
				anyString(), anyCollection(), any(), any())).thenReturn(total);
	}

	private void responsable(UUID compte) {
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				ficheId, UUID.randomUUID(), "Sarra Khemiri", UUID.randomUUID(), compte, "Omar Belhadj"));
		when(siteRepository.findById(any())).thenReturn(Optional.of(site));
	}

	@Test
	void seuilAtteintNotifieLeResponsableEtLesRh() {
		compte(5);
		responsable(responsableCompte);
		service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE));

		ArgumentCaptor<ObjetNotification> objet = ArgumentCaptor.forClass(ObjetNotification.class);
		ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
		verify(eventPublisher).notifier(eq(responsableCompte.toString()),
				eq("Pointages refusés à répétition — Sarra Khemiri"), corps.capture(), objet.capture());
		ArgumentCaptor<ObjetNotification> objetRh = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH),
				eq("Pointages refusés à répétition — Sarra Khemiri"), eq(corps.getValue()), objetRh.capture());
		assertThat(corps.getValue()).isEqualTo("Sarra Khemiri a eu 5 pointages refusés aujourd'hui. Dernier refus à 14:32 "
				+ "sur le site « Agence Lac » : hors zone de pointage (79,9 m pour une zone de 50 m). "
				+ "Le pointage manuel reste possible.");
		assertThat(objet.getValue().type()).isEqualTo(ObjetNotification.COLLABORATEUR);
		assertThat(objet.getValue().id()).isEqualTo(ficheId);
		assertThat(objetRh.getValue().notificationId()).isEqualTo(objet.getValue().notificationId());
	}

	@Test
	void quatreEchecsNeNotifientPas() {
		compte(4);
		service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE));
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void sixiemeEchecNeNotifiePasDeNouveau() {
		compte(6);
		service.apresRefus(refus(PointageStatut.REJETE_PRECISION_GPS));
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void statutsNonComptesNeDeclenchentRien() {
		for (PointageStatut s : new PointageStatut[] {PointageStatut.REJETE_HORS_HORAIRE, PointageStatut.REJETE_QR_INVALIDE,
				PointageStatut.REJETE_QR_EXPIRE, PointageStatut.REJETE_SITE_INACTIF,
				PointageStatut.REJETE_EMPLACEMENT_ABSENT, PointageStatut.REJETE_VISAGE_NON_RECONNU,
				PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE, PointageStatut.VALIDE}) {
			service.apresRefus(refus(s));
		}
		verifyNoInteractions(pointageRepository, eventPublisher, referentielClient);
	}

	@Test
	void melangeDesTroisStatutsComptesDeclencheLAlerte() {
		compte(5);
		responsable(responsableCompte);
		service.apresRefus(refus(PointageStatut.REJETE_POSITION_SIMULEE));
		service.apresRefus(refus(PointageStatut.REJETE_PRECISION_GPS));
		verify(eventPublisher, org.mockito.Mockito.times(2)).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH),
				anyString(), anyString(), any(ObjetNotification.class));
		// le décompte porte bien sur les trois statuts
		ArgumentCaptor<java.util.Collection<PointageStatut>> statuts = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(pointageRepository, org.mockito.Mockito.atLeastOnce())
				.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
						anyString(), statuts.capture(), any(), any());
		assertThat(statuts.getValue()).containsExactlyInAnyOrder(PointageStatut.REJETE_HORS_ZONE,
				PointageStatut.REJETE_PRECISION_GPS, PointageStatut.REJETE_POSITION_SIMULEE);
	}

	@Test
	void leDecompteSeLimiteALaJourneeCalendaireDeTunis() {
		compte(1);
		service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE));
		ArgumentCaptor<Instant> debut = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> fin = ArgumentCaptor.forClass(Instant.class);
		verify(pointageRepository).countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
				anyString(), anyCollection(), debut.capture(), fin.capture());
		// minuit à minuit heure de Tunis : les échecs de la veille (avant 23:00Z le 09) ne sont pas comptés
		assertThat(debut.getValue()).isEqualTo(Instant.parse("2026-11-09T23:00:00Z"));
		assertThat(fin.getValue()).isEqualTo(Instant.parse("2026-11-10T23:00:00Z"));
	}

	@Test
	void sansResponsableSeulsLesRhSontNotifies() {
		compte(5);
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				ficheId, UUID.randomUUID(), "Sarra Khemiri", null, null, null));
		when(siteRepository.findById(any())).thenReturn(Optional.of(site));
		service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE));
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString(),
				any(ObjetNotification.class));
		verify(eventPublisher, never()).notifier(org.mockito.ArgumentMatchers.argThat(d -> !"RH".equals(d)),
				anyString(), anyString(), any(ObjetNotification.class));
	}

	@Test
	void referentielEnErreurAlerteLesRhSeulsSansException() {
		compte(5);
		when(referentielClient.monValideurPointage()).thenThrow(new IllegalStateException("référentiel indisponible"));
		when(siteRepository.findById(any())).thenReturn(Optional.of(site));
		assertThatCode(() -> service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE))).doesNotThrowAnyException();
		ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), sujet.capture(), anyString(),
				any(ObjetNotification.class));
		assertThat(sujet.getValue()).isEqualTo("Pointages refusés à répétition — Un collaborateur");
		verify(eventPublisher, org.mockito.Mockito.times(1)).notifier(anyString(), anyString(), anyString(),
				any(ObjetNotification.class));
	}

	@Test
	void kafkaEnErreurNeFaitPasEchouerLePointage() {
		compte(5);
		responsable(responsableCompte);
		doThrow(new IllegalStateException("kafka")).when(eventPublisher)
				.notifier(anyString(), anyString(), anyString(), any(ObjetNotification.class));
		assertThatCode(() -> service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE))).doesNotThrowAnyException();
	}

	@Test
	void erreurDeDecompteNeFaitPasEchouerLePointage() {
		when(pointageRepository.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
				anyString(), anyCollection(), any(), any())).thenThrow(new IllegalStateException("base"));
		assertThatCode(() -> service.apresRefus(refus(PointageStatut.REJETE_HORS_ZONE))).doesNotThrowAnyException();
		verifyNoInteractions(eventPublisher);
	}
}
