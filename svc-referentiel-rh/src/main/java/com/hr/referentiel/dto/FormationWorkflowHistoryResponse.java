package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.ActionWorkflowFormation;

import java.time.Instant;
import java.util.UUID;

public class FormationWorkflowHistoryResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("action")
	private ActionWorkflowFormation action;

	@JsonProperty("acteur_identifiant")
	private UUID acteurIdentifiant;

	@JsonProperty("acteur_nom")
	private String acteurNom;

	@JsonProperty("commentaire")
	private String commentaire;

	@JsonProperty("date_action")
	private Instant dateAction;

	public UUID getIdentifiant() { return identifiant; }
	public void setIdentifiant(UUID identifiant) { this.identifiant = identifiant; }
	public ActionWorkflowFormation getAction() { return action; }
	public void setAction(ActionWorkflowFormation action) { this.action = action; }
	public UUID getActeurIdentifiant() { return acteurIdentifiant; }
	public void setActeurIdentifiant(UUID acteurIdentifiant) { this.acteurIdentifiant = acteurIdentifiant; }
	public String getActeurNom() { return acteurNom; }
	public void setActeurNom(String acteurNom) { this.acteurNom = acteurNom; }
	public String getCommentaire() { return commentaire; }
	public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
	public Instant getDateAction() { return dateAction; }
	public void setDateAction(Instant dateAction) { this.dateAction = dateAction; }
}
