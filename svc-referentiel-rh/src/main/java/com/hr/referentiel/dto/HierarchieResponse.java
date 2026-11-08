package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * D-N01 : hiérarchie d'un collaborateur. valideur = valideur D-H03 de ses demandes M01 (null = demandes à la RH,
 * valideurEstRh vrai). collaborateursEncadres = périmètre D-H03.
 */
public record HierarchieResponse(
		@JsonProperty("collaborateur") PersonneResume collaborateur,
		@JsonProperty("chemin") List<UniteResume> chemin,
		@JsonProperty("responsable_direct") PersonneResume responsableDirect,
		@JsonProperty("valideur") PersonneResume valideur,
		@JsonProperty("valideur_est_rh") boolean valideurEstRh,
		@JsonProperty("noeuds_manages") List<UniteResume> noeudsManages,
		@JsonProperty("collaborateurs_encadres") List<PersonneResume> collaborateursEncadres
) {
}
