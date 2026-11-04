package com.hr.evaluation.kafka;

import com.hr.evaluation.client.IdentiteClient;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.service.AlerteEcartANotifierEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-E07 : un message par compte RH, exclusions, repli ADMIN, jamais de destinataire littéral, pas d'exception. */
class AlerteEcartNotificationListenerTest {

    private final IdentiteClient identite = mock(IdentiteClient.class);
    private final EvaluationEventPublisher publisher = mock(EvaluationEventPublisher.class);
    private final AlerteEcartNotificationListener listener = new AlerteEcartNotificationListener(identite, publisher);

    private final UUID drh01 = UUID.randomUUID();
    private final UUID rh02 = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID collaborateur = UUID.randomUUID();
    private final UUID ro = UUID.randomUUID();
    private final UUID alerteId = UUID.randomUUID();

    private AlerteEcartANotifierEvent evenement(NiveauEcart niveau, int n, int ecartMax, Set<UUID> exclus) {
        return new AlerteEcartANotifierEvent(alerteId, niveau, "Sarra Khemiri", "Campagne S2", n, ecartMax, exclus);
    }

    @Test
    void ecouteApresLeCommitSeulement() throws Exception {
        var methode = AlerteEcartNotificationListener.class.getMethod("surAlerteANotifier", AlerteEcartANotifierEvent.class);
        TransactionalEventListener annotation = methode.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void unMessageParCompteRh_titreTexteEtIdentifiantDeLAlerte() {
        when(identite.comptesParRole("RH")).thenReturn(List.of(drh01, rh02));

        listener.surAlerteANotifier(evenement(NiveauEcart.ORANGE, 1, 2, Set.of(collaborateur, ro)));

        ArgumentCaptor<String> destinataire = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contenu = ArgumentCaptor.forClass(String.class);
        verify(publisher, times(2)).notifier(destinataire.capture(), sujet.capture(), contenu.capture());
        assertThat(destinataire.getAllValues()).containsExactlyInAnyOrder(drh01.toString(), rh02.toString())
                .doesNotContain("RH");
        assertThat(sujet.getValue()).isEqualTo("Alerte d'écart d'évaluation — Orange");
        assertThat(contenu.getValue()).isEqualTo("ALERTE_ECART_EVALUATION|alerte=" + alerteId + "|niveau=ORANGE"
                + "|Évaluation de Sarra Khemiri (Campagne S2) : 1 compétence(s) en écart, écart max 2 étoiles.");
        verify(identite, never()).comptesParRole("ADMIN");
    }

    @Test
    void libellesRougeEtUrgente() {
        when(identite.comptesParRole("RH")).thenReturn(List.of(drh01));

        listener.surAlerteANotifier(evenement(NiveauEcart.ROUGE, 2, 3, Set.of()));
        listener.surAlerteANotifier(evenement(NiveauEcart.URGENTE, 1, 4, Set.of()));

        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        verify(publisher, times(2)).notifier(anyString(), sujet.capture(), anyString());
        assertThat(sujet.getAllValues()).containsExactly(
                "Alerte d'écart d'évaluation — Rouge", "Alerte d'écart d'évaluation — Urgente");
    }

    @Test
    void collaborateurEtRoNeRecoiventRien_memeS_ilsSontRh() {
        when(identite.comptesParRole("RH")).thenReturn(List.of(drh01, rh02, collaborateur));

        listener.surAlerteANotifier(evenement(NiveauEcart.ROUGE, 1, 3, Set.of(collaborateur, drh01)));

        ArgumentCaptor<String> destinataire = ArgumentCaptor.forClass(String.class);
        verify(publisher).notifier(destinataire.capture(), anyString(), anyString());
        assertThat(destinataire.getValue()).isEqualTo(rh02.toString());
    }

    @Test
    void aucunRhRestant_repliSurLesAdmin() {
        when(identite.comptesParRole("RH")).thenReturn(List.of(collaborateur));
        when(identite.comptesParRole("ADMIN")).thenReturn(List.of(admin, ro));

        listener.surAlerteANotifier(evenement(NiveauEcart.ROUGE, 1, 3, Set.of(collaborateur, ro)));

        ArgumentCaptor<String> destinataire = ArgumentCaptor.forClass(String.class);
        verify(publisher).notifier(destinataire.capture(), anyString(), anyString());
        assertThat(destinataire.getValue()).isEqualTo(admin.toString());
    }

    @Test
    void niRhNiAdmin_aucunEnvoiEtAucuneException() {
        when(identite.comptesParRole("RH")).thenReturn(List.of());
        when(identite.comptesParRole("ADMIN")).thenReturn(List.of());

        listener.surAlerteANotifier(evenement(NiveauEcart.ORANGE, 1, 2, Set.of()));

        verify(publisher, never()).notifier(anyString(), anyString(), anyString());
    }

    @Test
    void panneIdentiteOuKafka_aucuneExceptionNePropage() {
        when(identite.comptesParRole("RH")).thenThrow(new IllegalStateException("identité en panne"));
        listener.surAlerteANotifier(evenement(NiveauEcart.ORANGE, 1, 2, Set.of()));
        verify(publisher, never()).notifier(anyString(), anyString(), anyString());

        org.mockito.Mockito.doReturn(List.of(drh01)).when(identite).comptesParRole("RH");
        org.mockito.Mockito.doThrow(new IllegalStateException("kafka")).when(publisher)
                .notifier(anyString(), anyString(), anyString());
        listener.surAlerteANotifier(evenement(NiveauEcart.ORANGE, 1, 2, Set.of()));
    }
}
