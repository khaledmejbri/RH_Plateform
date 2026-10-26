package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiteResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("code") String code,
		@JsonProperty("libelle") String libelle,
		@JsonProperty("latitude") Double latitude,
		@JsonProperty("longitude") Double longitude,
		@JsonProperty("rayon_metres") int rayonMetres,
		@JsonProperty("actif") boolean actif,
		@JsonProperty("qr_actif") boolean qrActif,
		@JsonProperty("qr_version") Integer qrVersion,
		/** Début de validité du QR actif (SCRUM-16 : affiché « valide du … au … »). */
		@JsonProperty("valid_from") Instant validFrom,
		@JsonProperty("valid_until") Instant validUntil,
		@JsonProperty("expire_bientot") boolean expireBientot,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt
) {
}
