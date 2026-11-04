package com.hr.evaluation.client;

import java.util.Optional;
import java.util.UUID;

/** Lecture interne (jeton partagé) de la fiche d'un collaborateur au référentiel. */
public interface ReferentielFicheClient {

	/** {@code Optional.empty()} si la fiche est inconnue ; lève une exception si le référentiel est injoignable. */
	Optional<FicheEvaluation> ficheEvaluation(UUID ficheId);
}
