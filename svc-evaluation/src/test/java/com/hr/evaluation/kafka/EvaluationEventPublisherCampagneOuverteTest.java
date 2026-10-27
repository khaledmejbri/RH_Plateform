package com.hr.evaluation.kafka;

import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationEventPublisherCampagneOuverteTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publieSurRhNotificationsAuCompteDuCollaborateur() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        EvaluationEventPublisher publisher = new EvaluationEventPublisher(kafkaTemplateProvider);

        UUID compte = UUID.randomUUID();
        UUID campagneId = UUID.randomUUID();
        UUID evalId = UUID.randomUUID();
        Evaluation evaluation = evaluation(evalId, campagneId);
        evaluation.setCollaborateurCompteId(compte);

        publisher.publierEvaluationCampagneOuverte(evaluation, "Annuelle 2026");

        NotificationMessage msg = capturer(compte.toString());
        assertThat(msg.type()).isEqualTo("WEBSOCKET");
        assertThat(msg.recipient()).isEqualTo(compte.toString());
        assertThat(msg.subject()).isEqualTo("Votre évaluation est ouverte");
        assertThat(msg.content()).startsWith("EVALUATION_CAMPAGNE_OUVERTE|campagne=" + campagneId);
        assertThat(msg.content()).contains("|evaluation=" + evalId);
    }

    @Test
    void sansCompteRepliSurLaFiche() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        EvaluationEventPublisher publisher = new EvaluationEventPublisher(kafkaTemplateProvider);
        Evaluation evaluation = evaluation(UUID.randomUUID(), UUID.randomUUID());

        publisher.publierEvaluationCampagneOuverte(evaluation, "Annuelle 2026");

        String fiche = evaluation.getCollaborateurIdentifiant().toString();
        assertThat(capturer(fiche).recipient()).isEqualTo(fiche);
    }

    @Test
    void validationCollaborateurNotifieLeCompteDuResponsable() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        EvaluationEventPublisher publisher = new EvaluationEventPublisher(kafkaTemplateProvider);
        UUID compteManager = UUID.randomUUID();
        Evaluation evaluation = evaluation(UUID.randomUUID(), UUID.randomUUID());
        evaluation.setSuperieurIdentifiant(UUID.randomUUID());
        evaluation.setSuperieurCompteId(compteManager);
        evaluation.setCollaborateurNom("Sarra Khemiri");

        publisher.publierValidationCollaborateur(evaluation);

        NotificationMessage msg = capturer(compteManager.toString());
        assertThat(msg.recipient()).isEqualTo(compteManager.toString());
        assertThat(msg.subject()).isEqualTo("Évaluation à noter — Sarra Khemiri");
        assertThat(msg.content()).startsWith("EVALUATION_A_NOTER|evaluation=" + evaluation.getId());
    }

    @Test
    void sansKafkaTemplateNeLevePasDException() {
        EvaluationEventPublisher publisher = new EvaluationEventPublisher(kafkaTemplateProvider);
        Evaluation evaluation = evaluation(UUID.randomUUID(), UUID.randomUUID());

        assertThatCode(() -> publisher.publierEvaluationCampagneOuverte(evaluation, "Annuelle 2026"))
                .doesNotThrowAnyException();
    }

    private NotificationMessage capturer(String cle) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(RhEvaluationTopics.RH_NOTIFICATIONS), eq(cle), payload.capture());
        return JSON.readValue(payload.getValue(), NotificationMessage.class);
    }

    private static Evaluation evaluation(UUID evalId, UUID campagneId) {
        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setId(campagneId);
        campaign.setNom("Annuelle 2026");
        Evaluation evaluation = new Evaluation();
        evaluation.setId(evalId);
        evaluation.setCollaborateurIdentifiant(UUID.randomUUID());
        evaluation.setCampaign(campaign);
        return evaluation;
    }
}
