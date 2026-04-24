package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutPlainteRh;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PlainteRhStatutMiseAJourRequest {

	@NotNull
	@JsonProperty("statut")
	private StatutPlainteRh statut;

	@Size(max = 2000)
	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	public StatutPlainteRh getStatut() {
		return statut;
	}

	public void setStatut(StatutPlainteRh statut) {
		this.statut = statut;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public void setCommentaireRh(String commentaireRh) {
		this.commentaireRh = commentaireRh;
	}
}
