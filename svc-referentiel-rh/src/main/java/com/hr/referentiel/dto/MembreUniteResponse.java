package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Identifiants (fiche et compte) d'un collaborateur rattaché à une unité ou à l'une de ses sous-unités. */
public record MembreUniteResponse(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("compte_id") UUID compteId
) {
}
