package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DemandeDocumentDisponibleRequest {

	@NotBlank
	@Size(max = 1024)
	@JsonProperty("reference_livrable")
	private String referenceLivrable;

	@Size(max = 2000)
	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	public String getReferenceLivrable() {
		return referenceLivrable;
	}

	public void setReferenceLivrable(String referenceLivrable) {
		this.referenceLivrable = referenceLivrable;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public void setCommentaireRh(String commentaireRh) {
		this.commentaireRh = commentaireRh;
	}
}
