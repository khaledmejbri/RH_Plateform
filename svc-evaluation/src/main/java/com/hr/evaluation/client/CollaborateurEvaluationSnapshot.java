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
		String superieurNomComplet,
		String familleMetierLibelle,
		String niveauSenioriteLibelle
) {

	/** Constructeur sans libellés (référentiel ancien ou tests) : les libellés restent nuls. */
	public CollaborateurEvaluationSnapshot(UUID identifiant, String statut, String familleMetierCode,
			String niveauSeniorite, UUID superieurIdentifiant, String profilAcces, UUID compteUtilisateurId,
			String nomComplet, String posteLibelle, UUID superieurCompteUtilisateurId, String superieurNomComplet) {
		this(identifiant, statut, familleMetierCode, niveauSeniorite, superieurIdentifiant, profilAcces,
				compteUtilisateurId, nomComplet, posteLibelle, superieurCompteUtilisateurId, superieurNomComplet,
				null, null);
	}
}
