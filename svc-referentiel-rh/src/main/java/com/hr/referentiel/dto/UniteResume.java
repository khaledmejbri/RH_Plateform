package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Nœud d'organigramme tel qu'un écran l'affiche : libellé et type en clair (D-H04). */
public record UniteResume(
		@JsonProperty("unite_id") UUID uniteId,
		@JsonProperty("libelle") String libelle,
		@JsonProperty("type_noeud") String typeNoeud,
		@JsonProperty("type_libelle") String typeLibelle
) {
}
