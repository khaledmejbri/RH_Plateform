package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendrier de présence pour l'export Temps de travail de {@code svc-presence} : jours travaillés de la société,
 * jours fériés actifs sur la période et congés approuvés des collaborateurs demandés.
 */
public record CalendrierPresenceResponse(
		@JsonProperty("jours_travailles") List<String> joursTravailles,
		@JsonProperty("jours_feries") List<LocalDate> joursFeries,
		@JsonProperty("conges") List<CongeApprouveItem> conges
) {

	public record CongeApprouveItem(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("date_debut") LocalDate dateDebut,
			@JsonProperty("date_fin") LocalDate dateFin,
			@JsonProperty("type_conge") String typeConge,
			@JsonProperty("periode_premier_jour") String periodePremierJour,
			@JsonProperty("periode_dernier_jour") String periodeDernierJour
	) {
	}
}
