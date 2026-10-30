package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.VerificationFaciale;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PointageResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("statut") PointageStatut statut,
		@JsonProperty("type") PointageType type,
		@JsonProperty("server_ts") Instant serverTs,
		@JsonProperty("site_id") UUID siteId,
		@JsonProperty("distance_metres") Double distanceMetres,
		@JsonProperty("accuracy_metres") Double accuracyMetres,
		@JsonProperty("motif_rejet") String motifRejet,
		@JsonProperty("collaborateur_id") String collaborateurId,
		/** Vérification faciale (D-P08, D-P10) : tous nuls quand aucun contrôle n'a eu lieu. */
		@JsonProperty("verification_faciale") VerificationFaciale verificationFaciale,
		@JsonProperty("score_facial") Integer scoreFacial,
		@JsonProperty("vivacite_reussie") Boolean vivaciteReussie,
		@JsonProperty("echecs_consecutifs") Integer echecsConsecutifs,
		@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua
) {
}
