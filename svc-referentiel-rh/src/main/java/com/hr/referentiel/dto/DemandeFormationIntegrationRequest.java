package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DemandeFormationIntegrationRequest {

	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	public String getCommentaireRh() { return commentaireRh; }
	public void setCommentaireRh(String commentaireRh) { this.commentaireRh = commentaireRh; }
}
