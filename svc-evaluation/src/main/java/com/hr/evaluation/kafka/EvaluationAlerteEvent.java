package com.hr.evaluation.kafka;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;

import java.time.Instant;
import java.util.UUID;

public record EvaluationAlerteEvent(
		UUID evaluationIdentifiant,
		UUID collaborateurIdentifiant,
		UUID superieurIdentifiant,
		TypeEvaluationRh type,
		Integer annee,
		CouleurAlerteEvaluationRh couleurAlerte,
		String message,
		Instant emisLe
) {
}
