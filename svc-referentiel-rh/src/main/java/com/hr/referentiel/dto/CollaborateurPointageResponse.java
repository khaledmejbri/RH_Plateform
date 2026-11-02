package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Collaborateur résolu pour les heures supplémentaires de svc-presence (appel interne) : identités, direction et
 * valideur D-H03 (D-P13.a). Ne contient que le nécessaire au contrôle de périmètre, au comptage et aux notifications.
 */
public record CollaborateurPointageResponse(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("compte_id") UUID compteId,
		@JsonProperty("matricule") String matricule,
		@JsonProperty("nom") String nom,
		@JsonProperty("actif") boolean actif,
		@JsonProperty("direction_id") UUID directionId,
		@JsonProperty("direction_libelle") String directionLibelle,
		@JsonProperty("valideur_id") UUID valideurId,
		@JsonProperty("valideur_compte_id") UUID valideurCompteId
) {
}
