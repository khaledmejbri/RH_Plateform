package com.hr.presence.service;

import com.hr.presence.domain.PointageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement applicatif publié lors de l'enregistrement d'un nouveau pointage {@code VALIDE}.
 * Écouté uniquement en {@code TransactionPhase.AFTER_COMMIT} pour exécution non bloquante sur Virtual Thread.
 */
public record PointageValideCreeEvent(
		UUID pointageId,
		String collaborateurId,
		PointageType type,
		Instant serverTs,
		UUID siteId,
		String siteLibelle,
		UUID societeId
) {
}
