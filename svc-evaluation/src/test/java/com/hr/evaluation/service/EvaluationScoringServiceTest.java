package com.hr.evaluation.service;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationScoringServiceTest {

	private final EvaluationScoringService service = new EvaluationScoringService(3);

	@Test
	void scoreSemestrielExcellentEstVert() {
		EvaluationScore score = service.calculer(5, 5, 5, 5);

		assertThat(score.scoreSur20()).isEqualTo(20);
		assertThat(score.appreciation()).isEqualTo(AppreciationEvaluationRh.EXCELLENT);
		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.VERT);
	}

	@Test
	void deuxCriteresSousSeuilDeclenchentOrange() {
		EvaluationScore score = service.calculer(2, 2, 4, 4);

		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.ORANGE);
		assertThat(score.recommandationsIa()).contains("alerte RH");
	}

	@Test
	void troisCriteresSousSeuilDeclenchentRouge() {
		EvaluationScore score = service.calculer(1, 2, 2, 5);

		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.ROUGE);
		assertThat(score.recommandationsIa()).contains("Plan d'action obligatoire");
	}

	// --- D-E09.c : niveau d ecart des competences techniques ---

	private static com.hr.evaluation.entity.EvaluationAnswer reponse(java.util.UUID questionId, int collaborateur, int ro) {
		com.hr.evaluation.entity.EvaluationQuestion q = new com.hr.evaluation.entity.EvaluationQuestion();
		q.setId(questionId);
		q.setLibelle("Q " + questionId);
		com.hr.evaluation.entity.EvaluationAnswer a = new com.hr.evaluation.entity.EvaluationAnswer();
		a.setQuestion(q);
		a.setNoteCollaborateur(collaborateur);
		a.setNoteManager(ro);
		return a;
	}

	@Test
	void niveauEcartDesCompetencesTechniques_alignOrangeUrgente_questionGeneraleSansNiveau() {
		java.util.UUID t42 = java.util.UUID.randomUUID();
		java.util.UUID t51 = java.util.UUID.randomUUID();
		java.util.UUID t32 = java.util.UUID.randomUUID();
		java.util.UUID g42 = java.util.UUID.randomUUID();

		var analyse = service.analyser(
				java.util.List.of(reponse(t42, 4, 2), reponse(t51, 5, 1), reponse(t32, 3, 2), reponse(g42, 4, 2)),
				java.util.List.of(),
				java.util.Set.of(t42, t51, t32));

		var parQuestion = analyse.gaps().stream().collect(java.util.stream.Collectors.toMap(
				g -> g.questionId(), g -> g));
		assertThat(parQuestion.get(t42.toString()).niveauEcart())
				.isEqualTo(com.hr.evaluation.domain.NiveauEcart.ORANGE);
		assertThat(parQuestion.get(t51.toString()).niveauEcart())
				.isEqualTo(com.hr.evaluation.domain.NiveauEcart.URGENTE);
		assertThat(parQuestion.get(t32.toString()).niveauEcart())
				.isEqualTo(com.hr.evaluation.domain.NiveauEcart.ALIGNE);
		assertThat(parQuestion.get(t42.toString()).competenceTechnique()).isTrue();
		assertThat(parQuestion.get(g42.toString()).niveauEcart()).isNull();
		assertThat(parQuestion.get(g42.toString()).competenceTechnique()).isFalse();
		assertThat(parQuestion.get(g42.toString()).severity()).isEqualTo("HIGH");
	}

	@Test
	void analyseMobile_ensembleVide_aucunNiveauDAlerte() {
		java.util.UUID t = java.util.UUID.randomUUID();

		var analyse = service.analyser(java.util.List.of(reponse(t, 5, 1)), java.util.List.of());

		assertThat(analyse.gaps()).singleElement().satisfies(g -> {
			assertThat(g.niveauEcart()).isNull();
			assertThat(g.competenceTechnique()).isFalse();
		});
	}
}
