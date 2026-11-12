package com.hr.referentiel.entity;

import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de document administratif : transmission directe au RRH, file unique ordonnée par {@link #creeLe}
 * (premier arrivé, premier servi — priorité FIFO, sans saut de file).
 */
@Entity
@Table(name = "rh_demande_document_administratif", indexes = {
		@Index(name = "idx_doc_dem_collab", columnList = "demandeur_identifiant"),
		@Index(name = "idx_doc_dem_statut_cree", columnList = "statut,cree_le"),
		@Index(name = "idx_doc_dem_numero", columnList = "numero", unique = true)
})
public class DemandeDocumentAdministratifRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	/** D-L02 : numéro lisible attribué à la création, unique et immuable. Null le temps de la reprise des anciennes lignes. */
	@Column(name = "numero", updatable = false, length = 24)
	private String numero;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "demandeur_identifiant", nullable = false)
	private Collaborateur demandeur;

	/** Code du type de document (référentiel TYPE_DOCUMENT, paramétrable : ni énumération ni contrainte CHECK). */
	@Column(name = "type_document", nullable = false, length = 40)
	private String typeDocument;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 32)
	private StatutDocumentAdministratifDemandeRh statut = StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE;

	@Column(name = "delai_sla_heures", nullable = false)
	private int delaiSlaHeures;

	@Column(name = "date_echeance_traitement", nullable = false)
	private Instant dateEcheanceTraitement;

	@Column(name = "commentaire_demandeur", length = 2000)
	private String commentaireDemandeur;

	@Column(name = "commentaire_rh", length = 2000)
	private String commentaireRh;

	@Column(name = "reference_livrable", length = 1024)
	private String referenceLivrable;

	/** Justification obligatoire lorsqu'un RH traite cette demande en dérogeant à l'ordre FIFO. */
	@Column(name = "justification_derogation_fifo", length = 2000)
	private String justificationDerogationFifo;

	/** Identifiant du RH ayant autorisé la dérogation FIFO. */
	@Column(name = "derogation_fifo_par")
	private UUID derogationFifoPar;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public DemandeDocumentAdministratifRh() {
	}

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

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Collaborateur getDemandeur() {
		return demandeur;
	}

	public void setDemandeur(Collaborateur demandeur) {
		this.demandeur = demandeur;
	}

	public String getTypeDocument() {
		return typeDocument;
	}

	public void setTypeDocument(String typeDocument) {
		this.typeDocument = typeDocument;
	}

	public StatutDocumentAdministratifDemandeRh getStatut() {
		return statut;
	}

	public void setStatut(StatutDocumentAdministratifDemandeRh statut) {
		this.statut = statut;
	}

	public int getDelaiSlaHeures() {
		return delaiSlaHeures;
	}

	public void setDelaiSlaHeures(int delaiSlaHeures) {
		this.delaiSlaHeures = delaiSlaHeures;
	}

	public Instant getDateEcheanceTraitement() {
		return dateEcheanceTraitement;
	}

	public void setDateEcheanceTraitement(Instant dateEcheanceTraitement) {
		this.dateEcheanceTraitement = dateEcheanceTraitement;
	}

	public String getCommentaireDemandeur() {
		return commentaireDemandeur;
	}

	public void setCommentaireDemandeur(String commentaireDemandeur) {
		this.commentaireDemandeur = commentaireDemandeur;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public void setCommentaireRh(String commentaireRh) {
		this.commentaireRh = commentaireRh;
	}

	public String getReferenceLivrable() {
		return referenceLivrable;
	}

	public void setReferenceLivrable(String referenceLivrable) {
		this.referenceLivrable = referenceLivrable;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public void setCreeLe(Instant creeLe) {
		this.creeLe = creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public void setModifieLe(Instant modifieLe) {
		this.modifieLe = modifieLe;
	}

	public String getJustificationDerogationFifo() {
		return justificationDerogationFifo;
	}

	public void setJustificationDerogationFifo(String justificationDerogationFifo) {
		this.justificationDerogationFifo = justificationDerogationFifo;
	}

	public UUID getDerogationFifoPar() {
		return derogationFifoPar;
	}

	public void setDerogationFifoPar(UUID derogationFifoPar) {
		this.derogationFifoPar = derogationFifoPar;
	}
}
