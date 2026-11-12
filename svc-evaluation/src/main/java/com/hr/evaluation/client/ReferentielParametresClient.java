package com.hr.evaluation.client;

import java.util.List;

/**
 * {@code POST /internal/referentiel/parametres/utiliser} (ADR lot 3 § 6) : valide les familles et niveaux ciblés par
 * un template technique (existe, actif) et pose la marque « déjà utilisée » dans le référentiel. Sens d'appel existant
 * (évaluation vers référentiel), jamais l'inverse.
 */
public interface ReferentielParametresClient {

	/**
	 * @throws com.hr.evaluation.web.EvaluationMetierException 503 {@code REFERENTIEL_INDISPONIBLE} si le référentiel ne
	 *         répond pas ou ne connaît pas encore l'endpoint
	 */
	Resultat utiliser(List<Reference> references);

	record Reference(String referentiel, String code) {
	}

	/** Codes valides (marqués), inactifs (refusés) et inconnus (refusés). */
	record Resultat(List<String> valides, List<String> inactifs, List<String> inconnus) {
	}
}
