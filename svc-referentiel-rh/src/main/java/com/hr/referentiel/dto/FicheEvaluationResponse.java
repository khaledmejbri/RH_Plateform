package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Fiche d'un collaborateur pour le snapshot d'une alerte d'écart M07 (appel interne de svc-evaluation) :
 * compte, matricule, nom et unité de rattachement. Aucune autre donnée personnelle.
 */
public record FicheEvaluationResponse(
		@JsonProperty("identifiant") UUID identifiant,
		@JsonProperty("compte_utilisateur_id") UUID compteUtilisateurId,
		@JsonProperty("matricule") String matricule,
		@JsonProperty("nom_complet") String nomComplet,
		@JsonProperty("unite_identifiant") UUID uniteIdentifiant,
		@JsonProperty("unite_libelle") String uniteLibelle,
		@JsonProperty("statut") String statut
) {
}
