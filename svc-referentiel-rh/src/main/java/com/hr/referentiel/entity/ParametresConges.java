package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Réglages des congés (ligne unique {@code id = 1}) : jours acquis par mois et date de début des acquisitions. */
@Entity
@Table(name = "rh_parametres")
public class ParametresConges {

	public static final short ID_UNIQUE = 1;

	@Id
	@Column(name = "id", nullable = false)
	private short id = ID_UNIQUE;

	@Column(name = "jours_acquis_par_mois", nullable = false, precision = 3, scale = 1)
	private BigDecimal joursAcquisParMois;

	@Column(name = "debut_acquisitions_le")
	private LocalDate debutAcquisitionsLe;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	public short getId() {
		return id;
	}

	public BigDecimal getJoursAcquisParMois() {
		return joursAcquisParMois;
	}

	public void setJoursAcquisParMois(BigDecimal joursAcquisParMois) {
		this.joursAcquisParMois = joursAcquisParMois;
	}

	public LocalDate getDebutAcquisitionsLe() {
		return debutAcquisitionsLe;
	}

	public void setDebutAcquisitionsLe(LocalDate debutAcquisitionsLe) {
		this.debutAcquisitionsLe = debutAcquisitionsLe;
	}

	public long getVersion() {
		return version;
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
