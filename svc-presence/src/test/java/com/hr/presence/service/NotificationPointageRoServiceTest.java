package com.hr.presence.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.presence.client.PresenceWorkflowMoteurClient;
import com.hr.presence.client.PresenceWorkflowMoteurClient.RegleActivePointage;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.domain.PointageType;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPointageRoServiceTest {

	@Mock private PresenceWorkflowMoteurClient workflowClient;
	@Mock private ReferentielClient referentielClient;
	@Mock private PresenceEventPublisher eventPublisher;

	private ExecutorService virtualExecutor;
	private NotificationPointageRoService service;

	private UUID pointageId;
	private UUID ficheId;
	private UUID compteCollabId;
	private UUID roFicheId;
	private UUID roCompteId;
	private UUID site1Id;
	private UUID site2Id;
	private Instant ts;

	@BeforeEach
	void setUp() {
		virtualExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("test-vt-", 0).factory());
		service = new NotificationPointageRoService(workflowClient, referentielClient, eventPublisher, virtualExecutor);
		pointageId = UUID.randomUUID();
		ficheId = UUID.randomUUID();
		compteCollabId = UUID.randomUUID();
		roFicheId = UUID.randomUUID();
		roCompteId = UUID.randomUUID();
		site1Id = UUID.randomUUID();
		site2Id = UUID.randomUUID();
		// 2026-11-24T08:15:12Z = 09:15 Africa/Tunis (UTC+1)
		ts = Instant.parse("2026-11-24T08:15:12Z");

		lenient().when(referentielClient.resoudreCollaborateurCourt(ficheId)).thenReturn(Optional.of(
				new CollaborateurResolu(ficheId, compteCollabId, "COL-WEB", "Sarra Khemiri", true,
						null, null, roFicheId, roCompteId)));
	}

	@AfterEach
	void tearDown() {
		virtualExecutor.close();
		SocieteContexte.effacer();
	}

	private PointageValideCreeEvent event(PointageType type, UUID siteId, UUID societeId) {
		return new PointageValideCreeEvent(pointageId, ficheId.toString(), type, ts, siteId, "Siège Social", societeId);
	}

	@Test
	void caseCochee_touteLaSociete_envoieNotificationExacteAvecIdDeterministe() {
		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
				"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET")));

		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null));

		ArgumentCaptor<ObjetNotification> objetCaptor = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(eventPublisher).notifierAvecCanal(
				eq("WEBSOCKET"),
				eq(roCompteId.toString()),
				eq("Pointage — Sarra Khemiri"),
				eq("Sarra Khemiri a pointé son entrée à 09:15 sur le site « Siège Social »."),
				objetCaptor.capture());

		ObjetNotification obj = objetCaptor.getValue();
		assertThat(obj.evenement()).isEqualTo("POINTAGE_EQUIPE_EFFECTUE");
		assertThat(obj.type()).isEqualTo(ObjetNotification.COLLABORATEUR);
		assertThat(obj.id()).isEqualTo(ficheId);
		assertThat(obj.numero()).isEqualTo("COL-WEB");

		UUID attenduId = UUID.nameUUIDFromBytes(
				(pointageId + ":" + roCompteId + ":POINTAGE_EQUIPE_EFFECTUE").getBytes(StandardCharsets.UTF_8));
		assertThat(obj.notificationId()).isEqualTo(attenduId);
	}

	@Test
	void sitesSelectionnes_siteInclusEnvoie_siteExcluNeEnvoiePas() {
		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
				"SITES_SELECTIONNES", List.of(site1Id), List.of("ENTREE", "SORTIE"), "BOTH")));

		service.traiterAvecSociete(event(PointageType.SORTIE, site1Id, null));
		verify(eventPublisher).notifierAvecCanal(
				eq("BOTH"),
				eq(roCompteId.toString()),
				eq("Pointage — Sarra Khemiri"),
				eq("Sarra Khemiri a pointé sa sortie à 09:15 sur le site « Siège Social »."),
				any());

		org.mockito.Mockito.reset(eventPublisher);
		service.traiterAvecSociete(event(PointageType.ENTREE, site2Id, null));
		verify(eventPublisher, never()).notifierAvecCanal(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void caseDecochee_ouModeHerite_ouTypeNonCoche_aucuneNotification() {
		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				false, "MOTEUR", "WF_POINTAGE_RO", 2, false,
				"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET")));
		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null));

		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				false, "HERITE", null, null, false,
				"TOUTE_LA_SOCIETE", List.of(), List.of(), "WEBSOCKET")));
		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null));

		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
				"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE"), "WEBSOCKET")));
		service.traiterAvecSociete(event(PointageType.SORTIE, site1Id, null));

		verify(eventPublisher, never()).notifierAvecCanal(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void collaborateurSansRo_ouAutoRo_aucuneNotification() {
		when(workflowClient.regleActivePointage()).thenReturn(Optional.of(new RegleActivePointage(
				true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
				"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET")));

		when(referentielClient.resoudreCollaborateurCourt(ficheId)).thenReturn(Optional.of(
				new CollaborateurResolu(ficheId, compteCollabId, "DG01", "Directeur Général", true,
						null, null, null, null)));
		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null));

		when(referentielClient.resoudreCollaborateurCourt(ficheId)).thenReturn(Optional.of(
				new CollaborateurResolu(ficheId, compteCollabId, "CS-WEB", "Omar Belhadj", true,
						null, null, ficheId, compteCollabId)));
		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null));

		verify(eventPublisher, never()).notifierAvecCanal(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void panneWorkflowOuReferentiel_neLeveAucuneException() {
		when(workflowClient.regleActivePointage()).thenThrow(new RuntimeException("svc-workflow down"));
		assertThatCode(() -> service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null)))
				.doesNotThrowAnyException();

		org.mockito.Mockito.doReturn(Optional.of(new RegleActivePointage(
				true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
				"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE"), "WEBSOCKET")))
				.when(workflowClient).regleActivePointage();
		when(referentielClient.resoudreCollaborateurCourt(ficheId)).thenThrow(new RuntimeException("referentiel timeout"));
		assertThatCode(() -> service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, null)))
				.doesNotThrowAnyException();

		verify(eventPublisher, never()).notifierAvecCanal(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void propagationSocieteContexteVersVirtualThreads() {
		UUID societeId = UUID.randomUUID();
		AtomicReference<UUID> societeWf = new AtomicReference<>();
		AtomicReference<UUID> societeRef = new AtomicReference<>();

		when(workflowClient.regleActivePointage()).thenAnswer(inv -> {
			societeWf.set(SocieteContexte.couranteOuVide().orElse(null));
			return Optional.of(new RegleActivePointage(
					true, "MOTEUR", "WF_POINTAGE_RO", 1, true,
					"TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE"), "WEBSOCKET"));
		});
		when(referentielClient.resoudreCollaborateurCourt(ficheId)).thenAnswer(inv -> {
			societeRef.set(SocieteContexte.couranteOuVide().orElse(null));
			return Optional.of(new CollaborateurResolu(
					ficheId, compteCollabId, "COL-WEB", "Sarra Khemiri", true,
					null, null, roFicheId, roCompteId));
		});

		service.traiterAvecSociete(event(PointageType.ENTREE, site1Id, societeId));

		assertThat(societeWf.get()).isEqualTo(societeId);
		assertThat(societeRef.get()).isEqualTo(societeId);
		verify(eventPublisher).notifierAvecCanal(anyString(), eq(roCompteId.toString()), anyString(), anyString(), any());
	}
}
