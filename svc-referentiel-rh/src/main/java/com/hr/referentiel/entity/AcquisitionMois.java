package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Jours par mois figés au premier passage d'un mois (D-S07.c). Écrit par insertion native idempotente. */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_acquisition_mois", uniqueConstraints = {
		@UniqueConstraint(name = "uk_acquisition_mois_societe_mois", columnNames = {"societe_id", "mois"})
})
public class AcquisitionMois {

	@Id
	@Column(name = "mois", nullable = false, length = 7)
	private String mois;

	@TenantId
	@Column(name = "societe_id", updatable = false)
	private UUID societeId;

	@Column(name = "jours_par_mois", nullable = false, precision = 3, scale = 1)
	private BigDecimal joursParMois;

	@Column(name = "premier_passage_le", nullable = false)
	private Instant premierPassageLe;

	public String getMois() {
		return mois;
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public BigDecimal getJoursParMois() {
		return joursParMois;
	}

	public Instant getPremierPassageLe() {
		return premierPassageLe;
	}
}
