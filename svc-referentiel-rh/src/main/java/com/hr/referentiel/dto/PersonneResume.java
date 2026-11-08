package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Personne telle qu'un écran l'affiche (D-L01.a) ; intituleRole = intitulé du rôle d'un responsable (D-H06), sinon le poste. */
public record PersonneResume(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("nom_complet") String nomComplet,
		@JsonProperty("matricule") String matricule,
		@JsonProperty("poste_libelle") String posteLibelle,
		@JsonProperty("intitule_role") String intituleRole,
		@JsonProperty("unite_id") UUID uniteId,
		@JsonProperty("unite_libelle") String uniteLibelle,
		@JsonProperty("actif") boolean actif
) {
}
