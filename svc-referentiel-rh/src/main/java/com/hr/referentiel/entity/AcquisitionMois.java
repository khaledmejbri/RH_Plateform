package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Jours par mois figés au premier passage d'un mois (D-S07.c). Écrit par insertion native idempotente. */
@Entity
@Table(name = "rh_acquisition_mois")
public class AcquisitionMois {

	@Id
	@Column(name = "mois", nullable = false, length = 7)
	private String mois;

	@Column(name = "jours_par_mois", nullable = false, precision = 3, scale = 1)
	private BigDecimal joursParMois;

	@Column(name = "premier_passage_le", nullable = false)
	private Instant premierPassageLe;

	public String getMois() {
		return mois;
	}

	public BigDecimal getJoursParMois() {
		return joursParMois;
	}

	public Instant getPremierPassageLe() {
		return premierPassageLe;
	}
}
