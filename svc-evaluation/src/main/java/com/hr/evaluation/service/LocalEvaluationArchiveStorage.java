package com.hr.evaluation.service;

import com.hr.evaluation.config.EvaluationArchiveProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalEvaluationArchiveStorage implements EvaluationArchiveStorage {

	private final EvaluationArchiveProperties properties;

	public LocalEvaluationArchiveStorage(EvaluationArchiveProperties properties) {
		this.properties = properties;
	}

	@Override
	public ArchivedPdf stocker(String objectKey, byte[] contenu) {
		try {
			Path root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
			Path target = root.resolve(objectKey).normalize();
			if (!target.startsWith(root)) {
				throw new IllegalArgumentException("Chemin d'archive invalide.");
			}
			Files.createDirectories(target.getParent());
			Files.write(target, contenu);
			String s3Url = "s3://" + properties.getS3Bucket() + "/" + objectKey.replace('\\', '/');
			return new ArchivedPdf(objectKey, s3Url);
		} catch (IOException ex) {
			throw new IllegalStateException("Impossible d'archiver le PDF d'evaluation.", ex);
		}
	}
}
