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
}
