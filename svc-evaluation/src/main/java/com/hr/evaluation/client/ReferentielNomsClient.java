package com.hr.evaluation.client;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Résolution par lot de fiches ou de comptes en personnes lisibles, auprès du référentiel (jeton interne). */
public interface ReferentielNomsClient {

	/** Taille maximale d'un lot accepté par le référentiel. */
	int LOT_MAX = 100;

	/**
	 * Résout 1 à {@code LOT_MAX} identifiants (fiche ou compte). Les identifiants inconnus sont absents de la
	 * réponse. Lève une exception si le référentiel est injoignable ou trop lent.
	 */
	List<PersonneResumee> resoudre(Collection<UUID> fichesOuComptes);
}
