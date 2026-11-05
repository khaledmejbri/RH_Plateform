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
		@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua,
		/** Journée de rattachement (D-P11.a), nulle pour un site sans horaire. */
		@JsonProperty("journee") java.time.LocalDate journee,
		/** Identité lisible, renseignée par la liste admin seulement (nulle sinon ou si introuvable). */
		@JsonProperty("collaborateur_nom") String collaborateurNom,
		@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
		@JsonProperty("site_code") String siteCode,
		@JsonProperty("site_libelle") String siteLibelle
) {

	/** Réponse sans identité lisible (mobile, historique) : les quatre champs ajoutés restent nuls. */
	public PointageResponse(
			UUID id, PointageStatut statut, PointageType type, Instant serverTs, UUID siteId,
			Double distanceMetres, Double accuracyMetres, String motifRejet, String collaborateurId,
			VerificationFaciale verificationFaciale, Integer scoreFacial, Boolean vivaciteReussie,
			Integer echecsConsecutifs, Instant bloqueJusqua, java.time.LocalDate journee) {
		this(id, statut, type, serverTs, siteId, distanceMetres, accuracyMetres, motifRejet, collaborateurId,
				verificationFaciale, scoreFacial, vivaciteReussie, echecsConsecutifs, bloqueJusqua, journee,
				null, null, null, null);
	}

	/** Copie enrichie du nom et du matricule du collaborateur, du code et du libellé du site. */
	public PointageResponse avecIdentite(String nom, String matricule, String codeSite, String libelleSite) {
		return new PointageResponse(id, statut, type, serverTs, siteId, distanceMetres, accuracyMetres, motifRejet,
				collaborateurId, verificationFaciale, scoreFacial, vivaciteReussie, echecsConsecutifs, bloqueJusqua,
				journee, nom, matricule, codeSite, libelleSite);
	}
}
