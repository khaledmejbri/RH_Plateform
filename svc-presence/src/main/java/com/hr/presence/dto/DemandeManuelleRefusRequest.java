package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemandeManuelleRefusRequest(
		@NotBlank
		@Size(max = 500)
		@JsonProperty("motif") String motif
) {
}
