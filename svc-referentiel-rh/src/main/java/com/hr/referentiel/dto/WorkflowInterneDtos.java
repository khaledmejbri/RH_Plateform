package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.BaseDecompteConge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrats internes de {@code svc-referentiel-rh} pour {@code svc-workflow} (§ 9.5.3).
 */
public final class WorkflowInterneDtos {

	private WorkflowInterneDtos() {
	}

	public record ResolutionLotRequest(
			@JsonProperty("date") LocalDate date,
			@JsonProperty("demandeur_id") UUID demandeurId,
			@JsonProperty("beneficiaires") List<UUID> beneficiaires,
			@JsonProperty("exclure") List<UUID> exclure,
			@NotEmpty @Size(max = 50) @Valid @JsonProperty("regles") List<RegleResolutionRequest> regles
	) {
	}

	public record RegleResolutionRequest(
			@NotBlank @JsonProperty("cle") String cle,
			@NotBlank @JsonProperty("regle") String regle,
			@Valid @JsonProperty("params") ParamsRegleResolution params
	) {
	}

	public record ParamsRegleResolution(
			@JsonProperty("niveau") Integer niveau,
			@JsonProperty("type_noeud") String typeNoeud,
			@JsonProperty("roles") List<String> roles,
			@JsonProperty("fonction_code") String fonctionCode,
			@JsonProperty("portee") String portee,
			@JsonProperty("collaborateur_id") UUID collaborateurId
	) {
		public static final ParamsRegleResolution VIDE = new ParamsRegleResolution(null, null, null, null, null, null);
	}

	public record ContexteDemandeur(
			@JsonProperty("profil_acces") String profilAcces,
			@JsonProperty("type_noeud") String typeNoeud,
			@JsonProperty("anciennete_mois") int ancienneteMois,
			@JsonProperty("est_manager") boolean estManager
	) {
	}

	public record CollaborateurResumeInterne(
			@JsonProperty("id") UUID id,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("statut") String statut,
			@JsonProperty("unite_id") UUID uniteId,
			@JsonProperty("unite_code") String uniteCode,
			@JsonProperty("unite_type") String uniteType,
			@JsonProperty("famille_metier_code") String familleMetierCode,
			@JsonProperty("niveau_seniorite") String niveauSeniorite
	) {
	}

	public record IntervenantResolu(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("intitule") String intitule
	) {
	}

	public record ResultatResolution(
			@JsonProperty("candidats") List<IntervenantResolu> candidats,
			@JsonProperty("raison_vide") String raisonVide
	) {
	}

	public record ResolutionLotResponse(
			@JsonProperty("resultats") Map<String, ResultatResolution> resultats,
			@JsonProperty("contexte_demandeur") ContexteDemandeur contexteDemandeur
	) {
	}

	public record CalendrierSocieteInterneResponse(
			@JsonProperty("societe_id") UUID societeId,
			@JsonProperty("fuseau") String fuseau,
			@JsonProperty("jours_travailles") List<String> joursTravailles,
			@JsonProperty("duree_hebdo_minutes") int dureeHebdoMinutes,
			@JsonProperty("duree_jour_minutes") Map<String, Integer> dureeJourMinutes,
			@JsonProperty("heure_debut_journee") String heureDebutJournee,
			@JsonProperty("heure_fin_journee") String heureFinJournee,
			@JsonProperty("base_decompte_conge") BaseDecompteConge baseDecompteConge,
			@JsonProperty("delegation_auto_conges") boolean delegationAutoConges,
			@JsonProperty("publication_quatre_yeux") boolean publicationQuatreYeux,
			@JsonProperty("jours_feries") List<LocalDate> joursFeries
	) {
	}

	public record AbsenceCollaborateurInterne(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("date_debut") LocalDate dateDebut,
			@JsonProperty("date_fin") LocalDate dateFin,
			@JsonProperty("type_conge") String typeConge
	) {
	}
}
