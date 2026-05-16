package com.hr.evaluation.service;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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
}
