package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class FormationCollaborateurCibleResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("nom_complet")
	private String nomComplet;

	@JsonProperty("matricule")
	private String matricule;

	public FormationCollaborateurCibleResponse(UUID identifiant, String nomComplet, String matricule) {
		this.identifiant = identifiant;
		this.nomComplet = nomComplet;
		this.matricule = matricule;
	}

	public UUID getIdentifiant() { return identifiant; }
	public String getNomComplet() { return nomComplet; }
	public String getMatricule() { return matricule; }
}
