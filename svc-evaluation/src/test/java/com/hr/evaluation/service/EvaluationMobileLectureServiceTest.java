package com.hr.evaluation.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hr.evaluation.client.PersonneResumee;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.dto.AnalyseMobile;
import com.hr.evaluation.dto.EvaluationDetailMobile;
import com.hr.evaluation.dto.ReponseMobile;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-E15 : le collaborateur ne voit la notation du responsable qu'au statut VALIDEE ; le responsable la voit toujours. */
class EvaluationMobileLectureServiceTest {

	private final UUID compteCollab = UUID.randomUUID();
	private final UUID compteResp = UUID.randomUUID();
	private final UUID evaluationId = UUID.randomUUID();

	private EvaluationRepository evaluationRepository;
	private EvaluationAnswerRepository answerRepository;
	private SkillAnswerRepository skillAnswerRepository;
	private EvaluationQuestionRepository questionRepository;
	private TemplateTechniqueEvaluation templateTechnique;
	private EvaluationMobileLectureService service;
	private Evaluation evaluation;

	@BeforeEach
	void setUp() {
		evaluationRepository = mock(EvaluationRepository.class);
		answerRepository = mock(EvaluationAnswerRepository.class);
		skillAnswerRepository = mock(SkillAnswerRepository.class);
		questionRepository = mock(EvaluationQuestionRepository.class);
		templateTechnique = mock(TemplateTechniqueEvaluation.class);
		LibellesProfilService libelles = mock(LibellesProfilService.class);
		NomsCollaborateurs noms = mock(NomsCollaborateurs.class);
		when(noms.resoudre(any())).thenReturn(Map.<UUID, PersonneResumee>of());
		service = new EvaluationMobileLectureService(evaluationRepository, answerRepository, skillAnswerRepository,
				questionRepository, new EvaluationScoringService(3), templateTechnique, libelles, noms);

		evaluation = new Evaluation();
		evaluation.setId(evaluationId);
		evaluation.setCampaign(new EvaluationCampaign());
		evaluation.setCollaborateurIdentifiant(UUID.randomUUID());
		evaluation.setSuperieurIdentifiant(UUID.randomUUID());
		evaluation.setCollaborateurCompteId(compteCollab);
		evaluation.setSuperieurCompteId(compteResp);
		evaluation.setScoreSur20(15);
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));
		when(templateTechnique.questions(evaluation)).thenReturn(List.of());

		EvaluationQuestion q = new EvaluationQuestion();
		q.setId(UUID.randomUUID());
		q.setLibelle("Communication");
		q.setTypeQuestion(QuestionType.SCALE);
		EvaluationAnswer a = new EvaluationAnswer();
		a.setId(UUID.randomUUID());
		a.setQuestion(q);
		a.setReponseCollaborateur("ok");
		a.setNoteCollaborateur(4);
		a.setReponseManager("bien");
		a.setCommentaireManager("a progresser");
		a.setNoteManager(2);
		when(answerRepository.findWithQuestionByEvaluationId(evaluationId)).thenReturn(List.of(a));
		when(skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId)).thenReturn(List.of());
	}

	private IdentiteAppelant collaborateur() {
		return new IdentiteAppelant(compteCollab, "col", false, Optional::empty);
	}

	private IdentiteAppelant responsable() {
		return new IdentiteAppelant(compteResp, "ro", false, Optional::empty);
	}

	@Test
	void reponses_collaborateurAvantValidation_notesDuResponsableAbsentes() {
		for (StatutEvaluationRh statut : List.of(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE,
				StatutEvaluationRh.VALIDEE_COLLABORATEUR, StatutEvaluationRh.VALIDEE_SUPERIEUR)) {
			evaluation.setStatut(statut);
			ReponseMobile r = service.reponses(evaluationId, collaborateur()).get(0);
			assertThat(r.noteManager()).as(statut.name()).isNull();
			assertThat(r.reponseManager()).isNull();
			assertThat(r.commentaireManager()).isNull();
			assertThat(r.noteCollaborateur()).isEqualTo(4);
			assertThat(r.reponseCollaborateur()).isEqualTo("ok");
		}
	}

	@Test
	void reponses_collaborateurApresValidation_notesDuResponsablePresentes() {
		evaluation.setStatut(StatutEvaluationRh.VALIDEE);
		ReponseMobile r = service.reponses(evaluationId, collaborateur()).get(0);
		assertThat(r.noteManager()).isEqualTo(2);
		assertThat(r.commentaireManager()).isEqualTo("a progresser");
	}

	@Test
	void reponses_responsableVoitToujoursLesDeuxSeries() {
		evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);
		ReponseMobile r = service.reponses(evaluationId, responsable()).get(0);
		assertThat(r.noteManager()).isEqualTo(2);
		assertThat(r.noteCollaborateur()).isEqualTo(4);
	}

	@Test
	void detail_collaborateurAvantValidation_champsResponsableScoreEtScoresAbsents_messageAttente() {
		evaluation.setStatut(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
		EvaluationDetailMobile d = service.detail(evaluationId, collaborateur());
		assertThat(d.monRole()).isEqualTo("COLLABORATEUR");
		assertThat(d.scoreSur20()).isNull();
		assertThat(d.scores()).isNull();
		assertThat(d.messageAttente()).isNotBlank();
		EvaluationDetailMobile.Question q = d.questionsGenerales().get(0);
		assertThat(q.noteResponsable()).isNull();
		assertThat(q.reponseResponsable()).isNull();
		assertThat(q.commentaireResponsable()).isNull();
		assertThat(q.noteCollaborateur()).isEqualTo(4);
	}

	@Test
	void detail_collaborateurApresValidation_toutEstVisible_sansMessage() {
		evaluation.setStatut(StatutEvaluationRh.VALIDEE);
		EvaluationDetailMobile d = service.detail(evaluationId, collaborateur());
		assertThat(d.messageAttente()).isNull();
		assertThat(d.scoreSur20()).isEqualTo(15);
		assertThat(d.scores().moyenneResponsable()).isNotNull();
		assertThat(d.scores().appreciation()).isEqualTo("POSITIF");
		assertThat(d.scores().appreciationLibelle()).isEqualTo("Positif");
		assertThat(d.questionsGenerales().get(0).noteResponsable()).isEqualTo(2);
	}

	@Test
	void detail_responsable_voitLesNotesAvantValidation() {
		evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);
		EvaluationDetailMobile d = service.detail(evaluationId, responsable());
		assertThat(d.monRole()).isEqualTo("RESPONSABLE");
		assertThat(d.messageAttente()).isNull();
		assertThat(d.questionsGenerales().get(0).noteResponsable()).isEqualTo(2);
	}

	@Test
	void detail_neContientAucunChampAlerteOuNiveauEcart() {
		for (Class<?> dto : List.of(EvaluationDetailMobile.class, EvaluationDetailMobile.Question.class,
				EvaluationDetailMobile.Scores.class, ReponseMobile.class, AnalyseMobile.class,
				AnalyseMobile.Ecart.class)) {
			for (var composant : dto.getRecordComponents()) {
				assertThat(composant.getName().toLowerCase()).doesNotStartWith("alerte").doesNotContain("niveauecart");
			}
			assertThat(dto.getAnnotation(JsonInclude.class)).as(dto.getSimpleName()).isNotNull();
		}
	}

	@Test
	void analyse_collaborateurAvantValidation_rienDuResponsable_etEntiteNonModifiee() {
		evaluation.setStatut(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
		AnalyseMobile a = service.analyse(evaluationId, collaborateur());
		assertThat(a.managerAverage()).isNull();
		assertThat(a.finalScore()).isNull();
		assertThat(a.averageGap()).isNull();
		assertThat(a.gaps()).isNull();
		assertThat(a.aNotesResponsable()).isNull();
		assertThat(a.selfAverage()).isNotNull();
		// la note du responsable de l'entité d'origine n'a pas été effacée
		assertThat(answerRepository.findWithQuestionByEvaluationId(evaluationId).get(0).getNoteManager()).isEqualTo(2);
	}

	@Test
	void analyse_responsableOuApresValidation_completeSansNiveauEcart() {
		evaluation.setStatut(StatutEvaluationRh.VALIDEE);
		AnalyseMobile a = service.analyse(evaluationId, collaborateur());
		assertThat(a.managerAverage()).isNotNull();
		assertThat(a.finalScore()).isNotNull();
		assertThat(a.gaps()).hasSize(1);
		evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);
		assertThat(service.analyse(evaluationId, responsable()).managerAverage()).isNotNull();
	}

	@Test
	void verifierExiste_inconnue_404() {
		when(evaluationRepository.existsById(any())).thenReturn(false);
		assertThatThrownBy(() -> service.verifierExiste(UUID.randomUUID()))
				.isInstanceOfSatisfying(EvaluationMetierException.class,
						e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void notesResponsableVisibles_seulementValideeOuArchivee() {
		assertThat(Set.of(StatutEvaluationRh.values()).stream()
				.filter(EvaluationMobileLectureService::notesResponsableVisibles).toList())
				.containsExactlyInAnyOrder(StatutEvaluationRh.VALIDEE, StatutEvaluationRh.ARCHIVEE);
	}
}
