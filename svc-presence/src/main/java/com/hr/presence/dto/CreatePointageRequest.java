package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreatePointageRequest(
		@NotBlank
		@JsonProperty("qr_token") String qrToken,

		@NotBlank
		@JsonProperty("type") String type,

		@NotNull
		@DecimalMin(value = "-90.0")
		@DecimalMax(value = "90.0")
		@JsonProperty("latitude") Double latitude,

		@NotNull
		@DecimalMin(value = "-180.0")
		@DecimalMax(value = "180.0")
		@JsonProperty("longitude") Double longitude,

		@JsonProperty("accuracy_metres") Double accuracyMetres,

		@JsonProperty("client_timestamp") Instant clientTimestamp,

		@Size(max = 128)
		@JsonProperty("device_id") String deviceId,

		@Size(max = 64)
		@JsonProperty("idempotency_key") String idempotencyKey,
		/** Indicateur OS « position simulée » (Android isMock, iOS isSimulatedBySoftware) — D-P01. */
		@JsonProperty("position_simulee") Boolean positionSimulee
) {
}
