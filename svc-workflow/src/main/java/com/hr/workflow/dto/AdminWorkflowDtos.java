package com.hr.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.ErreurDefinition;
import com.hr.workflow.domain.ActionJournalModele;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.StatutVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs des endpoints d'administration du moteur (§ 9.7.2).
 */
public final class AdminWorkflowDtos {

	private AdminWorkflowDtos() {
	}

	public record PageResponse<T>(
			@JsonProperty("elements") List<T> elements,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total
	) {
	}

	public record ActivationResponse(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("mode") ModeActivation mode,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("version") Long version
	) {
	}

	public record ModifierActivationRequest(
			@NotNull @JsonProperty("mode") ModeActivation mode,
			@NotNull @JsonProperty("version") Long version
	) {
	}

	public record CreerModeleRequest(
			@NotBlank @Size(min = 2, max = 40) @JsonProperty("code") String code,
			@NotBlank @Size(min = 2, max = 80) @JsonProperty("libelle") String libelle,
			@Size(max = 300) @JsonProperty("description") String description,
			@NotBlank @Size(max = 40) @JsonProperty("objet_type") String objetType,
			@Size(max = 40) @JsonProperty("sous_type") String sousType
	) {
	}

	public record ModifierModeleRequest(
			@NotBlank @Size(min = 2, max = 80) @JsonProperty("libelle") String libelle,
			@Size(max = 300) @JsonProperty("description") String description,
			@NotNull @JsonProperty("version") Long version
	) {
	}

	public record ActionVersionRequest(
			@NotNull @JsonProperty("version") Long version
	) {
	}

	public record ModeleResumeResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("sous_type") String sousType,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("version_publiee_numero") Integer versionPublieeNumero,
			@JsonProperty("brouillon_numero") Integer brouillonNumero,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("version") Long version
	) {
	}

	public record ModeleDetailResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("sous_type") String sousType,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("version_publiee_numero") Integer versionPublieeNumero,
			@JsonProperty("brouillon_numero") Integer brouillonNumero,
			@JsonProperty("cree_le") Instant creeLe,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("version") Long version,
			@JsonProperty("brouillon_id") UUID brouillonId
	) {
	}

	public record VersionResumeResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("numero") int numero,
			@JsonProperty("statut") StatutVersion statut,
			@JsonProperty("empreinte") String empreinte,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("modifie_par_nom") String modifieParNom,
			@JsonProperty("publie_le") Instant publieLe,
			@JsonProperty("retire_le") Instant retireLe,
			@JsonProperty("version") Long version
	) {
	}

	public record VersionDetailResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("modele_id") UUID modeleId,
			@JsonProperty("numero") int numero,
			@JsonProperty("statut") StatutVersion statut,
			@JsonProperty("definition") DefinitionWorkflow definition,
			@JsonProperty("empreinte") String empreinte,
			@JsonProperty("version") Long version,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("modifie_par_nom") String modifieParNom,
			@JsonProperty("publie_le") Instant publieLe,
			@JsonProperty("erreurs") List<ErreurDefinition> erreurs
	) {
	}

	public record ModifierVersionRequest(
			@NotNull @JsonProperty("definition") JsonNode definition,
			@NotNull @JsonProperty("version") Long version
	) {
	}

	public record ValidationResponse(
			@JsonProperty("valide") boolean valide,
			@JsonProperty("erreurs") List<ErreurDefinition> erreurs
	) {
	}

	public record SimulerVersionRequest(
			@NotNull @JsonProperty("demandeur_id") UUID demandeurId,
			@JsonProperty("beneficiaires") List<UUID> beneficiaires,
			@JsonProperty("variables") Map<String, Object> variables
	) {
	}

	public record CandidatSimule(
			@JsonProperty("nom") String nom,
			@JsonProperty("intitule") String intitule,
			@JsonProperty("origine") String origine
	) {
	}

	public record EtapeSimulee(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("statut") String statut,
			@JsonProperty("raison") String raison,
			@JsonProperty("candidats") List<CandidatSimule> candidats,
			@JsonProperty("echeance_simulee") Instant echeanceSimulee
	) {
	}

	public record SimulationResponse(
			@JsonProperty("etapes") List<EtapeSimulee> etapes
	) {
	}

	public record DiffEtape(
			@JsonProperty("code") String code,
			@JsonProperty("type_changement") String typeChangement,
			@JsonProperty("champs_modifies") List<String> champsModifies
	) {
	}

	public record ComparaisonVersionsResponse(
			@JsonProperty("version_source_id") UUID versionSourceId,
			@JsonProperty("version_cible_id") UUID versionCibleId,
			@JsonProperty("etapes") List<DiffEtape> etapes
	) {
	}

	public record JournalModeleResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("modele_id") UUID modeleId,
			@JsonProperty("version_id") UUID versionId,
			@JsonProperty("action") ActionJournalModele action,
			@JsonProperty("avant") Map<String, Object> avant,
			@JsonProperty("apres") Map<String, Object> apres,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("horodatage") Instant horodatage
	) {
	}
}
