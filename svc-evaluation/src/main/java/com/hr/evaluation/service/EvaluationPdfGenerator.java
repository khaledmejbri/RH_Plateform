package com.hr.evaluation.service;

import com.hr.evaluation.entity.EvaluationRh;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class EvaluationPdfGenerator {

	public byte[] generer(EvaluationRh evaluation) {
		List<String> lignes = new ArrayList<>();
		lignes.add("Evaluation RH " + evaluation.getType());
		lignes.add("Collaborateur: " + evaluation.getCollaborateurIdentifiant());
		lignes.add("Superieur: " + evaluation.getSuperieurIdentifiant());
		lignes.add("Annee: " + evaluation.getAnnee());
		if (evaluation.getSemestre() != null) {
			lignes.add("Semestre: " + evaluation.getSemestre());
		}
		lignes.add("Score: " + evaluation.getScoreSur20() + "/20");
		lignes.add("Appreciation: " + evaluation.getAppreciation());
		lignes.add("Alerte: " + evaluation.getCouleurAlerte());
		lignes.add("Points forts: " + nonNull(evaluation.getPointsForts()));
		lignes.add("Points a ameliorer: " + nonNull(evaluation.getPointsAAmeliorer()));
		lignes.add("Plan d'action: " + nonNull(evaluation.getPlanActionRecommande()));
		lignes.add("Recommandations: " + nonNull(evaluation.getRecommandationsIa()));
		lignes.add("Archive le: " + Instant.now());
		return toSimplePdf(lignes);
	}

	private static byte[] toSimplePdf(List<String> lignes) {
		StringBuilder text = new StringBuilder("BT /F1 11 Tf 50 790 Td ");
		for (String ligne : lignes) {
			text.append("(").append(escape(ligne)).append(") Tj T* ");
		}
		text.append("ET");
		byte[] stream = text.toString().getBytes(StandardCharsets.US_ASCII);

		String header = "%PDF-1.4\n";
		String obj1 = "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n";
		String obj2 = "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n";
		String obj3 = "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n";
		String obj4 = "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n";
		String obj5 = "5 0 obj << /Length " + stream.length + " >> stream\n" + new String(stream, StandardCharsets.US_ASCII) + "\nendstream endobj\n";
		String body = obj1 + obj2 + obj3 + obj4 + obj5;
		String trailer = "trailer << /Root 1 0 R >>\n%%EOF\n";
		return (header + body + trailer).getBytes(StandardCharsets.US_ASCII);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\")
				.replace("(", "\\(")
				.replace(")", "\\)")
				.replaceAll("[^\\x20-\\x7E]", "?");
	}

	private static String nonNull(String value) {
		return value == null ? "" : value;
	}
}
