package com.hr.evaluation.service;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.SkillAnswer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EvaluationScoringService {

	private final int seuilInsuffisant;

	public EvaluationScoringService(@Value("${evaluation.alert.seuil-insuffisant:3}") int seuilInsuffisant) {
		this.seuilInsuffisant = seuilInsuffisant;
	}

	public EvaluationScore calculer(Integer... notes) {
		List<Integer> valeurs = Arrays.stream(notes)
				.filter(v -> v != null)
				.toList();
		if (valeurs.isEmpty()) {
			throw new IllegalArgumentException("Au moins une note est obligatoire.");
		}
		int total = valeurs.stream().mapToInt(Integer::intValue).sum();
		int scoreSur20 = (int) Math.round((total * 20.0d) / (valeurs.size() * 5.0d));
		AppreciationEvaluationRh appreciation = appreciation(scoreSur20);
		CouleurAlerteEvaluationRh couleur = couleur(valeurs);
		return new EvaluationScore(scoreSur20, appreciation, couleur, recommandation(couleur, valeurs));
	}

	public EvaluationAnalyticsResponse analyser(List<EvaluationAnswer> generalAnswers, List<SkillAnswer> skillAnswers) {
		List<ScoreLine> lines = new ArrayList<>();

		for (EvaluationAnswer answer : generalAnswers) {
			Integer self = firstNonNull(answer.getNoteCollaborateur(), answer.getNoteAttribuee());
			Integer manager = answer.getNoteManager();
			if (self != null || manager != null) {
				String section = firstText(answer.getQuestion().getSectionLibelle(), answer.getQuestion().getSectionCode(), "General");
				BigDecimal weight = answer.getQuestion().getPoids() != null ? answer.getQuestion().getPoids() : BigDecimal.ONE;
				lines.add(new ScoreLine(
						answer.getQuestion().getId().toString(),
						answer.getQuestion().getLibelle(),
						section,
						self,
						manager,
						weight
				));
			}
		}

		for (SkillAnswer answer : skillAnswers) {
			Integer self = answer.getNiveauAutoEvaluation() != null ? answer.getNiveauAutoEvaluation().score() : null;
			Integer manager = answer.getNiveauManager() != null ? answer.getNiveauManager().score() : null;
			if (self != null || manager != null) {
				lines.add(new ScoreLine(
						answer.getQuestionTechnique().getId().toString(),
						answer.getQuestionTechnique().getCompetence(),
						"Competencies",
						self,
						manager,
						BigDecimal.ONE
				));
			}
		}

		BigDecimal selfAverage = weightedAverage(lines, true);
		BigDecimal managerAverage = weightedAverage(lines, false);
		BigDecimal finalScore = managerAverage.multiply(BigDecimal.valueOf(0.7))
				.add(selfAverage.multiply(BigDecimal.valueOf(0.3)))
				.setScale(2, RoundingMode.HALF_UP);

		List<EvaluationAnalyticsResponse.GapItem> gaps = lines.stream()
				.filter(line -> line.selfScore != null && line.managerScore != null)
				.map(line -> {
					int gap = Math.abs(line.selfScore - line.managerScore);
					return new EvaluationAnalyticsResponse.GapItem(
							line.questionId,
							line.label,
							line.section,
							line.selfScore,
							line.managerScore,
							gap,
							gapSeverity(gap)
					);
				})
				.toList();

		BigDecimal averageGap = gaps.isEmpty()
				? BigDecimal.ZERO
				: BigDecimal.valueOf(gaps.stream().mapToInt(EvaluationAnalyticsResponse.GapItem::gap).average().orElse(0))
						.setScale(2, RoundingMode.HALF_UP);
		BigDecimal discrepancy = averageGap.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);

		List<EvaluationAnalyticsResponse.SectionScore> sectionScores = sectionScores(lines);
		List<String> strengths = lines.stream()
				.filter(line -> firstNonNull(line.managerScore, line.selfScore) != null)
				.filter(line -> firstNonNull(line.managerScore, line.selfScore) >= 4)
				.map(line -> line.label)
				.limit(5)
				.toList();
		List<String> improvements = lines.stream()
				.filter(line -> firstNonNull(line.managerScore, line.selfScore) != null)
				.filter(line -> firstNonNull(line.managerScore, line.selfScore) <= 2)
				.map(line -> line.label)
				.limit(5)
				.toList();

		List<String> recommendations = new ArrayList<>();
		if (discrepancy.compareTo(BigDecimal.valueOf(30)) >= 0) {
			recommendations.add("Planifier un entretien de calibration: les ecarts self/manager sont significatifs.");
		}
		if (!improvements.isEmpty()) {
			recommendations.add("Creer un plan de developpement cible sur les axes faibles identifies.");
		}
		if (recommendations.isEmpty()) {
			recommendations.add("Maintenir les objectifs et suivre l'evolution au prochain cycle.");
		}

		return new EvaluationAnalyticsResponse(
				selfAverage,
				managerAverage,
				finalScore,
				sum(lines, true),
				sum(lines, false),
				averageGap,
				discrepancy,
				gaps,
				sectionScores,
				strengths,
				improvements,
				recommendations
		);
	}

	private AppreciationEvaluationRh appreciation(int scoreSur20) {
		if (scoreSur20 <= 7) {
			return AppreciationEvaluationRh.INSUFFISANT;
		}
		if (scoreSur20 <= 10) {
			return AppreciationEvaluationRh.A_AMELIORER;
		}
		if (scoreSur20 <= 14) {
			return AppreciationEvaluationRh.SATISFAISANT;
		}
		if (scoreSur20 <= 17) {
			return AppreciationEvaluationRh.POSITIF;
		}
		return AppreciationEvaluationRh.EXCELLENT;
	}

	private CouleurAlerteEvaluationRh couleur(List<Integer> valeurs) {
		long insuffisants = valeurs.stream()
				.filter(v -> v < seuilInsuffisant)
				.count();
		boolean tousExcellents = valeurs.stream().allMatch(v -> v == 5);
		if (tousExcellents) {
			return CouleurAlerteEvaluationRh.VERT;
		}
		if (insuffisants >= 3) {
			return CouleurAlerteEvaluationRh.ROUGE;
		}
		if (insuffisants == 2) {
			return CouleurAlerteEvaluationRh.ORANGE;
		}
		return CouleurAlerteEvaluationRh.VERT;
	}

	private String recommandation(CouleurAlerteEvaluationRh couleur, List<Integer> valeurs) {
		return switch (couleur) {
			case VERT -> "Profil stable. Maintenir les objectifs et capitaliser sur les points forts.";
			case ORANGE -> "Deux criteres sous le seuil. Prevoir une alerte RH et un suivi rapproche.";
			case ROUGE -> "Trois criteres ou plus sont insuffisants. Plan d'action obligatoire et escalade DG.";
		};
	}

	private BigDecimal weightedAverage(List<ScoreLine> lines, boolean self) {
		BigDecimal totalWeight = BigDecimal.ZERO;
		BigDecimal total = BigDecimal.ZERO;
		for (ScoreLine line : lines) {
			Integer score = self ? line.selfScore : line.managerScore;
			if (score != null) {
				total = total.add(BigDecimal.valueOf(score).multiply(line.weight));
				totalWeight = totalWeight.add(line.weight);
			}
		}
		if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return total.divide(totalWeight, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal sum(List<ScoreLine> lines, boolean self) {
		return lines.stream()
				.map(line -> self ? line.selfScore : line.managerScore)
				.filter(Objects::nonNull)
				.map(BigDecimal::valueOf)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
	}

	private List<EvaluationAnalyticsResponse.SectionScore> sectionScores(List<ScoreLine> lines) {
		Map<String, List<ScoreLine>> bySection = new LinkedHashMap<>();
		for (ScoreLine line : lines) {
			bySection.computeIfAbsent(line.section, ignored -> new ArrayList<>()).add(line);
		}
		return bySection.entrySet().stream()
				.map(entry -> {
					BigDecimal self = weightedAverage(entry.getValue(), true);
					BigDecimal manager = weightedAverage(entry.getValue(), false);
					return new EvaluationAnalyticsResponse.SectionScore(
							entry.getKey(),
							self,
							manager,
							self.subtract(manager).abs().setScale(2, RoundingMode.HALF_UP)
					);
				})
				.toList();
	}

	private String gapSeverity(int gap) {
		if (gap <= 0) {
			return "ALIGNED";
		}
		if (gap <= 1) {
			return "MODERATE";
		}
		if (gap <= 3) {
			return "HIGH";
		}
		return "CRITICAL";
	}

	private Integer firstNonNull(Integer first, Integer second) {
		return first != null ? first : second;
	}

	private String firstText(String first, String second, String fallback) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return fallback;
	}

	private record ScoreLine(
			String questionId,
			String label,
			String section,
			Integer selfScore,
			Integer managerScore,
			BigDecimal weight
	) {}
}
