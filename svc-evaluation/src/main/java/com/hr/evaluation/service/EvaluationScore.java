package com.hr.evaluation.service;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;

public record EvaluationScore(
		int scoreSur20,
		AppreciationEvaluationRh appreciation,
		CouleurAlerteEvaluationRh couleurAlerte,
		String recommandationsIa
) {
}
