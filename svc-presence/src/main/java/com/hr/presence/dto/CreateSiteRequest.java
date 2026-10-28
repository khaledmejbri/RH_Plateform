package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSiteRequest(
		@NotBlank
		@Size(max = 64)
		@JsonProperty("code") String code,

		@NotBlank
		@Size(max = 255)
		@JsonProperty("libelle") String libelle,

		@JsonProperty("latitude") Double latitude,

		@JsonProperty("longitude") Double longitude,

		/** EXTERIEUR (défaut) ou INTERIEUR — D-P01. */
		@JsonProperty("type_lieu") String typeLieu,

		/** Optionnel : défaut selon le type de lieu ; 10 à 100 m. */
		@JsonProperty("rayon_metres") Integer rayonMetres
) {
}
