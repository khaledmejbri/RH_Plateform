package com.hr.evaluation.dto;

import java.util.List;

/** Page de lignes d'évaluation d'une campagne (ticket 2B, B2). */
public record PageEvaluations(List<EvaluationLigne> elements, long total, int page, int taille) {
}
