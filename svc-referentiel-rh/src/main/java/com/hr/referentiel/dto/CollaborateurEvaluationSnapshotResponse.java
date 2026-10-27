package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Projection légère pour activation campagne M07 (E4) — population ACTIF + snapshot matching.
 */
public record CollaborateurEvaluationSnapshotResponse(
		@JsonProperty("identifiant") UUID identifiant,
		@JsonProperty("statut") String statut,
		@JsonProperty("famille_metier_code") String familleMetierCode,
		@JsonProperty("niveau_seniorite") String niveauSeniorite,
		@JsonProperty("superieur_identifiant") UUID superieurIdentifiant,
		@JsonProperty("profil_acces") String profilAcces,
		@JsonProperty("compte_utilisateur_id") UUID compteUtilisateurId,
		@JsonProperty("nom_complet") String nomComplet,
		@JsonProperty("poste_libelle") String posteLibelle,
		@JsonProperty("superieur_compte_utilisateur_id") UUID superieurCompteUtilisateurId,
		@JsonProperty("superieur_nom_complet") String superieurNomComplet
) {
}
