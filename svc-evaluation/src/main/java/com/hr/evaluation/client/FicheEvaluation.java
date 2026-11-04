package com.hr.evaluation.client;

import java.util.UUID;

/** Fiche d'un collaborateur lue au référentiel pour le snapshot d'une alerte d'écart (ADR M07 § 7). */
public record FicheEvaluation(
		UUID identifiant,
		UUID compteUtilisateurId,
		String matricule,
		String nomComplet,
		UUID uniteIdentifiant,
		String uniteLibelle,
		String statut) {
}
