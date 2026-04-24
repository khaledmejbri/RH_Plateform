package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class DemandeAdministrativeRhCreationRequest {

	@NotNull
	@JsonProperty("type_demande")
	private TypeDemandeAdministrativeRh typeDemande;

	@NotNull
	@JsonProperty("contenu")
	private Map<String, Object> contenu;

	public TypeDemandeAdministrativeRh getTypeDemande() {
		return typeDemande;
	}

	public void setTypeDemande(TypeDemandeAdministrativeRh typeDemande) {
		this.typeDemande = typeDemande;
	}

	public Map<String, Object> getContenu() {
		return contenu;
	}

	public void setContenu(Map<String, Object> contenu) {
		this.contenu = contenu;
	}
}
