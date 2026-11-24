package com.hr.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs du noyau d'exécution : routes internes (§ 9.7.4) et routes utilisateur (§ 9.7.3).
 */
public final class WorkflowMoteurDtos {

	private WorkflowMoteurDtos() {
	}

	// ─── Routes internes (§ 9.6.3, § 9.7.4) ──────────────────────────────────

	public record DemarrerInstanceRequest(
			@NotBlank @JsonProperty("objet_type") String objetType,
			@JsonProperty("sous_type") String sousType,
			@NotNull @JsonProperty("objet_id") UUID objetId,
			@JsonProperty("objet_numero") String objetNumero,
			@NotNull @JsonProperty("demandeur_collaborateur_id") UUID demandeurCollaborateurId,
			@JsonProperty("demandeur_compte_id") UUID demandeurCompteId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("beneficiaires") List<UUID> beneficiaires,
			@JsonProperty("variables") Map<String, Object> variables
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EtapeCouranteDto(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("intitule_intervenants") String intituleIntervenants
	) {
	}

	public record DemarrerInstanceResponse(
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("statut") StatutInstance statut,
			@JsonProperty("etape_courante") EtapeCouranteDto etapeCourante,
			@JsonProperty("effet_immediat") String effetImmediat
	) {
	}

	public record MajVariablesRequest(
			@NotNull @JsonProperty("variables") Map<String, Object> variables
	) {
	}

	public record ActeurInterneDto(
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("nom") String nom
	) {
	}

	public record AnnulerInstanceRequest(
			@Size(max = 500) @JsonProperty("motif") String motif,
			@JsonProperty("acteur") ActeurInterneDto acteur
	) {
	}

	public record ComplementFourniRequest(
			@JsonProperty("acteur") ActeurInterneDto acteur,
			@Size(max = 1000) @JsonProperty("commentaire") String commentaire
	) {
	}

	public record ActivationInterneResponse(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("mode") ModeActivation mode
	) {
	}

	public record RegleActivePointageResponse(
			@JsonProperty("actif_effectif") boolean actifEffectif,
			@JsonProperty("mode") ModeActivation mode,
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("version_numero") Integer versionNumero,
			@JsonProperty("notifier_ro") boolean notifierRo,
			@JsonProperty("portee_sites") String porteeSites,
			@JsonProperty("site_ids") List<UUID> siteIds,
			@JsonProperty("types_pointage") List<String> typesPointage,
			@JsonProperty("canal") String canal
	) {
		public static RegleActivePointageResponse inactive(ModeActivation mode) {
			return new RegleActivePointageResponse(
					false, mode, null, null, false,
					"TOUTE_LA_SOCIETE", List.of(), List.of(), "WEBSOCKET");
		}
	}

	// ─── Routes utilisateur (§ 9.7.3) ────────────────────────────────────────

	public record TypeOuvertResponse(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("sous_types") Object sousTypes
	) {
	}

	public record TacheListeItemResponse(
			@JsonProperty("tache_id") UUID tacheId,
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("type") TypeTache type,
			@JsonProperty("libelle_etape") String libelleEtape,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_libelle") String objetLibelle,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("en_retard") boolean enRetard,
			@JsonProperty("origine") OrigineCandidat origine,
			@JsonProperty("pour_nom") String pourNom,
			@JsonProperty("actions_permises") List<String> actionsPermises,
			@JsonProperty("version") Long version,
			@JsonProperty("resume") Map<String, Object> resume
	) {
	}

	public record CompteurTachesResponse(
			@JsonProperty("a_traiter") long aTraiter,
			@JsonProperty("en_retard") long enRetard
	) {
	}

	public record TacheDetailResponse(
			@JsonProperty("tache_id") UUID tacheId,
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("type") TypeTache type,
			@JsonProperty("statut") StatutTache statut,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_libelle") String objetLibelle,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("en_retard") boolean enRetard,
			@JsonProperty("actions_permises") List<String> actionsPermises,
			@JsonProperty("commentaire_obligatoire") boolean commentaireObligatoire,
			@JsonProperty("version") Long version,
			@JsonProperty("resume") Map<String, Object> resume,
			@JsonProperty("suivi") SuiviInstanceResponse suivi
	) {
	}

	public record DecisionRequest(
			@NotBlank @JsonProperty("action") String action,
			@Size(max = 1000) @JsonProperty("commentaire") String commentaire,
			@NotNull @JsonProperty("version") Long version,
			@NotBlank @Size(max = 64) @JsonProperty("cle_idempotence") String cleIdempotence
	) {
	}

	public record InstanceDecisionDto(
			@JsonProperty("statut") StatutInstance statut,
			@JsonProperty("etape_courante") EtapeCouranteDto etapeCourante
	) {
	}

	public record DecisionResponse(
			@JsonProperty("tache_id") UUID tacheId,
			@JsonProperty("decision") String decision,
			@JsonProperty("instance") InstanceDecisionDto instance
	) {
	}

	public record DecisionEnCoursResponse(
			@JsonProperty("statut") String statut
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EtapeSuiviDto(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("statut") String statut,
			@JsonProperty("raison") String raison,
			@JsonProperty("intitule_intervenants") String intituleIntervenants,
			@JsonProperty("candidats") List<String> candidats,
			@JsonProperty("decideur_nom") String decideurNom,
			@JsonProperty("pour_nom") String pourNom,
			@JsonProperty("resultat") String resultat,
			@JsonProperty("motif") String motif,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("date_decision") Instant dateDecision
	) {
	}

	public record SuiviInstanceResponse(
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("modele_version_numero") int modeleVersionNumero,
			@JsonProperty("statut") StatutInstance statut,
			@JsonProperty("etape_courante") EtapeCouranteDto etapeCourante,
			@JsonProperty("demarre_le") Instant demarreLe,
			@JsonProperty("termine_le") Instant termineLe,
			@JsonProperty("resultat_motif") String resultatMotif,
			@JsonProperty("etapes") List<EtapeSuiviDto> etapes
	) {
	}
}
