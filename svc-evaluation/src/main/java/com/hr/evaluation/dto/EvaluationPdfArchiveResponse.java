package com.hr.evaluation.dto;

import java.time.Instant;
import java.util.UUID;

public record EvaluationPdfArchiveResponse(
		UUID evaluationIdentifiant,
		String objectKey,
		String archiveUrl,
		Instant archiveLe
) {
}
