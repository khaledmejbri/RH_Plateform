package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.StatutHeuresSup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO des heures supplémentaires (D-P13 à D-P16) ; JSON en snake_case. */
public final class HeuresSupDtos {

	private HeuresSupDtos() {
	}

	/** Les contrôles de durée, de motif et de plage sont faits par le service (codes 400 dédiés). */
	public record CreationHeuresSupRequest(
			@JsonProperty("collaborateur_ids") @NotEmpty @Size(max = 50) List<UUID> collaborateurIds,
			@JsonProperty("site_id") @NotNull UUID siteId,
			@JsonProperty("date_debut") @NotNull LocalDate dateDebut,
			@JsonProperty("date_fin") LocalDate dateFin,
			@JsonProperty("ouverture_avant_minutes") Integer ouvertureAvantMinutes,
			@JsonProperty("ouverture_apres_minutes") Integer ouvertureApresMinutes,
			@JsonProperty("motif") String motif
	) {
	}

	public record RefusHeuresSupRequest(@JsonProperty("motif") String motif) {
	}

	public record AnnulationHeuresSupRequest(@JsonProperty("motif") String motif) {
	}

	public record BeneficiaireResponse(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom") String nom
	) {
	}

	public record DemandeHeuresSupResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("reference") String reference,
			@JsonProperty("statut") StatutHeuresSup statut,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("date_debut") LocalDate dateDebut,
			@JsonProperty("date_fin") LocalDate dateFin,
			/** Jours ouverts restants de la plage (vide quand la demande n'est plus EN_ATTENTE / VALIDEE). */
			@JsonProperty("jours") List<LocalDate> jours,
			@JsonProperty("ouverture_avant_minutes") int ouvertureAvantMinutes,
			@JsonProperty("ouverture_apres_minutes") int ouvertureApresMinutes,
			@JsonProperty("debut_plage_ts") Instant debutPlageTs,
			@JsonProperty("motif") String motif,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("beneficiaires") List<BeneficiaireResponse> beneficiaires,
			@JsonProperty("creee_validee") boolean creeeValidee,
			@JsonProperty("decide_par") String decidePar,
			@JsonProperty("decide_le") Instant decideLe,
			@JsonProperty("motif_refus") String motifRefus,
			@JsonProperty("annule_par") String annulePar,
			@JsonProperty("annule_le") Instant annuleLe,
			@JsonProperty("motif_annulation") String motifAnnulation,
			@JsonProperty("cree_le") Instant creeLe
	) {
	}

	/** Une ligne de comptage : bénéficiaire x site x journée (D-P14, D-P16.a). */
	public record HeuresComptees(
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom") String nom,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("journee") LocalDate journee,
			@JsonProperty("horaire_debut") String horaireDebut,
			@JsonProperty("horaire_fin") String horaireFin,
			@JsonProperty("plage_avant") String plageAvant,
			@JsonProperty("plage_apres") String plageApres,
			@JsonProperty("entree") Instant entree,
			@JsonProperty("sortie") Instant sortie,
			@JsonProperty("minutes_comptees") long minutesComptees,
			@JsonProperty("heures_comptees") String heuresComptees,
			@JsonProperty("reference_demande") String referenceDemande,
			@JsonProperty("etat") EtatJourHeuresSup etat
	) {
	}

	public record BeneficiairePossibleResponse(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom") String nom
	) {
	}

	/** Heures sup validées à venir de l'appelant. */
	public record HeuresSupAVenir(
			@JsonProperty("reference") String reference,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("journee") LocalDate journee,
			@JsonProperty("ouverture_avant_minutes") int ouvertureAvantMinutes,
			@JsonProperty("ouverture_apres_minutes") int ouvertureApresMinutes
	) {
	}

	public record MesHeuresSupResponse(
			@JsonProperty("a_venir") List<HeuresSupAVenir> aVenir,
			@JsonProperty("comptees") List<HeuresComptees> comptees
	) {
	}

	public record FenetreDuJourRequest(@JsonProperty("qr_token") @NotBlank String qrToken) {
	}

	public record HeureSupDuJour(
			@JsonProperty("reference") String reference,
			@JsonProperty("ouverture_avant_minutes") int ouvertureAvantMinutes,
			@JsonProperty("ouverture_apres_minutes") int ouvertureApresMinutes
	) {
	}

	public record FenetreDuJourResponse(
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("avec_horaire") boolean avecHoraire,
			@JsonProperty("journee") LocalDate journee,
			@JsonProperty("ferme") boolean ferme,
			@JsonProperty("horaire_debut") String horaireDebut,
			@JsonProperty("horaire_fin") String horaireFin,
			@JsonProperty("ouverture_debut") String ouvertureDebut,
			@JsonProperty("ouverture_fin") String ouvertureFin,
			@JsonProperty("heures_sup") List<HeureSupDuJour> heuresSup
	) {
	}

	public record SiteMobileResponse(
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("avec_horaire") boolean avecHoraire
	) {
	}
}
