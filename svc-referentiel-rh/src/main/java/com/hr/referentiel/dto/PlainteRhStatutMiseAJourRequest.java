package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutPlainteRh;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CDC v2 §M04 : commentaire RH obligatoire si statut = RESOLU ou FERME.
 * Transitions autorisées : NOUVEAU→EN_ANALYSE, EN_ANALYSE→EN_TRAITEMENT,
 * EN_TRAITEMENT→RESOLU, RESOLU→FERME. Pas de saut de statut.
 */
public class PlainteRhStatutMiseAJourRequest {

	@NotNull
	@JsonProperty("statut")
	private StatutPlainteRh statut;

	@Size(max = 2000)
	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	public StatutPlainteRh getStatut() { return statut; }
	public void setStatut(StatutPlainteRh statut) { this.statut = statut; }

	public String getCommentaireRh() { return commentaireRh; }
	public void setCommentaireRh(String commentaireRh) { this.commentaireRh = commentaireRh; }
}
