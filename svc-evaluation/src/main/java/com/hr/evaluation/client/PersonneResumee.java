package com.hr.evaluation.client;

import java.util.UUID;

/**
 * Personne telle que le référentiel la décrit pour un affichage (ADR lot 2 § 1) : nom, matricule et rattachement.
 * Interne au service : jamais renvoyée telle quelle à un écran.
 */
public record PersonneResumee(
		UUID collaborateurId,
		UUID compteId,
		String nomComplet,
		String matricule,
		Boolean actif,
		String posteLibelle,
		UUID uniteId,
		String uniteLibelle) {
}
