package com.hr.evaluation.client;

import java.util.Optional;

/** Lecture de la fiche de l'appelant courant avec son JWT relayé ({@code /collaborateurs/moi}). */
public interface ReferentielMoiClient {

	/** {@code Optional.empty()} si l'appelant n'a pas de fiche ; lève une exception si le référentiel est injoignable. */
	Optional<MoiReferentiel> moi();
}
