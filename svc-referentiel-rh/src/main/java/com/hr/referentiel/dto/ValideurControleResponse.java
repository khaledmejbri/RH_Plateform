package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Réponse interne : le compte appelant est-il le valideur D-H03 du collaborateur ? Échec fermé. */
public record ValideurControleResponse(
		@JsonProperty("est_valideur") boolean estValideur,
		@JsonProperty("collaborateur_actif") boolean collaborateurActif,
		@JsonProperty("valideur_fiche_id") UUID valideurFicheId
) {
}
