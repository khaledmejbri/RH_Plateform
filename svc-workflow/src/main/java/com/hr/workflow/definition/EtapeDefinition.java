package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Définition d'une étape dans un workflow (§ 9.3.1, § 9.3.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapeDefinition(
		@JsonProperty("code") String code,
		@JsonProperty("libelle") String libelle,
		@JsonProperty("type") String type,
		@JsonProperty("condition") Condition condition,
		@JsonProperty("intervenants") IntervenantsDefinition intervenants,
		@JsonProperty("repli_si_vide") RepliSiVideDefinition repliSiVide,
		@JsonProperty("taches") List<TacheParalleleDefinition> taches,
		@JsonProperty("public") List<String> publicDestinataires,
		@JsonProperty("actions") List<String> actions,
		@JsonProperty("controle_avant_approbation") Boolean controleAvantApprobation,
		@JsonProperty("commentaire_obligatoire_si") Condition commentaireObligatoireSi,
		@JsonProperty("delai") DelaiDefinition delai,
		@JsonProperty("relances") RelancesDefinition relances,
		@JsonProperty("a_echeance") EcheanceDefinition aEcheance,
		@JsonProperty("si_approuve") TransitionDefinition siApprouve,
		@JsonProperty("si_refuse") TransitionDefinition siRefuse,
		@JsonProperty("notifications") NotificationsEtapeDefinition notifications
) {
	public boolean estHumaine() {
		return "VALIDATION".equals(type) || "TACHE".equals(type) || "TACHES_PARALLELES".equals(type);
	}

	public boolean controleAvantApprobationActif() {
		return Boolean.TRUE.equals(controleAvantApprobation);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RepliSiVideDefinition(
			@JsonProperty("action") String action,
			@JsonProperty("roles") List<String> roles
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TacheParalleleDefinition(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("intervenants") IntervenantsDefinition intervenants,
			@JsonProperty("repli_si_vide") RepliSiVideDefinition repliSiVide,
			@JsonProperty("obligatoire") Boolean obligatoire
	) {
		public boolean estObligatoire() {
			return !Boolean.FALSE.equals(obligatoire);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RelancesDefinition(
			@JsonProperty("nombre") Integer nombre,
			@JsonProperty("avant_echeance_heures") Integer avantEcheanceHeures,
			@JsonProperty("intervalle_heures") Integer intervalleHeures
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TransitionDefinition(
			@JsonProperty("aller_a") String allerA
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record NotificationsEtapeDefinition(
			@JsonProperty("a_l_activation") List<String> aLActivation,
			@JsonProperty("a_la_fin") List<String> aLaFin,
			@JsonProperty("canal") String canal
	) {
	}
}
