package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FamilleMetierCreationRequest {

	/** Ignore (lot 3) : le code est genere depuis le libelle. Conserve pour les anciens clients. */
	@Size(max = 64)
	@JsonProperty("code")
	private String code;

	@NotBlank
	@Size(max = 255)
	@JsonProperty("libelle")
	private String libelle;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLibelle() {
		return libelle;
	}

	public void setLibelle(String libelle) {
		this.libelle = libelle;
	}
}
