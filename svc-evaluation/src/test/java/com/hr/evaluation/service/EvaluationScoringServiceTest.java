package com.hr.evaluation.service;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationScoringServiceTest {

	private final EvaluationScoringService service = new EvaluationScoringService(3);

	@Test
	void scoreSemestrielExcellentEstVert() {
		EvaluationScore score = service.calculer(5, 5, 5, 5);

		assertThat(score.scoreSur20()).isEqualTo(20);
		assertThat(score.appreciation()).isEqualTo(AppreciationEvaluationRh.EXCELLENT);
		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.VERT);
	}

	@Test
	void deuxCriteresSousSeuilDeclenchentOrange() {
		EvaluationScore score = service.calculer(2, 2, 4, 4);

		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.ORANGE);
		assertThat(score.recommandationsIa()).contains("alerte RH");
	}

	@Test
	void troisCriteresSousSeuilDeclenchentRouge() {
		EvaluationScore score = service.calculer(1, 2, 2, 5);

		assertThat(score.couleurAlerte()).isEqualTo(CouleurAlerteEvaluationRh.ROUGE);
		assertThat(score.recommandationsIa()).contains("Plan d'action obligatoire");
	}

	// --- D-E09.c : niveau d ecart des competences techniques ---

	private static com.hr.evaluation.entity.EvaluationAnswer reponse(java.util.UUID questionId, int collaborateur, int ro) {
		com.hr.evaluation.entity.EvaluationQuestion q = new com.hr.evaluation.entity.EvaluationQuestion();
		q.setId(questionId);
		q.setLibelle("Q " + questionId);
		com.hr.evaluation.entity.EvaluationAnswer a = new com.hr.evaluation.entity.EvaluationAnswer();
		a.setQuestion(q);
		a.setNoteCollaborateur(collaborateur);
		a.setNoteManager(ro);
		return a;
	}

	@Test
	void niveauEcartDesCompetencesTechniques_alignOrangeUrgente_questionGeneraleSansNiveau() {
		java.util.UUID t42 = java.util.UUID.randomUUID();
		java.util.UUID t51 = java.util.UUID.randomUUID();
		java.util.UUID t32 = java.util.UUID.randomUUID();
		java.util.UUID g42 = java.util.UUID.randomUUID();

		var analyse = service.analyser(
				java.util.List.of(reponse(t42, 4, 2), reponse(t51, 5, 1), reponse(t32, 3, 2), reponse(g42, 4, 2)),
				java.util.List.of(),
				java.util.Set.of(t42, t51, t32));

		var parQuestion = analyse.gaps().stream().collect(java.util.stream.Collectors.toMap(
				g -> g.questionId(), g -> g));
		assertThat(parQuestion.get(t42.toString()).niveauEcart())
				.isEqualTo("ORANGE");
		assertThat(parQuestion.get(t51.toString()).niveauEcart())
				.isEqualTo("URGENTE");
		assertThat(parQuestion.get(t32.toString()).niveauEcart())
				.isEqualTo("ALIGNE");
		assertThat(parQuestion.get(t42.toString()).competenceTechnique()).isTrue();
		assertThat(parQuestion.get(g42.toString()).niveauEcart()).isNull();
		assertThat(parQuestion.get(g42.toString()).competenceTechnique()).isFalse();
		assertThat(parQuestion.get(g42.toString()).severity()).isEqualTo("HIGH");
	}

	@Test
	void analyseMobile_ensembleVide_aucunNiveauDAlerte() {
		java.util.UUID t = java.util.UUID.randomUUID();

		var analyse = service.analyser(java.util.List.of(reponse(t, 5, 1)), java.util.List.of());

		assertThat(analyse.gaps()).singleElement().satisfies(g -> {
			assertThat(g.niveauEcart()).isNull();
			assertThat(g.competenceTechnique()).isFalse();
		});
	}

	// --- Lot 0 UAT : D-E14 (EVA-10), pas de score ni de recommandation sans note ---

	private static com.hr.evaluation.entity.EvaluationAnswer reponseSansNote() {
		com.hr.evaluation.entity.EvaluationQuestion q = new com.hr.evaluation.entity.EvaluationQuestion();
		q.setId(java.util.UUID.randomUUID());
		q.setLibelle("Q sans note");
		com.hr.evaluation.entity.EvaluationAnswer a = new com.hr.evaluation.entity.EvaluationAnswer();
		a.setQuestion(q);
		return a;
	}

	@Test
	void analyseSansAucuneNoteNeSignaleAucuneNoteEtNeRecommandeRien() {
		var analyse = service.analyser(java.util.List.of(reponseSansNote()), java.util.List.of());

		assertThat(analyse.aNotesCollaborateur()).isFalse();
		assertThat(analyse.aNotesResponsable()).isFalse();
		assertThat(analyse.recommendations()).isEmpty();
	}

	@Test
	void analyseAvecNotesCollaborateurSeulementSignaleSeulementLesNotesCollaborateur() {
		com.hr.evaluation.entity.EvaluationAnswer a = reponse(java.util.UUID.randomUUID(), 4, 4);
		a.setNoteManager(null);

		var analyse = service.analyser(java.util.List.of(a), java.util.List.of());

		assertThat(analyse.aNotesCollaborateur()).isTrue();
		assertThat(analyse.aNotesResponsable()).isFalse();
		assertThat(analyse.recommendations()).anyMatch(r -> r.contains("évolution"));
	}

	@Test
	void analyseAvecNotesDesDeuxCotesLesSignaleToutes() {
		var analyse = service.analyser(java.util.List.of(reponse(java.util.UUID.randomUUID(), 4, 3)), java.util.List.of());

		assertThat(analyse.aNotesCollaborateur()).isTrue();
		assertThat(analyse.aNotesResponsable()).isTrue();
	}

	// --- Lot 1 UAT, ticket 1B : D-L01.d, textes accentués (EVA-10) ---

	private static final java.util.regex.Pattern MOTS_SANS_ACCENT = java.util.regex.Pattern.compile(
			"\b(Creer|developpement|criteres|critere|evolution|Prevoir|ecarts|identifies|cible|rapproche)\b");

	@Test
	void recommandationsDeLAnalyseSontAccentuees() {
		java.util.UUID q1 = java.util.UUID.randomUUID();
		java.util.UUID q2 = java.util.UUID.randomUUID();
		// écarts importants et notes faibles : calibration + plan de développement
		var analyse = service.analyser(java.util.List.of(reponse(q1, 5, 1), reponse(q2, 5, 2)), java.util.List.of());

		assertThat(analyse.recommendations())
				.contains("Créer un plan de développement ciblé sur les axes faibles identifiés.")
				.anyMatch(r -> r.startsWith("Planifier un entretien de calibration : les écarts"))
				.noneMatch(r -> MOTS_SANS_ACCENT.matcher(r).find());
	}

	@Test
	void recommandationsDeScoreSontAccentuees() {
		assertThat(service.calculer(2, 2, 4, 4).recommandationsIa())
				.isEqualTo("Deux critères sous le seuil. Prévoir une alerte RH et un suivi rapproché.");
		assertThat(service.calculer(1, 2, 2, 5).recommandationsIa())
				.isEqualTo("Trois critères ou plus sont insuffisants. Plan d'action obligatoire et escalade DG.");
		for (int[] notes : new int[][] {{5, 5, 5, 5}, {2, 2, 4, 4}, {1, 2, 2, 5}}) {
			assertThat(service.calculer(notes[0], notes[1], notes[2], notes[3]).recommandationsIa())
					.doesNotContainPattern(MOTS_SANS_ACCENT.pattern());
		}
	}

	@Test
	void sectionParDefautAccentuee() {
		var analyse = service.analyser(java.util.List.of(reponse(java.util.UUID.randomUUID(), 4, 3)), java.util.List.of());

		assertThat(analyse.sections()).extracting(sec -> sec.section()).containsExactly("Général");
	}
}
