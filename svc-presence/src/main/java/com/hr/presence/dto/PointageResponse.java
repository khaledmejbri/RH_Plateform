package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PointageResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("statut") PointageStatut statut,
		@JsonProperty("type") PointageType type,
		@JsonProperty("server_ts") Instant serverTs,
		@JsonProperty("site_id") UUID siteId,
		@JsonProperty("distance_metres") Double distanceMetres,
		@JsonProperty("accuracy_metres") Double accuracyMetres,
		@JsonProperty("motif_rejet") String motifRejet,
		@JsonProperty("collaborateur_id") String collaborateurId
) {
}
