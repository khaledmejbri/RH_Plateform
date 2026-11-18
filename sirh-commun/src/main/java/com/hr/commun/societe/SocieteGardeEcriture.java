package com.hr.commun.societe;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.Optional;
import java.util.UUID;

/**
 * Écouteur JPA interdisant toute écriture ({@link PrePersist}, {@link PreUpdate})
 * lorsque la société courante est absente ou égale à {@link SocieteContexte#AUCUNE}.
 */
public class SocieteGardeEcriture {

	@PrePersist
	@PreUpdate
	public void verifierSociete(Object entite) {
		Optional<UUID> courante = SocieteContexte.couranteOuVide();
		if (courante.isEmpty() || SocieteContexte.AUCUNE.equals(courante.get())) {
			throw new IllegalStateException("Écriture JPA refusée : aucune société active dans SocieteContexte");
		}
	}
}
