package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** D-N02 : fiche d'un membre de l'équipe pour son valideur D-H03 : annuaire + demandes M01 dont l'appelant est ou a été le valideur. */
public record FicheEquipeResponse(
		@JsonProperty("annuaire") FicheAnnuaireResponse annuaire,
		@JsonProperty("demandes") List<DemandeEquipe> demandes
) {

	public record DemandeEquipe(
			@JsonProperty("identifiant") UUID identifiant,
			@JsonProperty("numero") String numero,
			@JsonProperty("type_demande") String typeDemande,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("statut") String statut,
			@JsonProperty("statut_libelle") String statutLibelle,
			@JsonProperty("periode_debut") LocalDate periodeDebut,
			@JsonProperty("periode_fin") LocalDate periodeFin,
			@JsonProperty("cree_le") Instant creeLe) {
	}
}
