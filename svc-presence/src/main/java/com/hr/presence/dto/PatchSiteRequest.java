package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record PatchSiteRequest(
		@Size(max = 255)
		@JsonProperty("libelle") String libelle,

		@JsonProperty("actif") Boolean actif,

		/** Changer le type sans rayon applique le rayon par défaut du nouveau type (D-P01). */
		@JsonProperty("type_lieu") String typeLieu,

		@JsonProperty("rayon_metres") Integer rayonMetres,

		/** PAPIER ou ECRAN (D-P05). */
		@JsonProperty("mode_qr") String modeQr
) {
}
