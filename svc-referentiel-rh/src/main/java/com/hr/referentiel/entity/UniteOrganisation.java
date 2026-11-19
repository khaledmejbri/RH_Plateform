package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_unite_organisation", indexes = {
		@Index(name = "idx_unite_societe_code", columnList = "societe_id, code", unique = true),
		@Index(name = "idx_unite_societe_actif", columnList = "societe_id, actif")
})
public class UniteOrganisation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", updatable = false)
	private UUID societeId;

	@Column(name = "code", nullable = false, length = 32)
	private String code;

	@Column(name = "libelle", nullable = false, length = 255)
	private String libelle;

	/** Type libre du nœud (ex. CEO, Direction, Département, Unité, CTO). */
	@Column(name = "type_noeud", length = 120)
	private String typeNoeud;

	/** Titre libre du poste de management sur ce nœud (ex. Directeur IT, Chef de département). */
	@Column(name = "titre_poste", length = 255)
	private String titrePoste;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_identifiant")
	private UniteOrganisation parent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_identifiant")
	private Collaborateur manager;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public UniteOrganisation() {
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public void setSocieteId(UUID societeId) {
		this.societeId = societeId;
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

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLibelle() {
		return libelle;
	}

	public void setLibelle(String libelle) {
		this.libelle = libelle;
	}

	public String getTypeNoeud() {
		return typeNoeud;
	}

	public void setTypeNoeud(String typeNoeud) {
		this.typeNoeud = typeNoeud;
	}

	public String getTitrePoste() {
		return titrePoste;
	}

	public void setTitrePoste(String titrePoste) {
		this.titrePoste = titrePoste;
	}

	public UniteOrganisation getParent() {
		return parent;
	}

	public void setParent(UniteOrganisation parent) {
		this.parent = parent;
	}

	public Collaborateur getManager() {
		return manager;
	}

	public void setManager(Collaborateur manager) {
		this.manager = manager;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
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
}
