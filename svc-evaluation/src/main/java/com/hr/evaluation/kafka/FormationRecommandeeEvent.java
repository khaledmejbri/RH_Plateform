package com.hr.evaluation.kafka;

import java.time.Instant;
import java.util.UUID;

public record FormationRecommandeeEvent(
		UUID evaluationIdentifiant,
		UUID collaborateurIdentifiant,
		UUID superieurIdentifiant,
		Integer annee,
		String intituleFormation,
		String origine,
		Instant emisLe
) {
}
