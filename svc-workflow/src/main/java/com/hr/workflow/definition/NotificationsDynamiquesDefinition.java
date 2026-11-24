package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Configuration des notifications dynamiques de pointage dans une définition de workflow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationsDynamiquesDefinition(
		@JsonProperty("notifier_ro") Boolean notifierRo,
		@JsonProperty("portee_sites") String porteeSites,
		@JsonProperty("site_ids") List<String> siteIds,
		@JsonProperty("types_pointage") List<String> typesPointage,
		@JsonProperty("canal") String canal
) {
	public static NotificationsDynamiquesDefinition parDefautPointage() {
		return new NotificationsDynamiquesDefinition(
				false,
				"TOUTE_LA_SOCIETE",
				List.of(),
				List.of("ENTREE", "SORTIE"),
				"WEBSOCKET"
		);
	}
}
