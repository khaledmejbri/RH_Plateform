package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Jour férié (D-S02) : date unique (actifs et inactifs), libellé non unique. */
@Entity
@Table(name = "rh_jour_ferie", uniqueConstraints = @UniqueConstraint(name = "uk_jour_ferie_date", columnNames = "date_jour"))
public class JourFerie {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "date_jour", nullable = false)
	private LocalDate dateJour;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	public UUID getId() {
		return id;
	}

	public LocalDate getDateJour() {
		return dateJour;
	}

	public void setDateJour(LocalDate dateJour) {
		this.dateJour = dateJour;
	}

	public String getLibelle() {
		return libelle;
	}

	public void setLibelle(String libelle) {
		this.libelle = libelle;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public long getVersion() {
		return version;
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

	public UUID getModifieParCompteId() {
		return modifieParCompteId;
	}

	public void setModifieParCompteId(UUID modifieParCompteId) {
		this.modifieParCompteId = modifieParCompteId;
	}
}
