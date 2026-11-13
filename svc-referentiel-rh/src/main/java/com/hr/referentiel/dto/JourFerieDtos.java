package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

/** Contrat JSON (snake_case) des jours fériés (ADR soldes, annexe A, P4 à P10). */
public final class JourFerieDtos {

	private JourFerieDtos() {
	}

	/** Création ({@code version} ignorée) et modification ({@code date} et {@code libelle} facultatifs). */
	public record JourFerieRequest(
			@JsonProperty("date") LocalDate date,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("version") Long version) {
	}

	public record VersionJourFerieRequest(@JsonProperty("version") Long version) {
	}

	public record JourFerieResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("date") LocalDate date,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("version") long version) {
	}

	public record JourFerieActif(
			@JsonProperty("date") LocalDate date,
			@JsonProperty("libelle") String libelle) {
	}
}
