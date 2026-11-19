package com.hr.referentiel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal technique d'idempotence des événements Kafka consommés (ex. {@code rh.societes}, § 9.8).
 * Table technique globale sans {@code @TenantId}.
 */
@Entity
@Table(name = "rh_evenement_traite")
public class RhEvenementTraite {

	@Id
	@Column(name = "evenement_id", nullable = false, updatable = false)
	private UUID evenementId;

	@Column(name = "sujet", nullable = false, length = 100)
	private String sujet;

	@Column(name = "traite_le", nullable = false)
	private Instant traiteLe;

	protected RhEvenementTraite() {
	}

	public RhEvenementTraite(UUID evenementId, String sujet, Instant traiteLe) {
		this.evenementId = evenementId;
		this.sujet = sujet;
		this.traiteLe = traiteLe;
	}

	public UUID getEvenementId() {
		return evenementId;
	}

	public String getSujet() {
		return sujet;
	}

	public Instant getTraiteLe() {
		return traiteLe;
	}
}
