package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Action à l'échéance d'une étape humaine (§ 9.3.1, § 9.3.2, D-W20).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EcheanceDefinition(
		@JsonProperty("action") String action,
		@JsonProperty("apres") ApresEcheance apres,
		@JsonProperty("intervenants") IntervenantsDefinition intervenants
) {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ApresEcheance(
			@JsonProperty("duree") Integer duree,
			@JsonProperty("unite") String unite
	) {
	}
}
