package com.hr.evaluation.kafka;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;
import com.hr.evaluation.entity.EvaluationRh;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lot 1 UAT, ticket 1B (D-L01.d) : messages d'alerte de couleur accentués. */
@ExtendWith(MockitoExtension.class)
class EvaluationEventPublisherAlerteTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private EvaluationRh evaluation(CouleurAlerteEvaluationRh couleur) {
        EvaluationRh e = new EvaluationRh();
        e.setId(UUID.randomUUID());
        e.setCollaborateurIdentifiant(UUID.randomUUID());
        e.setType(TypeEvaluationRh.SEMESTRIELLE);
        e.setAnnee(2026);
        e.setCouleurAlerte(couleur);
        return e;
    }

    private String messagePublie(CouleurAlerteEvaluationRh couleur) {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        EvaluationRh e = evaluation(couleur);
        new EvaluationEventPublisher(kafkaTemplateProvider).publierAlerteSiNecessaire(e);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(RhEvaluationTopics.EVALUATION_ALERTE), eq(e.getId().toString()), payload.capture());
        return JSON.readTree(payload.getValue()).get("message").asText();
    }

    @Test
    void alerteOrangeEstAccentuee() {
        assertThat(messagePublie(CouleurAlerteEvaluationRh.ORANGE))
                .isEqualTo("Alerte RH : deux critères sont sous le seuil.");
    }

    @Test
    void alerteRougeEstLisible() {
        assertThat(messagePublie(CouleurAlerteEvaluationRh.ROUGE)).isEqualTo("Plan d'action obligatoire et escalade DG.");
    }

    @Test
    void alerteVertePublieRien() {
        new EvaluationEventPublisher(kafkaTemplateProvider).publierAlerteSiNecessaire(evaluation(CouleurAlerteEvaluationRh.VERT));
        verifyNoInteractions(kafkaTemplate);
    }
}
