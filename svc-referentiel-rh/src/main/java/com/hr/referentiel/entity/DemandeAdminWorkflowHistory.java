package com.hr.referentiel.entity;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rh_demande_admin_workflow_history", indexes = {
		@Index(name = "idx_admin_history_demande", columnList = "demande_administrative_id"),
		@Index(name = "idx_admin_history_action", columnList = "action")
})
public class DemandeAdminWorkflowHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "demande_administrative_id", nullable = false)
	private DemandeAdministrativeRh demandeAdministrative;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 50)
	private ActionWorkflowAdministratif action;

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
	public DemandeAdministrativeRh getDemandeAdministrative() { return demandeAdministrative; }
	public void setDemandeAdministrative(DemandeAdministrativeRh demandeAdministrative) { this.demandeAdministrative = demandeAdministrative; }
	public ActionWorkflowAdministratif getAction() { return action; }
	public void setAction(ActionWorkflowAdministratif action) { this.action = action; }
	public UUID getActeurIdentifiant() { return acteurIdentifiant; }
	public void setActeurIdentifiant(UUID acteurIdentifiant) { this.acteurIdentifiant = acteurIdentifiant; }
	public String getActeurNom() { return acteurNom; }
	public void setActeurNom(String acteurNom) { this.acteurNom = acteurNom; }
	public String getCommentaire() { return commentaire; }
	public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
	public Instant getDateAction() { return dateAction; }
	public void setDateAction(Instant dateAction) { this.dateAction = dateAction; }
}
