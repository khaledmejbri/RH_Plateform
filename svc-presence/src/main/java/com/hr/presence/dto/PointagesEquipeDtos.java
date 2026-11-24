package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de l'endpoint mobile {@code GET /api/rh/v1/mobile/presence/equipe/pointages}.
 */
public final class PointagesEquipeDtos {

	private PointagesEquipeDtos() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PointageEquipeItem(
			@JsonProperty("id") UUID id,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
			@JsonProperty("type") PointageType type,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("statut") PointageStatut statut,
			@JsonProperty("statut_libelle") String statutLibelle,
			@JsonProperty("server_ts") Instant serverTs,
			@JsonProperty("journee") LocalDate journee,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_code") String siteCode,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("manuel") boolean manuel,
			@JsonProperty("motif_rejet") String motifRejet
	) {
	}

	public record PointagesEquipePage(
			@JsonProperty("elements") List<PointageEquipeItem> elements,
			@JsonProperty("page") int page,
			@JsonProperty("size") int size,
			@JsonProperty("total_elements") long totalElements,
			@JsonProperty("total_pages") int totalPages,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("effectif_equipe") int effectifEquipe
	) {
	}
}
