package com.hr.identiteacces.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record SocieteEvenementMessage(
		@JsonProperty("evenement_id") UUID evenementId,
		@JsonProperty("evenement") String evenement,
		@JsonProperty("societe_id") UUID societeId,
		@JsonProperty("code") String code,
		@JsonProperty("emis_le") Instant emisLe
) {
	public static final String SOCIETE_CREEE = "SOCIETE_CREEE";
	public static final String SOCIETE_SUSPENDUE = "SOCIETE_SUSPENDUE";
	public static final String SOCIETE_REACTIVEE = "SOCIETE_REACTIVEE";
}
