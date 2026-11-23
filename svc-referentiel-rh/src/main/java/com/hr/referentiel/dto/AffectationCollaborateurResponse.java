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
		@JsonProperty("unite_libelle") String uniteLibelle,
		@JsonProperty("prenom") String prenom,
		@JsonProperty("nom_famille") String nomFamille,
		@JsonProperty("fonction") String fonction,
		@JsonProperty("qualite") String qualite,
		@JsonProperty("qualification_affectation") String qualificationAffectation,
		@JsonProperty("affectation") String affectation
) {
	public AffectationCollaborateurResponse(
			UUID collaborateurId,
			UUID compteId,
			String matricule,
			String nom,
			String directionLibelle,
			String departementLibelle,
			String serviceLibelle,
			boolean actif,
			String posteLibelle,
			UUID uniteId,
			String uniteLibelle) {
		this(collaborateurId, compteId, matricule, nom, directionLibelle, departementLibelle, serviceLibelle,
				actif, posteLibelle, uniteId, uniteLibelle, null, null, null, null, null, null);
	}
}
