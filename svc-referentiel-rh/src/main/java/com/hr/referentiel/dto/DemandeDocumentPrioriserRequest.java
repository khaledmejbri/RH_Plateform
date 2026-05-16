package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de la requête pour prioriser une demande de document en dehors de l'ordre FIFO.
 * Une justification obligatoire est requise : elle sera envoyée au Responsable de l'unité RH.
 */
public class DemandeDocumentPrioriserRequest {

	@NotBlank
	@Size(max = 2000)
	@JsonProperty("justification")
	private String justification;

	public String getJustification() {
		return justification;
	}

	public void setJustification(String justification) {
		this.justification = justification;
	}
}
