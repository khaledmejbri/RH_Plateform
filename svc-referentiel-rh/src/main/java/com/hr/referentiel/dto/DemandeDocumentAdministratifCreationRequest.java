package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DemandeDocumentAdministratifCreationRequest {

	@NotNull
	@JsonProperty("type_document")
	private TypeDocumentAdministratifDemandeRh typeDocument;

	@Size(max = 2000)
	@JsonProperty("commentaire_demandeur")
	private String commentaireDemandeur;

	public TypeDocumentAdministratifDemandeRh getTypeDocument() {
		return typeDocument;
	}

	public void setTypeDocument(TypeDocumentAdministratifDemandeRh typeDocument) {
		this.typeDocument = typeDocument;
	}

	public String getCommentaireDemandeur() {
		return commentaireDemandeur;
	}

	public void setCommentaireDemandeur(String commentaireDemandeur) {
		this.commentaireDemandeur = commentaireDemandeur;
	}
}
