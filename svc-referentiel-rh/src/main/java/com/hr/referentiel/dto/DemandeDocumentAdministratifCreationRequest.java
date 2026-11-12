package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DemandeDocumentAdministratifCreationRequest {

	/** Code d'un type de document actif (référentiel TYPE_DOCUMENT) ; inconnu : 400, inactif : 422. */
	@NotBlank
	@Size(max = 40)
	@JsonProperty("type_document")
	private String typeDocument;

	@Size(max = 2000)
	@JsonProperty("commentaire_demandeur")
	private String commentaireDemandeur;

	public String getTypeDocument() {
		return typeDocument;
	}

	public void setTypeDocument(String typeDocument) {
		this.typeDocument = typeDocument;
	}

	public String getCommentaireDemandeur() {
		return commentaireDemandeur;
	}

	public void setCommentaireDemandeur(String commentaireDemandeur) {
		this.commentaireDemandeur = commentaireDemandeur;
	}
}
