package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.TypeLieu;

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
		@JsonProperty("type_lieu") TypeLieu typeLieu,
		@JsonProperty("mode_qr") ModeQr modeQr,
		@JsonProperty("borne_active") boolean borneActive,
		@JsonProperty("borne_cree_le") Instant borneCreeLe,
		@JsonProperty("precision_ancrage_metres") Double precisionAncrageMetres,
		@JsonProperty("nb_mesures_ancrage") Integer nbMesuresAncrage,
		@JsonProperty("emplacement_updated_at") Instant emplacementUpdatedAt,
		@JsonProperty("emplacement_updated_by") String emplacementUpdatedBy,
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
