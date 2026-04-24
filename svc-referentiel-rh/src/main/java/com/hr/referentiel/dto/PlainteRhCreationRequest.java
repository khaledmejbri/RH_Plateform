package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.TypePlainteRh;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PlainteRhCreationRequest {

	@NotNull
	@JsonProperty("type_plainte")
	private TypePlainteRh typePlainte;

	@NotBlank
	@Size(max = 255)
	@JsonProperty("titre")
	private String titre;

	@NotBlank
	@Size(max = 4000)
	@JsonProperty("description")
	private String description;

	public TypePlainteRh getTypePlainte() {
		return typePlainte;
	}

	public void setTypePlainte(TypePlainteRh typePlainte) {
		this.typePlainte = typePlainte;
	}

	public String getTitre() {
		return titre;
	}

	public void setTitre(String titre) {
		this.titre = titre;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
