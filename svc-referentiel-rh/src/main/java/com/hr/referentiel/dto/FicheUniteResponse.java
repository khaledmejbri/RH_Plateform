package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** D-N01 : fiche d'une unité avec chemin, responsable, sous-unités et membres. */
public record FicheUniteResponse(
		@JsonProperty("unite") UniteResume unite,
		@JsonProperty("code") String code,
		@JsonProperty("actif") boolean actif,
		@JsonProperty("a_rattacher") boolean aRattacher,
		@JsonProperty("chemin") List<UniteResume> chemin,
		@JsonProperty("parent") UniteResume parent,
		@JsonProperty("responsable") PersonneResume responsable,
		@JsonProperty("sous_unites") List<SousUnite> sousUnites,
		@JsonProperty("membres") List<PersonneResume> membres,
		@JsonProperty("effectif_direct") int effectifDirect,
		@JsonProperty("effectif_total") int effectifTotal
) {

	public record SousUnite(
			@JsonProperty("unite") UniteResume unite,
			@JsonProperty("effectif_total") int effectifTotal) {
	}
}
