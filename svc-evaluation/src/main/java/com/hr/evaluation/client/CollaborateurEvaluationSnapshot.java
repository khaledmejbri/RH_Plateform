package com.hr.evaluation.client;

import java.util.UUID;

/**
 * Snapshot collaborateur pour activation campagne (E4).
 */
public record CollaborateurEvaluationSnapshot(
		UUID identifiant,
		String statut,
		String familleMetierCode,
		String niveauSeniorite,
		UUID superieurIdentifiant,
		String profilAcces,
		UUID compteUtilisateurId,
		String nomComplet,
		String posteLibelle,
		UUID superieurCompteUtilisateurId,
		String superieurNomComplet
) {
}
