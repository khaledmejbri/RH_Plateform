package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;

import java.util.List;
import java.util.UUID;

public class PlainteSuiviResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("type_plainte")
	private TypePlainteRh typePlainte;

	@JsonProperty("statut")
	private StatutPlainteRh statut;

	/** Libellé courant du statut (lot 3, paramétrable). */
	@JsonProperty("statut_libelle")
	private String statutLibelle;

	@JsonProperty("etapes")
	private List<WorkflowEtapeResponse> etapes;

	public PlainteSuiviResponse() {
	}

	public PlainteSuiviResponse(UUID identifiant, TypePlainteRh typePlainte, StatutPlainteRh statut,
			List<WorkflowEtapeResponse> etapes) {
		this.identifiant = identifiant;
		this.typePlainte = typePlainte;
		this.statut = statut;
		this.etapes = etapes;
	}

	public String getStatutLibelle() {
		return statutLibelle;
	}

	public void setStatutLibelle(String statutLibelle) {
		this.statutLibelle = statutLibelle;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public TypePlainteRh getTypePlainte() {
		return typePlainte;
	}

	public StatutPlainteRh getStatut() {
		return statut;
	}

	public List<WorkflowEtapeResponse> getEtapes() {
		return etapes;
	}
}
