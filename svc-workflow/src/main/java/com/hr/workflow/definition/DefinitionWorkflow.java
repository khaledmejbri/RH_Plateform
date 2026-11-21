package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Définition d'une version de workflow — format 1 (§ 9.3.1, § 9.3.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DefinitionWorkflow(
		@JsonProperty("format") Integer format,
		@JsonProperty("objet_type") String objetType,
		@JsonProperty("options") OptionsWorkflow options,
		@JsonProperty("etapes") List<EtapeDefinition> etapes
) {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OptionsWorkflow(
			@JsonProperty("separation_des_taches") Boolean separationDesTaches
	) {
		public boolean estSeparationDesTaches() {
			return Boolean.TRUE.equals(separationDesTaches);
		}
	}

	public boolean separationDesTaches() {
		return options != null && options.estSeparationDesTaches();
	}
}
