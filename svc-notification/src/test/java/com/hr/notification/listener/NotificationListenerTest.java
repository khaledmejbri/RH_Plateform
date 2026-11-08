package com.hr.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.notification.dto.NotificationDto;
import com.hr.notification.service.AnnuaireComptesClient;
import com.hr.notification.service.EmailService;
import com.hr.notification.service.NotificationStockageService;
import com.hr.notification.service.WebSocketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private EmailService email;
    @Mock
    private WebSocketNotificationService webSocket;
    @Mock
    private NotificationStockageService stockage;
    @Mock
    private AnnuaireComptesClient annuaire;

    private NotificationListener listener;

    @BeforeEach
    void init() {
        listener = new NotificationListener(email, webSocket, stockage, annuaire, new ObjectMapper());
    }

    private static NotificationDto dto() {
        return new NotificationDto(UUID.randomUUID(), "s", "c", null, null, null, null, null, null, false,
                "WEBSOCKET", "x", "s", "c");
    }

    private static String json(String type, String recipient) {
        return "{\"type\":\"" + type + "\",\"recipient\":\"" + recipient + "\",\"subject\":\"S\",\"content\":\"C\","
                + "\"notification_id\":\"" + UUID.randomUUID() + "\",\"champ_inconnu\":1}";
    }

    @Test
    void conserveAvantDePousserSurLeCompte() {
        UUID compte = UUID.randomUUID();
        NotificationDto dto = dto();
        when(stockage.enregistrer(any(), eq(compte))).thenReturn(Optional.of(dto));

        listener.handleNotificationMessage(json("WEBSOCKET", compte.toString()));

        InOrder ordre = inOrder(stockage, webSocket);
        ordre.verify(stockage).enregistrer(any(), eq(compte));
        ordre.verify(webSocket).sendToUser(compte.toString(), dto);
    }

    @Test
    void messageRejoueNeRenvoieRien() {
        UUID compte = UUID.randomUUID();
        when(stockage.enregistrer(any(), eq(compte))).thenReturn(Optional.empty());

        listener.handleNotificationMessage(json("BOTH", compte.toString()));

        verify(webSocket, never()).sendToUser(anyString(), any());
    }

    @Test
    void destinataireRhUneLigneEtUnEnvoiParCompteRh() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(annuaire.comptesRh()).thenReturn(List.of(a, b));
        when(stockage.enregistrer(any(), any())).thenReturn(Optional.of(dto()));

        listener.handleNotificationMessage(json("WEBSOCKET", "RH"));

        verify(stockage).enregistrer(any(), eq(a));
        verify(stockage).enregistrer(any(), eq(b));
        verify(webSocket).sendToUser(eq(a.toString()), any());
        verify(webSocket).sendToUser(eq(b.toString()), any());
    }

    @Test
    void emailSeulNEstPasConserve() {
        listener.handleNotificationMessage(json("EMAIL", "x@rh-evenement.test"));

        verify(email).sendTextEmail(anyString(), anyString(), anyString());
        verifyNoInteractions(stockage, webSocket);
    }

    @Test
    void destinataireQuiNEstPasUnCompteNEstPasConserve() {
        listener.handleNotificationMessage(json("WEBSOCKET", "nom.utilisateur"));

        verifyNoInteractions(stockage, webSocket);
    }

    @Test
    void jsonIllisibleEstIgnore() {
        listener.handleNotificationMessage("{pas du json");

        verifyNoInteractions(stockage, webSocket, annuaire);
    }
}
