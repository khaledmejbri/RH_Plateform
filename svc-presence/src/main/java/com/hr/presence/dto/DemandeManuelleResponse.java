package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeManuelleResponse(
		@JsonProperty("id") UUID id,
		/** Numéro lisible PM-AAAAMM-NNNN (D-L02). */
		@JsonProperty("numero") String numero,
		@JsonProperty("statut") StatutDemandeManuelle statut,
		@JsonProperty("type") PointageType type,
		@JsonProperty("heure_declaree") Instant heureDeclaree,
		@JsonProperty("justification") String justification,
		@JsonProperty("collaborateur_id") String collaborateurId,
		@JsonProperty("collaborateur_nom") String collaborateurNom,
		@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
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
		@JsonProperty("alerte_seuil") Boolean alerteSeuil,
		// ---- Sécurisation du pointage manuel (D-P21 à D-P26, ADR annexe A) ----
		@JsonProperty("statut_libelle") String statutLibelle,
		@JsonProperty("refus_statut") String refusStatut,
		@JsonProperty("refus_statut_libelle") String refusStatutLibelle,
		@JsonProperty("distance_metres") Double distanceMetres,
		@JsonProperty("precision_metres") Double precisionMetres,
		@JsonProperty("rayon_metres") Integer rayonMetres,
		@JsonProperty("mode_qr") String modeQr,
		@JsonProperty("mode_qr_libelle") String modeQrLibelle,
		/** Minutes entières, tronquées : création moins tentative. */
		@JsonProperty("delai_demande_minutes") Long delaiDemandeMinutes,
		@JsonProperty("echeance") Instant echeance,
		/** Minutes entières, tronquées : décision moins tentative. */
		@JsonProperty("delai_decision_minutes") Long delaiDecisionMinutes,
		// Réservés à qui décide et au back-office : absents pour le demandeur (D-P24.d)
		@JsonProperty("plafond_metres") Integer plafondMetres,
		@JsonProperty("reprise") Boolean reprise,
		@JsonProperty("avec_signal") Boolean avecSignal,
		@JsonProperty("signaux") List<Signal> signaux,
		@JsonProperty("seuil_mois") Integer seuilMois,
		/** Refus de pointage du jour (Tunis) de la tentative : compteur D-P20, calculé à la lecture. */
		@JsonProperty("refus_du_jour") Long refusDuJour,
		@JsonProperty("commentaire_valideur") String commentaireValideur
) {

	/** Forme historique (avant D-P21 à D-P26) : les champs de sécurisation restent nuls. */
	public DemandeManuelleResponse(UUID id, String numero, StatutDemandeManuelle statut, PointageType type,
			Instant heureDeclaree, String justification, String collaborateurId, String collaborateurNom,
			String collaborateurMatricule, UUID siteId, UUID pointageRejeteId, String motifRejetInitial,
			String valideurNom, boolean validationParRh, String decidePar, Instant decideLe, String motifRefus,
			UUID pointageId, Instant creeLe, Long manuelsDuMois, Boolean alerteSeuil) {
		this(id, numero, statut, type, heureDeclaree, justification, collaborateurId, collaborateurNom,
				collaborateurMatricule, siteId, pointageRejeteId, motifRejetInitial, valideurNom, validationParRh,
				decidePar, decideLe, motifRefus, pointageId, creeLe, manuelsDuMois, alerteSeuil,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	/** Signal de vigilance : code stable et libellé rédigé par le serveur. */
	public record Signal(@JsonProperty("code") String code, @JsonProperty("libelle") String libelle) {
	}
}
