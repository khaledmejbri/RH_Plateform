package com.hr.evaluation.service;

public interface EvaluationArchiveStorage {

	ArchivedPdf stocker(String objectKey, byte[] contenu);
}
