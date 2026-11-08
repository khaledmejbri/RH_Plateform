package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Collaborateur avec son affectation actuelle (direction, département, service) pour la vue « Temps de travail »
 * de svc-presence (D-P17). Un libellé est nul quand la hiérarchie ne comporte pas ce niveau.
 */
public record AffectationCollaborateurResponse(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("compte_id") UUID compteId,
		@JsonProperty("matricule") String matricule,
		@JsonProperty("nom") String nom,
		@JsonProperty("direction_libelle") String directionLibelle,
		@JsonProperty("departement_libelle") String departementLibelle,
		@JsonProperty("service_libelle") String serviceLibelle,
		@JsonProperty("actif") boolean actif,
		@JsonProperty("poste_libelle") String posteLibelle,
		@JsonProperty("unite_id") UUID uniteId,
		@JsonProperty("unite_libelle") String uniteLibelle
) {
}
