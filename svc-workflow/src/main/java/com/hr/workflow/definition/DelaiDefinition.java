package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Délai d'une étape humaine (§ 9.3.1, § 9.3.2, D-W17, D-W18).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelaiDefinition(
		@JsonProperty("duree") Integer duree,
		@JsonProperty("unite") String unite,
		@JsonProperty("plafond_avant_variable") String plafondAvantVariable
) {
}
