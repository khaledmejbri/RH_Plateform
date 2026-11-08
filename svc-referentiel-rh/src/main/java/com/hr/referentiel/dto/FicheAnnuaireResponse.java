package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** D-N02 : fiche annuaire d'un collègue. Huit clés, rien d'autre : ni matricule, ni statut, ni profil, ni date de recrutement. */
public record FicheAnnuaireResponse(
		@JsonProperty("collaborateur_id") UUID collaborateurId,
		@JsonProperty("nom_complet") String nomComplet,
		@JsonProperty("poste_libelle") String posteLibelle,
		@JsonProperty("intitule_role") String intituleRole,
		@JsonProperty("unite_id") UUID uniteId,
		@JsonProperty("unite_libelle") String uniteLibelle,
		@JsonProperty("responsable") Responsable responsable,
		@JsonProperty("courriel_professionnel") String courrielProfessionnel
) {

	public record Responsable(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("nom_complet") String nomComplet) {
	}
}
