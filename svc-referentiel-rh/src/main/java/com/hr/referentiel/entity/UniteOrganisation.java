package com.hr.referentiel.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rh_unite_organisation", indexes = {
		@Index(name = "idx_unite_code", columnList = "code", unique = true),
		@Index(name = "idx_unite_actif", columnList = "actif")
})
public class UniteOrganisation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "code", nullable = false, unique = true, length = 32)
	private String code;

	@Column(name = "libelle", nullable = false, length = 255)
	private String libelle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_identifiant")
	private UniteOrganisation parent;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public UniteOrganisation() {
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

	public UniteOrganisation getParent() {
		return parent;
	}

	public void setParent(UniteOrganisation parent) {
		this.parent = parent;
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
