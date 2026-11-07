package com.hr.evaluation.service;

import com.hr.evaluation.entity.EvaluationRh;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationPdfGeneratorTest {

	@Test
	void accentsConservesEnWinAnsiSansPointDInterrogation() {
		EvaluationRh e = new EvaluationRh();
		e.setRecommandationsIa("Créer un plan de développement ciblé.");

		String pdf = new String(new EvaluationPdfGenerator().generer(e), StandardCharsets.ISO_8859_1);

		assertThat(pdf).contains("/Encoding /WinAnsiEncoding").contains("(Évaluation RH").contains("Créer un plan de développement ciblé.");
		assertThat(pdf).doesNotContain("?valuation").doesNotContain("Cr?er");
	}
}
