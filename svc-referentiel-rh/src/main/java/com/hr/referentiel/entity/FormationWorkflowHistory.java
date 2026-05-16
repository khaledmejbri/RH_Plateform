package com.hr.referentiel.entity;

import com.hr.referentiel.domain.ActionWorkflowFormation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rh_formation_workflow_history", indexes = {
		@Index(name = "idx_formation_history_demande", columnList = "demande_formation_id"),
		@Index(name = "idx_formation_history_action", columnList = "action")
})
public class FormationWorkflowHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "demande_formation_id", nullable = false)
	private DemandeFormationRh demandeFormation;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 50)
	private ActionWorkflowFormation action;

	@Column(name = "acteur_identifiant")
	private UUID acteurIdentifiant;

	@Column(name = "acteur_nom", length = 200)
	private String acteurNom;

	@Column(name = "commentaire", length = 2000)
	private String commentaire;

	@Column(name = "date_action", nullable = false)
	private Instant dateAction;

	@PrePersist
	public void prePersist() {
		if (dateAction == null) {
			dateAction = Instant.now();
		}
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public DemandeFormationRh getDemandeFormation() { return demandeFormation; }
	public void setDemandeFormation(DemandeFormationRh demandeFormation) { this.demandeFormation = demandeFormation; }
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
