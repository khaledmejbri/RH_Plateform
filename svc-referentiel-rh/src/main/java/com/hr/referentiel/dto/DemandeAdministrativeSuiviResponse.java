package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;

import java.util.List;
import java.util.UUID;

/**
 * Suivi du workflow CDC : employé → supérieur (si présent) → RRH → clôture.
 */
public class DemandeAdministrativeSuiviResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("type_demande")
	private TypeDemandeAdministrativeRh typeDemande;

	@JsonProperty("statut")
	private StatutDemandeAdministrativeRh statut;

	@JsonProperty("etape_superieur_requise")
	private boolean etapeSuperieurRequise;

	@JsonProperty("etapes")
	private List<WorkflowEtapeResponse> etapes;

	public DemandeAdministrativeSuiviResponse() {
	}

	public DemandeAdministrativeSuiviResponse(UUID identifiant, TypeDemandeAdministrativeRh typeDemande,
			StatutDemandeAdministrativeRh statut, boolean etapeSuperieurRequise,
			List<WorkflowEtapeResponse> etapes) {
		this.identifiant = identifiant;
		this.typeDemande = typeDemande;
		this.statut = statut;
		this.etapeSuperieurRequise = etapeSuperieurRequise;
		this.etapes = etapes;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public TypeDemandeAdministrativeRh getTypeDemande() {
		return typeDemande;
	}

	public StatutDemandeAdministrativeRh getStatut() {
		return statut;
	}

	public boolean isEtapeSuperieurRequise() {
		return etapeSuperieurRequise;
	}

	public List<WorkflowEtapeResponse> getEtapes() {
		return etapes;
	}
}
