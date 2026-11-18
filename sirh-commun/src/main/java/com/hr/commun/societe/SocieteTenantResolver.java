package com.hr.commun.societe;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import java.util.UUID;

/**
 * Résolveur Hibernate {@code @TenantId} : renvoie la société courante, sinon {@link SocieteContexte#AUCUNE}
 * afin qu'aucune ligne métier ne soit visible hors contexte.
 */
public class SocieteTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return SocieteContexte.couranteOuVide()
				.filter(id -> !SocieteContexte.AUCUNE.equals(id))
				.orElse(SocieteContexte.AUCUNE);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}
}
