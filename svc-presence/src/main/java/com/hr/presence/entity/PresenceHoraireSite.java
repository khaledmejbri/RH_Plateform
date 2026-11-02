package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Horaire d'un jour de la semaine pour un site (D-P11). Aucune ligne pour un site = site sans horaire ;
 * un jour absent = site fermé ; fin ≤ début = horaire de nuit (la journée est rattachée au jour du début).
 */
@Entity
@Table(name = "presence_horaire_site",
		uniqueConstraints = @UniqueConstraint(name = "uk_presence_horaire_site_jour", columnNames = {"site_id", "jour_semaine"}))
public class PresenceHoraireSite {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "site_id", nullable = false)
	private UUID siteId;

	/** 1 = lundi … 7 = dimanche (ISO). */
	@Column(name = "jour_semaine", nullable = false)
	private int jourSemaine;

	@Column(name = "heure_debut", nullable = false)
	private LocalTime heureDebut;

	@Column(name = "heure_fin", nullable = false)
	private LocalTime heureFin;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public boolean estDeNuit() {
		return !heureFin.isAfter(heureDebut);
	}

	public UUID getId() {
		return id;
	}

	public UUID getSiteId() {
		return siteId;
	}

	public void setSiteId(UUID siteId) {
		this.siteId = siteId;
	}

	public int getJourSemaine() {
		return jourSemaine;
	}

	public void setJourSemaine(int jourSemaine) {
		this.jourSemaine = jourSemaine;
	}

	public LocalTime getHeureDebut() {
		return heureDebut;
	}

	public void setHeureDebut(LocalTime heureDebut) {
		this.heureDebut = heureDebut;
	}

	public LocalTime getHeureFin() {
		return heureFin;
	}

	public void setHeureFin(LocalTime heureFin) {
		this.heureFin = heureFin;
	}
}
