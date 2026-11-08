package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.dto.PresenceCollaborateurDtos.PersonneResume;

import java.time.Instant;

/**
 * Détail d'un pointage manuel (D-N01.b, ADR lot 2 C4 / C5) : tous les champs de {@link DemandeManuelleResponse} (à plat
 * dans le JSON) plus site, unité, valideur, pointage refusé d'origine et seuil mensuel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeManuelleDetail(
		@JsonUnwrapped DemandeManuelleResponse demande,
		@JsonProperty("site_libelle") String siteLibelle,
		/** Unité du demandeur (service, à défaut département ou direction). */
		@JsonProperty("unite_libelle") String uniteLibelle,
		/** Nul = validation par les RH (aucun responsable selon D-H03). */
		@JsonProperty("valideur") PersonneResume valideur,
		@JsonProperty("pointage_rejete") PointageRejeteResume pointageRejete,
		/** Seuil mensuel d'alerte RH (D-P04) ; le compteur {@code manuels_du_mois} est dans {@code demande}. */
		@JsonProperty("seuil_mois") Integer seuilMois) {

	/** Refus d'origine : statut et motif en clair, distance, instant. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PointageRejeteResume(
			@JsonProperty("statut") PointageStatut statut,
			@JsonProperty("statut_libelle") String statutLibelle,
			@JsonProperty("motif_rejet") String motifRejet,
			@JsonProperty("distance_metres") Double distanceMetres,
			@JsonProperty("server_ts") Instant serverTs) {
	}
}
