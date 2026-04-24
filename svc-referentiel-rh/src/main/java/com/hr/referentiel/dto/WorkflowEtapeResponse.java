package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkflowEtapeResponse {

	@JsonProperty("code")
	private String code;

	@JsonProperty("libelle")
	private String libelle;

	@JsonProperty("terminee")
	private boolean terminee;

	@JsonProperty("en_cours")
	private boolean enCours;

	public WorkflowEtapeResponse() {
	}

	public WorkflowEtapeResponse(String code, String libelle, boolean terminee, boolean enCours) {
		this.code = code;
		this.libelle = libelle;
		this.terminee = terminee;
		this.enCours = enCours;
	}

	public String getCode() {
		return code;
	}

	public String getLibelle() {
		return libelle;
	}

	public boolean isTerminee() {
		return terminee;
	}

	public boolean isEnCours() {
		return enCours;
	}
}
