package com.hr.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.domain.OrigineDelegation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs des délégations (D-W21, D-W4-05) et de la réaffectation RH (D-W23, D-W4-08).
 */
public final class DelegationDtos {

	private DelegationDtos() {
	}

	public record CreerDelegationMoiRequest(
			@JsonProperty("delegue_id") UUID delegueId,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("objet_types") List<String> objetTypes,
			@Size(max = 300) @JsonProperty("motif") String motif
	) {
	}

	public record CreerDelegationAdminRequest(
			@JsonProperty("delegant_id") UUID delegantId,
			@JsonProperty("delegue_id") UUID delegueId,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("objet_types") List<String> objetTypes,
			@Size(max = 300) @JsonProperty("motif") String motif
	) {
	}

	public record DelegationResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("delegant_id") UUID delegantId,
			@JsonProperty("delegant_nom") String delegantNom,
			@JsonProperty("delegue_id") UUID delegueId,
			@JsonProperty("delegue_nom") String delegueNom,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("objet_types") List<String> objetTypes,
			@JsonProperty("motif") String motif,
			@JsonProperty("origine") OrigineDelegation origine,
			@JsonProperty("active") boolean active,
			@JsonProperty("creee_le") Instant creeeLe,
			@JsonProperty("annulee_le") Instant annuleeLe
	) {
	}

	public record ReaffecterTacheRequest(
			@NotEmpty @Size(min = 1, max = 10) @JsonProperty("collaborateur_ids") List<UUID> collaborateurIds,
			@JsonProperty("motif") String motif,
			@NotNull @JsonProperty("version") Long version
	) {
	}
}
