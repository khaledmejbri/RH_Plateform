package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Solde acquis de congé annuel d'un collaborateur (une ligne par fiche, créée à la première écriture ; absente =
 * acquis 0, version 0). {@code version} n'augmente qu'à chaque mouvement (pas quand le réservé change, D-S08.a) : elle
 * est donc gérée à la main, sous verrou pessimiste, et non par {@code @Version}.
 */
@Entity
@Table(name = "rh_solde_conge")
public class SoldeConge {

	@Id
	@Column(name = "collaborateur_id", nullable = false, updatable = false)
	private UUID collaborateurId;

	@Column(name = "acquis", nullable = false, precision = 6, scale = 1)
	private BigDecimal acquis = BigDecimal.ZERO.setScale(1);

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "dernier_mouvement_id")
	private UUID dernierMouvementId;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public UUID getCollaborateurId() {
		return collaborateurId;
	}

	public void setCollaborateurId(UUID collaborateurId) {
		this.collaborateurId = collaborateurId;
	}

	public BigDecimal getAcquis() {
		return acquis;
	}

	public void setAcquis(BigDecimal acquis) {
		this.acquis = acquis;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public UUID getDernierMouvementId() {
		return dernierMouvementId;
	}

	public void setDernierMouvementId(UUID dernierMouvementId) {
		this.dernierMouvementId = dernierMouvementId;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public void setModifieLe(Instant modifieLe) {
		this.modifieLe = modifieLe;
	}
}
