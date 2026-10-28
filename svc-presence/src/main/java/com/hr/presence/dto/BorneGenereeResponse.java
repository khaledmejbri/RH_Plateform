package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Jeton de borne montré une seule fois au RH (D-P05). */
public record BorneGenereeResponse(
		@JsonProperty("site_id") UUID siteId,
		@JsonProperty("jeton") String jeton,
		/** Chemin de la page plein écran dans la Plateforme RH. */
		@JsonProperty("chemin") String chemin
) {
}
