package com.hr.presence.entity;

import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de pointage manuel après un rejet (D-P04) : validée par le responsable D-H03, sinon par les RH.
 * Identités et noms figés à la création (snapshot référentiel).
 */
@Entity
@Table(
		name = "presence_demande_manuelle",
		uniqueConstraints = @UniqueConstraint(name = "uk_demande_manuelle_rejet", columnNames = "pointage_rejete_id"),
		indexes = {
				@Index(name = "idx_demande_manuelle_collab", columnList = "collaborateur_id,cree_le"),
				@Index(name = "idx_demande_manuelle_valideur", columnList = "valideur_id,statut"),
				@Index(name = "idx_demande_manuelle_statut", columnList = "statut,cree_le")
		}
)
public class PresenceDemandeManuelle {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "pointage_rejete_id", nullable = false)
	private UUID pointageRejeteId;

	@Column(name = "collaborateur_id", nullable = false, length = 64)
	private String collaborateurId;

	@Column(name = "collaborateur_compte_id", length = 64)
	private String collaborateurCompteId;

	@Column(name = "collaborateur_nom", length = 200)
	private String collaborateurNom;

	@Column(name = "site_id")
	private UUID siteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointageType type;

	@Column(name = "heure_declaree", nullable = false)
	private Instant heureDeclaree;

	@Column(nullable = false, length = 500)
	private String justification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatutDemandeManuelle statut = StatutDemandeManuelle.EN_ATTENTE;

	/** Fiche du valideur attendu ; NULL = validation par les RH. */
	@Column(name = "valideur_id", length = 64)
	private String valideurId;

	@Column(name = "valideur_compte_id", length = 64)
	private String valideurCompteId;

	@Column(name = "valideur_nom", length = 200)
	private String valideurNom;

	@Column(name = "decide_par", length = 200)
	private String decidePar;

	@Column(name = "decide_le")
	private Instant decideLe;

	@Column(name = "motif_refus", length = 500)
	private String motifRefus;

	@Column(name = "pointage_id")
	private UUID pointageId;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	public boolean validationParRh() {
		return valideurId == null;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public UUID getPointageRejeteId() { return pointageRejeteId; }
	public void setPointageRejeteId(UUID pointageRejeteId) { this.pointageRejeteId = pointageRejeteId; }

	public String getCollaborateurId() { return collaborateurId; }
	public void setCollaborateurId(String collaborateurId) { this.collaborateurId = collaborateurId; }

	public String getCollaborateurCompteId() { return collaborateurCompteId; }
	public void setCollaborateurCompteId(String collaborateurCompteId) { this.collaborateurCompteId = collaborateurCompteId; }

	public String getCollaborateurNom() { return collaborateurNom; }
	public void setCollaborateurNom(String collaborateurNom) { this.collaborateurNom = collaborateurNom; }

	public UUID getSiteId() { return siteId; }
	public void setSiteId(UUID siteId) { this.siteId = siteId; }

	public PointageType getType() { return type; }
	public void setType(PointageType type) { this.type = type; }

	public Instant getHeureDeclaree() { return heureDeclaree; }
	public void setHeureDeclaree(Instant heureDeclaree) { this.heureDeclaree = heureDeclaree; }

	public String getJustification() { return justification; }
	public void setJustification(String justification) { this.justification = justification; }

	public StatutDemandeManuelle getStatut() { return statut; }
	public void setStatut(StatutDemandeManuelle statut) { this.statut = statut; }

	public String getValideurId() { return valideurId; }
	public void setValideurId(String valideurId) { this.valideurId = valideurId; }

	public String getValideurCompteId() { return valideurCompteId; }
	public void setValideurCompteId(String valideurCompteId) { this.valideurCompteId = valideurCompteId; }

	public String getValideurNom() { return valideurNom; }
	public void setValideurNom(String valideurNom) { this.valideurNom = valideurNom; }

	public String getDecidePar() { return decidePar; }
	public void setDecidePar(String decidePar) { this.decidePar = decidePar; }

	public Instant getDecideLe() { return decideLe; }
	public void setDecideLe(Instant decideLe) { this.decideLe = decideLe; }

	public String getMotifRefus() { return motifRefus; }
	public void setMotifRefus(String motifRefus) { this.motifRefus = motifRefus; }

	public UUID getPointageId() { return pointageId; }
	public void setPointageId(UUID pointageId) { this.pointageId = pointageId; }

	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
}
