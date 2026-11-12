package com.hr.evaluation.service;

import com.hr.evaluation.client.ReferentielParametresClient;
import com.hr.evaluation.client.ReferentielParametresClient.Reference;
import com.hr.evaluation.client.ReferentielParametresClient.Resultat;
import com.hr.evaluation.dto.CreateQuestionRequest;
import com.hr.evaluation.dto.CreateTemplateRequest;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR lot 3 § 6 : ciblage famille / niveau validé et marqué par le référentiel (client mocké) ; types de question. */
class TemplateServiceCiblageTest {

    private final EvaluationTemplateRepository templateRepository = mock(EvaluationTemplateRepository.class);
    private final EvaluationQuestionRepository questionRepository = mock(EvaluationQuestionRepository.class);
    private final ReferentielParametresClient client = mock(ReferentielParametresClient.class);
    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateRepository, questionRepository, client, ParametresTestes.parDefaut());
        when(templateRepository.save(any(EvaluationTemplate.class))).thenAnswer(i -> {
            EvaluationTemplate t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    private static CreateTemplateRequest technique(String famille, String niveau) {
        CreateTemplateRequest r = new CreateTemplateRequest();
        r.setNom("Compétences");
        r.setType("TECHNICAL");
        r.setFamilleMetierCode(famille);
        r.setNiveauSeniorite(niveau);
        return r;
    }

    @Test
    void ciblageValide_appelleLeReferentielAvecLaFamilleEtLeNiveau() {
        when(client.utiliser(anyList())).thenReturn(new Resultat(List.of("DEV", "SENIOR"), List.of(), List.of()));

        service.creerTemplate(technique("DEV", "SENIOR"), UUID.randomUUID());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reference>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).utiliser(captor.capture());
        assertThat(captor.getValue()).containsExactly(new Reference("FAMILLE_METIER", "DEV"),
                new Reference("NIVEAU_SENIORITE", "SENIOR"));
    }

    @Test
    void niveauInconnu_400_etRienNEstEnregistre() {
        when(client.utiliser(anyList())).thenReturn(new Resultat(List.of("DEV"), List.of(), List.of("STAGIAIRE")));

        assertThatThrownBy(() -> service.creerTemplate(technique("DEV", "STAGIAIRE"), UUID.randomUUID()))
                .isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("NIVEAU_SENIORITE_INVALIDE");
                });
        verify(templateRepository, never()).save(any());
    }

    @Test
    void niveauInactif_422() {
        when(client.utiliser(anyList())).thenReturn(new Resultat(List.of("DEV"), List.of("SENIOR"), List.of()));

        assertThatThrownBy(() -> service.creerTemplate(technique("DEV", "SENIOR"), UUID.randomUUID()))
                .isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("VALEUR_INACTIVE");
                });
    }

    @Test
    void referentielMuet_503_seulementPourUnTemplateTechnique() {
        when(client.utiliser(anyList())).thenThrow(
                EvaluationMetierException.unavailable("REFERENTIEL_INDISPONIBLE", "indisponible"));

        assertThatThrownBy(() -> service.creerTemplate(technique("DEV", "SENIOR"), UUID.randomUUID()))
                .isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(503));

        CreateTemplateRequest general = new CreateTemplateRequest();
        general.setNom("Général");
        general.setType("GENERIC");
        service.creerTemplate(general, UUID.randomUUID());
        verify(client, org.mockito.Mockito.times(1)).utiliser(anyList());
    }

    @Test
    void typeDeQuestionDesactive_422_ajoutRefuse() {
        var banc = ParametresTestes.avec(ParametresInstantane.de(ParametresTestes.initiales().stream()
                .map(v -> v.referentiel().equals("TYPE_QUESTION") && v.code().equals("DATE")
                        ? new ParametresInstantane.Valeur(v.referentiel(), v.code(), v.nature(), v.libelle(),
                                v.description(), v.couleur(), v.ordre(), false, v.systeme(), null)
                        : v).toList(), null));
        service = new TemplateService(templateRepository, questionRepository, client, banc.parametres());
        EvaluationTemplate brouillon = new EvaluationTemplate();
        UUID id = UUID.randomUUID();
        brouillon.setId(id);
        when(templateRepository.findById(id)).thenReturn(Optional.of(brouillon));

        CreateQuestionRequest question = new CreateQuestionRequest();
        question.setLibelle("Date de début");
        question.setTypeQuestion("DATE");

        assertThatThrownBy(() -> service.ajouterQuestion(id, question))
                .isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("TYPE_QUESTION_INACTIF");
                });
        verify(questionRepository, never()).save(any());
    }
}
