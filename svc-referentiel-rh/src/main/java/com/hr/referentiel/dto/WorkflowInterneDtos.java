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
			@JsonProperty("demandeur_collaborateur_id") UUID demandeurCollaborateurId,
			@JsonProperty("beneficiaire_collaborateur_ids") List<UUID> beneficiaireCollaborateurIds,
			@JsonProperty("date_reference") LocalDate dateReference,
			@NotEmpty @Size(max = 50) @Valid @JsonProperty("regles") List<RegleResolutionRequest> regles
	) {
	}

	public record RegleResolutionRequest(
			@NotBlank @JsonProperty("cle") String cle,
			@NotBlank @JsonProperty("type") String type,
			@JsonProperty("niveau") Integer niveau,
			@JsonProperty("type_unite") String typeUnite,
			@JsonProperty("role") String role,
			@JsonProperty("fonction_code") String fonctionCode,
			@JsonProperty("perimetre") String perimetre,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("exclure") List<String> exclure
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
			@JsonProperty("matricule") String matricule
	) {
	}

	public record ResultatResolution(
			@JsonProperty("cle") String cle,
			@JsonProperty("intervenants") List<IntervenantResolu> intervenants,
			@JsonProperty("raison_vide") String raisonVide
	) {
	}

	public record ResolutionLotResponse(
			@JsonProperty("contexte_demandeur") CollaborateurResumeInterne contexteDemandeur,
			@JsonProperty("resultats") List<ResultatResolution> resultats
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
