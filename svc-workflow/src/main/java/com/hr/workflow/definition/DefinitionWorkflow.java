package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
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
		@JsonProperty("etapes") List<EtapeDefinition> etapes,
		@JsonProperty("notifications_dynamiques") NotificationsDynamiquesDefinition notificationsDynamiques
) {
	@JsonCreator
	public DefinitionWorkflow {
	}

	/** Constructeur de compatibilité à 4 arguments pour les 7 types transactionnels et tests existants. */
	public DefinitionWorkflow(Integer format, String objetType, OptionsWorkflow options, List<EtapeDefinition> etapes) {
		this(format, objetType, options, etapes, null);
	}

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
