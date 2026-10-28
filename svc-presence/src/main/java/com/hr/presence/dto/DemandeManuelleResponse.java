package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeManuelleResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("statut") StatutDemandeManuelle statut,
		@JsonProperty("type") PointageType type,
		@JsonProperty("heure_declaree") Instant heureDeclaree,
		@JsonProperty("justification") String justification,
		@JsonProperty("collaborateur_id") String collaborateurId,
		@JsonProperty("collaborateur_nom") String collaborateurNom,
		@JsonProperty("site_id") UUID siteId,
		@JsonProperty("pointage_rejete_id") UUID pointageRejeteId,
		@JsonProperty("motif_rejet_initial") String motifRejetInitial,
		/** Null = validation par les RH (aucun responsable selon D-H03). */
		@JsonProperty("valideur_nom") String valideurNom,
		@JsonProperty("validation_par_rh") boolean validationParRh,
		@JsonProperty("decide_par") String decidePar,
		@JsonProperty("decide_le") Instant decideLe,
		@JsonProperty("motif_refus") String motifRefus,
		@JsonProperty("pointage_id") UUID pointageId,
		@JsonProperty("cree_le") Instant creeLe,
		/** Vue RH : pointages manuels validés du collaborateur dans le mois de la demande. */
		@JsonProperty("manuels_du_mois") Long manuelsDuMois,
		@JsonProperty("alerte_seuil") Boolean alerteSeuil
) {
}
