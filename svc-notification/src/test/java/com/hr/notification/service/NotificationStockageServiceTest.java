package com.hr.notification.service;

import com.hr.notification.dto.NotificationDto;
import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.entity.Notification;
import com.hr.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStockageServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-07T10:00:00Z");

    @Mock
    private NotificationRepository repository;

    private NotificationStockageService service;
    private final UUID compte = UUID.randomUUID();

    @BeforeEach
    void init() {
        service = new NotificationStockageService(repository, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "retentionJours", 180);
        ReflectionTestUtils.setField(service, "tailleLotPurge", 1000);
    }

    private NotificationMessage message(UUID evenement) {
        return NotificationMessage.builder().type("WEBSOCKET").recipient(compte.toString())
                .subject("Demande validée").content("Votre demande a été validée.")
                .notificationId(evenement.toString()).evenement("DEMANDE_VALIDEE")
                .objetType("DEMANDE").objetId(UUID.randomUUID().toString()).objetNumero("DEM-202611-0007").build();
    }


    @Test
    void enregistreUneLigneNonLuePourLeCompte() {
        UUID evenement = UUID.randomUUID();
        when(repository.existsByNotificationIdAndCompteId(evenement, compte)).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Optional<NotificationDto> dto = service.enregistrer(message(evenement), compte);

        ArgumentCaptor<Notification> c = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(c.capture());
        assertThat(c.getValue().getCompteId()).isEqualTo(compte);
        assertThat(c.getValue().getNotificationId()).isEqualTo(evenement);
        assertThat(c.getValue().getLuLe()).isNull();
        assertThat(c.getValue().getRecuLe()).isEqualTo(MAINTENANT);
        assertThat(dto).isPresent();
        assertThat(dto.get().lu()).isFalse();
        assertThat(dto.get().objetType()).isEqualTo("DEMANDE");
        assertThat(dto.get().objetNumero()).isEqualTo("DEM-202611-0007");
    }

    @Test
    void evenementRejoueNeCreePasDeDoublonNiDeRenvoi() {
        UUID evenement = UUID.randomUUID();
        when(repository.existsByNotificationIdAndCompteId(evenement, compte)).thenReturn(true);

        assertThat(service.enregistrer(message(evenement), compte)).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ancienFormatSansIdentifiantNiObjetEstConserve() {
        NotificationMessage ancien = NotificationMessage.builder().type("WEBSOCKET").recipient(compte.toString())
                .subject("Bienvenue").content("Texte").build();
        when(repository.existsByNotificationIdAndCompteId(any(), eq(compte))).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto dto = service.enregistrer(ancien, compte).orElseThrow();

        assertThat(dto.objetType()).isNull();
        assertThat(dto.objetId()).isNull();
        assertThat(dto.id()).isNotNull();
    }

    @Test
    void identifiantsMalFormesNeFontPasPerdreLeMessage() {
        NotificationMessage m = message(UUID.randomUUID());
        m.setObjetId("pas-un-uuid");
        m.setEmisLe("hier");
        when(repository.existsByNotificationIdAndCompteId(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto dto = service.enregistrer(m, compte).orElseThrow();

        assertThat(dto.objetId()).isNull();
        assertThat(dto.emisLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void listeToujoursBorneeAuCompteDeLAppelant() {
        when(repository.findByCompteIdAndLuLeIsNull(eq(compte), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = service.lister(compte, false, 0, 20);

        assertThat(page.contenu()).isEmpty();
        assertThat(page.taille()).isEqualTo(20);
        verify(repository).findByCompteIdAndLuLeIsNull(eq(compte), any(Pageable.class));
    }

    @Test
    void paginationInvalideDonne400() {
        assertThatThrownBy(() -> service.lister(compte, null, 0, 101)).isInstanceOf(ParametreInvalideException.class);
        assertThatThrownBy(() -> service.lister(compte, null, -1, 20)).isInstanceOf(ParametreInvalideException.class);
        assertThatThrownBy(() -> service.lister(compte, null, 0, 0)).isInstanceOf(ParametreInvalideException.class);
    }

    @Test
    void marquerLueEstIdempotenteEtGardeLaDateDeLecture() {
        Instant dejaLue = Instant.parse("2026-11-01T08:00:00Z");
        Notification n = Notification.builder().id(UUID.randomUUID()).compteId(compte).luLe(dejaLue).build();
        when(repository.findByIdAndCompteId(n.getId(), compte)).thenReturn(Optional.of(n));

        service.marquerLue(n.getId(), compte);

        assertThat(n.getLuLe()).isEqualTo(dejaLue);
        verify(repository, never()).save(any());
    }

    @Test
    void marquerLuePoseLaDateDeLecture() {
        Notification n = Notification.builder().id(UUID.randomUUID()).compteId(compte).build();
        when(repository.findByIdAndCompteId(n.getId(), compte)).thenReturn(Optional.of(n));

        service.marquerLue(n.getId(), compte);

        assertThat(n.getLuLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void notificationDUnAutreCompteOuInexistanteDonne404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompteId(id, compte)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marquerLue(id, compte)).isInstanceOf(NotificationIntrouvableException.class);
    }

    @Test
    void toutMarquerLuNeToucheQueLesNotificationsJusquaLInstantDeLaDemande() {
        when(repository.marquerToutesLues(compte, MAINTENANT, MAINTENANT)).thenReturn(3);

        assertThat(service.marquerToutesLues(compte)).isEqualTo(3);
        verify(repository).marquerToutesLues(compte, MAINTENANT, MAINTENANT);
    }

    @Test
    void compteurCompteLesNonLuesDuCompte() {
        when(repository.countByCompteIdAndLuLeIsNull(compte)).thenReturn(120L);

        assertThat(service.compter(compte).nonLues()).isEqualTo(120L);
    }

    @Test
    void purgeSelectionneAuDelaDe180JoursSeulement() {
        Instant limite = MAINTENANT.minus(Duration.ofDays(180));
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(repository.idsAnterieursA(eq(limite), any(Pageable.class))).thenReturn(ids);

        assertThat(service.purger()).isEqualTo(2);

        verify(repository).deleteAllByIdInBatch(ids);
        // 181 jours : avant la limite, donc supprimée ; 179 jours : après la limite, donc conservée
        assertThat(MAINTENANT.minus(Duration.ofDays(181))).isBefore(limite);
        assertThat(MAINTENANT.minus(Duration.ofDays(179))).isAfter(limite);
    }

    @Test
    void purgeParLotsJusquaEpuisement() {
        ReflectionTestUtils.setField(service, "tailleLotPurge", 2);
        Instant limite = MAINTENANT.minus(Duration.ofDays(180));
        List<UUID> lot1 = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> lot2 = List.of(UUID.randomUUID());
        when(repository.idsAnterieursA(eq(limite), any(Pageable.class))).thenReturn(lot1, lot2);

        assertThat(service.purger()).isEqualTo(3);
    }
}
