package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Règle d'intervenant d'une étape ou d'une tâche (§ 9.3.1, § 9.3.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntervenantsDefinition(
		@JsonProperty("regle") String regle,
		@JsonProperty("params") Map<String, Object> params,
		@JsonProperty("mode") String mode
) {
	public String modeEffectif() {
		return mode != null && !mode.isBlank() ? mode : "UN_SEUL";
	}
}
