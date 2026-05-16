package com.hr.evaluation.dto;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.domain.SemestreEvaluationRh;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationRhResponse(
		UUID identifiant,
		TypeEvaluationRh type,
		StatutEvaluationRh statut,
		UUID collaborateurIdentifiant,
		UUID superieurIdentifiant,
		Integer annee,
		SemestreEvaluationRh semestre,
		Integer qualiteTravail,
		Integer rendement,
		Integer ponctualite,
		Integer espritEquipe,
		Integer savoirTechnique,
		Integer savoirFaire,
		Integer savoirEtre,
		Integer scoreSur20,
		AppreciationEvaluationRh appreciation,
		CouleurAlerteEvaluationRh couleurAlerte,
		String pointsForts,
		String pointsAAmeliorer,
		String planActionRecommande,
		String recommandationsIa,
		String bilanSavoir,
		String bilanSavoirFaire,
		String bilanSavoirEtre,
		String resultatsObjectifsN,
		String objectifsNPlus1,
		List<String> formationsRecommandees,
		boolean formationsIntegreesM05,
		Instant validationCollaborateurLe,
		Instant validationSuperieurLe,
		String pdfObjectKey,
		String pdfArchiveUrl,
		Instant pdfArchiveLe,
		Instant creeLe,
		Instant modifieLe
) {
}
