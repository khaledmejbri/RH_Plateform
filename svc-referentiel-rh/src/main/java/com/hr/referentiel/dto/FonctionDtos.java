package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FonctionDtos {

	private FonctionDtos() {
	}

	public record FonctionRequest(
			@NotBlank @Size(min = 2, max = 80) @JsonProperty("libelle") String libelle,
			@Size(max = 300) @JsonProperty("description") String description,
			@JsonProperty("version") Long version
	) {
	}

	public record FonctionResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("utilisee") boolean utilisee,
			@JsonProperty("version") Long version,
			@JsonProperty("cree_le") Instant creeLe,
			@JsonProperty("modifie_le") Instant modifieLe
	) {
	}

	public record AffectationRequest(
			@NotNull @JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("unite_perimetre_id") UUID unitePerimetreId,
			@NotNull @JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au
	) {
	}

	public record AffectationResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("fonction_id") UUID fonctionId,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
			@JsonProperty("unite_perimetre_id") UUID unitePerimetreId,
			@JsonProperty("unite_perimetre_nom") String unitePerimetreNom,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("cree_le") Instant creeLe
	) {
	}
}
