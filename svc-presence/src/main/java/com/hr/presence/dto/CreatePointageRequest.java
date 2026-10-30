package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PlateformeMobile;
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
		@JsonProperty("position_simulee") Boolean positionSimulee,

		/**
		 * Score facial 0-100 calculé sur l'appareil (D-P08.b). Contrôlé par le service (400 PREUVE_FACIALE_INVALIDE),
		 * pas par le bean : une valeur hors plage n'est pas un rejet 422 mais une requête mal formée.
		 */
		@JsonProperty("score_facial") Integer scoreFacial,

		/** Résultat du contrôle du vivant (D-P09). */
		@JsonProperty("vivacite_reussie") Boolean vivaciteReussie,

		@Size(max = 32)
		@JsonProperty("modele_facial_version") String modeleFacialVersion,

		@JsonProperty("plateforme") PlateformeMobile plateforme
) {

	/** Requête sans preuve faciale (appli sans vérification faciale, réglage INACTIVE). */
	public CreatePointageRequest(
			String qrToken, String type, Double latitude, Double longitude, Double accuracyMetres,
			Instant clientTimestamp, String deviceId, String idempotencyKey, Boolean positionSimulee) {
		this(qrToken, type, latitude, longitude, accuracyMetres, clientTimestamp, deviceId, idempotencyKey,
				positionSimulee, null, null, null, null);
	}
}
