package com.hr.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.RaisonSautEtape;
import com.hr.workflow.domain.ResultatEtapeExecution;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs de supervision, détail admin, réaffectation et indicateurs (US-12, US-15, D-W4-08, D-W4-09).
 */
public final class SupervisionDtos {

	private SupervisionDtos() {
	}

	public record InstanceResumeResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_libelle") String objetLibelle,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("demandeur_id") UUID demandeurId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("statut") StatutInstance statut,
			@JsonProperty("etape_courante_code") String etapeCouranteCode,
			@JsonProperty("etape_courante_libelle") String etapeCouranteLibelle,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("en_retard") boolean enRetard,
			@JsonProperty("demarre_le") Instant demarreLe,
			@JsonProperty("termine_le") Instant termineLe
	) {
	}

	public record CandidatAdminDto(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("nom") String nom,
			@JsonProperty("origine") OrigineCandidat origine,
			@JsonProperty("pour_collaborateur_id") UUID pourCollaborateurId,
			@JsonProperty("pour_nom") String pourNom,
			@JsonProperty("ajoute_le") Instant ajouteLe,
			@JsonProperty("retire_le") Instant retireLe
	) {
	}

	public record EtapeExecutionAdminDto(
			@JsonProperty("id") UUID id,
			@JsonProperty("etape_code") String etapeCode,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("statut") StatutEtapeExecution statut,
			@JsonProperty("raison_saut") RaisonSautEtape raisonSaut,
			@JsonProperty("resultat") ResultatEtapeExecution resultat,
			@JsonProperty("nb_renvois") int nbRenvois,
			@JsonProperty("active_le") Instant activeLe,
			@JsonProperty("terminee_le") Instant termineeLe
	) {
	}

	public record TacheAdminDto(
			@JsonProperty("id") UUID id,
			@JsonProperty("etape_execution_id") UUID etapeExecutionId,
			@JsonProperty("code_tache") String codeTache,
			@JsonProperty("type") TypeTache type,
			@JsonProperty("statut") StatutTache statut,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("obligatoire") boolean obligatoire,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("prochaine_relance_le") Instant prochaineRelanceLe,
			@JsonProperty("en_retard") boolean enRetard,
			@JsonProperty("nb_relances") int nbRelances,
			@JsonProperty("escalade_a_faire_le") Instant escaladeAFaireLe,
			@JsonProperty("escalade_le") Instant escaladeLe,
			@JsonProperty("version") Long version,
			@JsonProperty("decision") DecisionTache decision,
			@JsonProperty("decideur_nom") String decideurNom,
			@JsonProperty("decide_le") Instant decideLe,
			@JsonProperty("pour_nom") String pourNom,
			@JsonProperty("commentaire") String commentaire,
			@JsonProperty("candidats") List<CandidatAdminDto> candidats
	) {
	}

	public record HistoriqueAdminDto(
			@JsonProperty("id") UUID id,
			@JsonProperty("tache_id") UUID tacheId,
			@JsonProperty("type_evenement") String typeEvenement,
			@JsonProperty("acteur_compte_id") UUID acteurCompteId,
			@JsonProperty("acteur_nom") String acteurNom,
			@JsonProperty("pour_nom") String pourNom,
			@JsonProperty("details") Map<String, Object> details,
			@JsonProperty("horodatage") Instant horodatage
	) {
	}

	public record InstanceDetailAdminResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("objet_libelle") String objetLibelle,
			@JsonProperty("objet_numero") String objetNumero,
			@JsonProperty("objet_id") UUID objetId,
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("modele_version_numero") int modeleVersionNumero,
			@JsonProperty("demandeur_id") UUID demandeurId,
			@JsonProperty("demandeur_nom") String demandeurNom,
			@JsonProperty("statut") StatutInstance statut,
			@JsonProperty("etape_courante_code") String etapeCouranteCode,
			@JsonProperty("etape_courante_libelle") String etapeCouranteLibelle,
			@JsonProperty("demarre_le") Instant demarreLe,
			@JsonProperty("termine_le") Instant termineLe,
			@JsonProperty("resultat_motif") String resultatMotif,
			@JsonProperty("variables") Map<String, Object> variables,
			@JsonProperty("etapes") List<EtapeExecutionAdminDto> etapes,
			@JsonProperty("taches") List<TacheAdminDto> taches,
			@JsonProperty("historique") List<HistoriqueAdminDto> historique
	) {
	}

	public record ReaffecterTacheResponse(
			@JsonProperty("tache_id") UUID tacheId,
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("statut_instance") StatutInstance statutInstance,
			@JsonProperty("echeance_le") Instant echeanceLe,
			@JsonProperty("version") Long version,
			@JsonProperty("candidats") List<CandidatAdminDto> candidats
	) {
	}

	public record IndicateurEtapeDto(
			@JsonProperty("modele_code") String modeleCode,
			@JsonProperty("etape_code") String etapeCode,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("delai_moyen_heures") BigDecimal delaiMoyenHeures,
			@JsonProperty("nb") long nb
	) {
	}

	public record IndicateursWorkflowResponse(
			@JsonProperty("en_cours") long enCours,
			@JsonProperty("en_retard") long enRetard,
			@JsonProperty("bloquees") long bloquees,
			@JsonProperty("par_etape") List<IndicateurEtapeDto> parEtape
	) {
	}
}
