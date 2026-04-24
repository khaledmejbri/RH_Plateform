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
