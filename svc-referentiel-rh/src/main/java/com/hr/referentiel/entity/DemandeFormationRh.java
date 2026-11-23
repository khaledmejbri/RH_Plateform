package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import com.hr.referentiel.domain.CibleDemandeFormationRh;
import com.hr.referentiel.domain.OrigineDemandeFormationRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_demande_formation", indexes = {
		@Index(name = "idx_demande_formation_societe_demandeur", columnList = "societe_id, demandeur_identifiant"),
		@Index(name = "idx_demande_formation_societe_statut", columnList = "societe_id, statut"),
		@Index(name = "idx_demande_formation_societe_origine", columnList = "societe_id, origine"),
		@Index(name = "idx_demande_formation_societe_numero", columnList = "societe_id, numero", unique = true),
		@Index(name = "idx_demande_formation_societe_wf_mode_etat", columnList = "societe_id, workflow_mode, workflow_etat")
})
public class DemandeFormationRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", updatable = false)
	private UUID societeId;

	/** D-L02 : numéro lisible attribué à la création, unique et immuable. Null le temps de la reprise des anciennes lignes. */
	@Column(name = "numero", updatable = false, length = 24)
	private String numero;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "demandeur_identifiant", nullable = false)
	private Collaborateur demandeur;

	@Enumerated(EnumType.STRING)
	@Column(name = "origine", nullable = false, length = 40)
	private OrigineDemandeFormationRh origine;

	@Enumerated(EnumType.STRING)
	@Column(name = "cible", nullable = false, length = 40)
	private CibleDemandeFormationRh cible;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "unite_cible_identifiant")
	private UniteOrganisation uniteCible;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(
			name = "rh_demande_formation_collaborateur_cible",
			joinColumns = @JoinColumn(name = "demande_formation_identifiant"))
	@Column(name = "collaborateur_identifiant", nullable = false)
	private Set<UUID> collaborateursCibles = new LinkedHashSet<>();

	@Column(name = "type_formation", nullable = false, length = 160)
	private String typeFormation;

	@Column(name = "organisme", nullable = false, length = 255)
	private String organisme;

	@Column(name = "duree_heures", nullable = false)
	private Integer dureeHeures;

	@Column(name = "cout_estime", precision = 14, scale = 2)
	private BigDecimal coutEstime;

	@Column(name = "objectifs_pedagogiques", nullable = false, length = 2000)
	private String objectifsPedagogiques;

	@Column(name = "justification", nullable = false, length = 2000)
	private String justification;

	@Column(name = "date_souhaitee_debut")
	private LocalDate dateSouhaiteeDebut;

	@Column(name = "date_souhaitee_fin")
	private LocalDate dateSouhaiteeFin;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 40)
	private StatutDemandeFormationRh statut = StatutDemandeFormationRh.EN_VALIDATION_RRH;

	@Column(name = "commentaire_rh", length = 2000)
	private String commentaireRh;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	// ---- Intégration moteur de workflow (lot W6) ----

	@Column(name = "workflow_mode", nullable = false, length = 10, columnDefinition = "varchar(10) default 'HERITE'")
	private String workflowMode = "HERITE";

	@Column(name = "workflow_instance_id")
	private UUID workflowInstanceId;

	@Column(name = "workflow_etat", length = 12)
	private String workflowEtat;

	@Column(name = "workflow_etape_libelle", length = 80)
	private String workflowEtapeLibelle;

	@Column(name = "workflow_echeance_le")
	private Instant workflowEcheanceLe;

	@Column(name = "workflow_tentatives", nullable = false, columnDefinition = "integer default 0")
	private int workflowTentatives = 0;

	@Column(name = "workflow_projection_le")
	private Instant workflowProjectionLe;

	@PrePersist
	public void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		modifieLe = Instant.now();
	}

	public String getNumero() {
		return numero;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public Collaborateur getDemandeur() { return demandeur; }
	public void setDemandeur(Collaborateur demandeur) { this.demandeur = demandeur; }
	public OrigineDemandeFormationRh getOrigine() { return origine; }
	public void setOrigine(OrigineDemandeFormationRh origine) { this.origine = origine; }
	public CibleDemandeFormationRh getCible() { return cible; }
	public void setCible(CibleDemandeFormationRh cible) { this.cible = cible; }
	public UniteOrganisation getUniteCible() { return uniteCible; }
	public void setUniteCible(UniteOrganisation uniteCible) { this.uniteCible = uniteCible; }
	public Set<UUID> getCollaborateursCibles() { return collaborateursCibles; }
	public void setCollaborateursCibles(Set<UUID> collaborateursCibles) {
		this.collaborateursCibles = collaborateursCibles != null ? collaborateursCibles : new LinkedHashSet<>();
	}
	public String getTypeFormation() { return typeFormation; }
	public void setTypeFormation(String typeFormation) { this.typeFormation = typeFormation; }
	public String getOrganisme() { return organisme; }
	public void setOrganisme(String organisme) { this.organisme = organisme; }
	public Integer getDureeHeures() { return dureeHeures; }
	public void setDureeHeures(Integer dureeHeures) { this.dureeHeures = dureeHeures; }
	public BigDecimal getCoutEstime() { return coutEstime; }
	public void setCoutEstime(BigDecimal coutEstime) { this.coutEstime = coutEstime; }
	public String getObjectifsPedagogiques() { return objectifsPedagogiques; }
	public void setObjectifsPedagogiques(String objectifsPedagogiques) { this.objectifsPedagogiques = objectifsPedagogiques; }
	public String getJustification() { return justification; }
	public void setJustification(String justification) { this.justification = justification; }
	public LocalDate getDateSouhaiteeDebut() { return dateSouhaiteeDebut; }
	public void setDateSouhaiteeDebut(LocalDate dateSouhaiteeDebut) { this.dateSouhaiteeDebut = dateSouhaiteeDebut; }
	public LocalDate getDateSouhaiteeFin() { return dateSouhaiteeFin; }
	public void setDateSouhaiteeFin(LocalDate dateSouhaiteeFin) { this.dateSouhaiteeFin = dateSouhaiteeFin; }
	public StatutDemandeFormationRh getStatut() { return statut; }
	public void setStatut(StatutDemandeFormationRh statut) { this.statut = statut; }
	public String getCommentaireRh() { return commentaireRh; }
	public void setCommentaireRh(String commentaireRh) { this.commentaireRh = commentaireRh; }
	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
	public String getWorkflowMode() { return workflowMode; }
	public void setWorkflowMode(String workflowMode) { this.workflowMode = workflowMode; }
	public UUID getWorkflowInstanceId() { return workflowInstanceId; }
	public void setWorkflowInstanceId(UUID workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
	public String getWorkflowEtat() { return workflowEtat; }
	public void setWorkflowEtat(String workflowEtat) { this.workflowEtat = workflowEtat; }
	public String getWorkflowEtapeLibelle() { return workflowEtapeLibelle; }
	public void setWorkflowEtapeLibelle(String workflowEtapeLibelle) { this.workflowEtapeLibelle = workflowEtapeLibelle; }
	public Instant getWorkflowEcheanceLe() { return workflowEcheanceLe; }
	public void setWorkflowEcheanceLe(Instant workflowEcheanceLe) { this.workflowEcheanceLe = workflowEcheanceLe; }
	public int getWorkflowTentatives() { return workflowTentatives; }
	public void setWorkflowTentatives(int workflowTentatives) { this.workflowTentatives = workflowTentatives; }
	public Instant getWorkflowProjectionLe() { return workflowProjectionLe; }
	public void setWorkflowProjectionLe(Instant workflowProjectionLe) { this.workflowProjectionLe = workflowProjectionLe; }
}
