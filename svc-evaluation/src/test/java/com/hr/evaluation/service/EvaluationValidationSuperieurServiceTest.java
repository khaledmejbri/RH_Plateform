package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-E10 : validation de l'évaluation par le RO, précédence 404 > 403 > 409 > 422, idempotence, pas de score. */
class EvaluationValidationSuperieurServiceTest {

    private final EvaluationRepository evaluationRepository = mock(EvaluationRepository.class);
    private final EvaluationAnswerRepository answerRepository = mock(EvaluationAnswerRepository.class);
    private final TemplateTechniqueEvaluation templateTechnique = mock(TemplateTechniqueEvaluation.class);
    private final AlerteEcartService alerteEcartService = mock(AlerteEcartService.class);
    private EvaluationValidationSuperieurService service;

    private final UUID roCompte = UUID.randomUUID();
    private Evaluation evaluation;
    private final List<EvaluationQuestion> questions = new ArrayList<>();
    private final List<EvaluationAnswer> reponses = new ArrayList<>();

    private IdentiteAppelant ro() {
        return new IdentiteAppelant(roCompte, "cs-web", false, Optional::empty);
    }

    @BeforeEach
    void setUp() {
        service = new EvaluationValidationSuperieurService(evaluationRepository, answerRepository, templateTechnique,
                alerteEcartService);
        evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setCampaign(new EvaluationCampaign());
        evaluation.setCollaborateurIdentifiant(UUID.randomUUID());
        evaluation.setCollaborateurCompteId(UUID.randomUUID());
        evaluation.setSuperieurIdentifiant(UUID.randomUUID());
        evaluation.setSuperieurCompteId(roCompte);
        evaluation.setValidationCollaborateurLe(Instant.now());
        evaluation.setStatut(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
        evaluation.setScoreSur20(14);
        when(evaluationRepository.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(evaluationRepository.findByIdWithCampaign(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateTechnique.questions(evaluation)).thenReturn(questions);
        when(answerRepository.findWithQuestionByEvaluationId(evaluation.getId())).thenReturn(reponses);
    }

    private EvaluationQuestion question(String libelle, Integer noteRo) {
        EvaluationQuestion q = new EvaluationQuestion();
        q.setId(UUID.randomUUID());
        q.setLibelle(libelle);
        questions.add(q);
        if (noteRo != null) {
            EvaluationAnswer a = new EvaluationAnswer();
            a.setQuestion(q);
            a.setNoteCollaborateur(3);
            a.setNoteManager(noteRo);
            reponses.add(a);
        }
        return q;
    }

    private static void assertErreur(Throwable t, int statut, String code) {
        assertThat(t).isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(statut);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void roValide_statutValideeDatePoseeCompteDuJwtEtDetectionDansLaMemeTransaction() {
        question("Java", 2);

        Evaluation resultat = service.validerParSuperieur(evaluation.getId(), ro());

        assertThat(resultat.getStatut()).isEqualTo(StatutEvaluationRh.VALIDEE);
        assertThat(resultat.getValidationSuperieurLe()).isNotNull();
        assertThat(resultat.getValidationSuperieurPar()).isEqualTo(roCompte);
        verify(alerteEcartService).detecter(resultat);
    }

    @Test
    void validationNeModifiePasLeScoreSur20() {
        question("Java", 2);

        Evaluation resultat = service.validerParSuperieur(evaluation.getId(), ro());

        assertThat(resultat.getScoreSur20()).isEqualTo(14);
    }

    @Test
    void evaluationInexistante_404() {
        UUID inconnue = UUID.randomUUID();
        when(evaluationRepository.findByIdForUpdate(inconnue)).thenReturn(Optional.empty());
        when(evaluationRepository.findByIdWithCampaign(inconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validerParSuperieur(inconnue, ro()))
                .satisfies(t -> assertErreur(t, 404, "EVALUATION_INTROUVABLE"));
        assertThatThrownBy(() -> service.etatValidationSuperieur(inconnue, ro()))
                .satisfies(t -> assertErreur(t, 404, "EVALUATION_INTROUVABLE"));
    }

    @Test
    void pasLeRo_403_RhEtAdminCompris_rienNeChange() {
        IdentiteAppelant rhOuAdmin = new IdentiteAppelant(UUID.randomUUID(), "drh01", true, Optional::empty);
        IdentiteAppelant collaborateur = new IdentiteAppelant(evaluation.getCollaborateurCompteId(), "col", false,
                Optional::empty);

        for (IdentiteAppelant appelant : List.of(rhOuAdmin, collaborateur)) {
            assertThatThrownBy(() -> service.validerParSuperieur(evaluation.getId(), appelant))
                    .satisfies(t -> assertErreur(t, 403, "PAS_RO_EVALUATION"));
        }
        assertThat(evaluation.getValidationSuperieurLe()).isNull();
        verify(evaluationRepository, never()).save(any());
        verifyNoInteractions(alerteEcartService);
    }

    @Test
    void collaborateurNonValide_409() {
        evaluation.setValidationCollaborateurLe(null);

        assertThatThrownBy(() -> service.validerParSuperieur(evaluation.getId(), ro()))
                .satisfies(t -> assertErreur(t, 409, "COLLABORATEUR_NON_VALIDE"));
        verifyNoInteractions(alerteEcartService);
    }

    @Test
    void competenceSansNoteRo_422AvecLaListe() {
        question("Java", 2);
        EvaluationQuestion manquante = question("SQL", null);

        assertThatThrownBy(() -> service.validerParSuperieur(evaluation.getId(), ro())).satisfies(t -> {
            assertErreur(t, 422, "COMPETENCES_NON_NOTEES");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> liste =
                    (List<Map<String, Object>>) ((EvaluationMetierException) t).getDonnees().get("competencesManquantes");
            assertThat(liste).singleElement().satisfies(m -> {
                assertThat(m).containsEntry("libelle", "SQL");
                assertThat(m).containsEntry("questionId", manquante.getId().toString());
            });
        });
        assertThat(evaluation.getValidationSuperieurLe()).isNull();
        verifyNoInteractions(alerteEcartService);
    }

    @Test
    void collaborateurNonValideEtCompetenceManquante_409Prime() {
        evaluation.setValidationCollaborateurLe(null);
        question("SQL", null);

        assertThatThrownBy(() -> service.validerParSuperieur(evaluation.getId(), ro()))
                .satisfies(t -> assertErreur(t, 409, "COLLABORATEUR_NON_VALIDE"));
    }

    @Test
    void secondAppel_200SansEffet_dateConserveePasDeNouvelleDetection() {
        question("Java", 2);
        Evaluation premiere = service.validerParSuperieur(evaluation.getId(), ro());
        Instant date = premiere.getValidationSuperieurLe();
        org.mockito.Mockito.clearInvocations(alerteEcartService, evaluationRepository);
        when(evaluationRepository.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(evaluationRepository.findByIdWithCampaign(evaluation.getId())).thenReturn(Optional.of(evaluation));

        Evaluation seconde = service.validerParSuperieur(evaluation.getId(), ro());

        assertThat(seconde.getValidationSuperieurLe()).isEqualTo(date);
        verify(evaluationRepository, never()).save(any());
        verifyNoInteractions(alerteEcartService);
    }

    @Test
    void sansTemplateTechnique_validationPermise() {
        Evaluation resultat = service.validerParSuperieur(evaluation.getId(), ro());

        assertThat(resultat.getStatut()).isEqualTo(StatutEvaluationRh.VALIDEE);
        verify(alerteEcartService).detecter(resultat);
    }

    @Test
    void etatValidation_peutValiderSeulementSiToutEstNote() {
        question("Java", 2);
        question("SQL", null);
        question("Docker", null);

        var etat = service.etatValidationSuperieur(evaluation.getId(), ro());

        assertThat(etat.dejaValidee()).isFalse();
        assertThat(etat.collaborateurAValide()).isTrue();
        assertThat(etat.avecCompetencesTechniques()).isTrue();
        assertThat(etat.nbCompetencesManquantes()).isEqualTo(2);
        assertThat(etat.competencesManquantes()).extracting(c -> c.libelle()).containsExactly("SQL", "Docker");
        assertThat(etat.peutValider()).isFalse();

        reponses.clear();
        questions.clear();
        question("Java", 2);
        assertThat(service.etatValidationSuperieur(evaluation.getId(), ro()).peutValider()).isTrue();
    }

    @Test
    void etatValidation_collaborateurNonValide_ouDejaValidee_pasValidable() {
        evaluation.setValidationCollaborateurLe(null);
        assertThat(service.etatValidationSuperieur(evaluation.getId(), ro()).peutValider()).isFalse();

        evaluation.setValidationCollaborateurLe(Instant.now());
        evaluation.setValidationSuperieurLe(Instant.now());
        var etat = service.etatValidationSuperieur(evaluation.getId(), ro());
        assertThat(etat.dejaValidee()).isTrue();
        assertThat(etat.peutValider()).isFalse();
    }

    @Test
    void etatValidation_pasLeRo_403() {
        IdentiteAppelant autre = new IdentiteAppelant(UUID.randomUUID(), "x", true, Optional::empty);
        assertThatThrownBy(() -> service.etatValidationSuperieur(evaluation.getId(), autre))
                .satisfies(t -> assertErreur(t, 403, "PAS_RO_EVALUATION"));
    }
}
