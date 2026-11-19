package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Affectation d'un collaborateur à une {@link Fonction} (avec périmètre d'unité optionnel et dates, § 4.3, US-05).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(
		name = "rh_fonction_affectation",
		indexes = {
				@Index(name = "idx_rh_fonc_aff_societe_fonction", columnList = "societe_id, fonction_id"),
				@Index(name = "idx_rh_fonc_aff_societe_collab", columnList = "societe_id, collaborateur_id")
		})
public class FonctionAffectation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fonction_id", nullable = false)
	private Fonction fonction;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collaborateur_id", nullable = false)
	private Collaborateur collaborateur;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "unite_perimetre_id")
	private UniteOrganisation unitePerimetre;

	@Column(name = "du", nullable = false)
	private LocalDate du;

	@Column(name = "au")
	private LocalDate au;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe = Instant.now();

	protected FonctionAffectation() {
	}

	public FonctionAffectation(
			Fonction fonction,
			Collaborateur collaborateur,
			UniteOrganisation unitePerimetre,
			LocalDate du,
			LocalDate au,
			Instant creeLe) {
		this.fonction = fonction;
		this.collaborateur = collaborateur;
		this.unitePerimetre = unitePerimetre;
		this.du = du;
		this.au = au;
		this.creeLe = creeLe;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public Fonction getFonction() {
		return fonction;
	}

	public Collaborateur getCollaborateur() {
		return collaborateur;
	}

	public UniteOrganisation getUnitePerimetre() {
		return unitePerimetre;
	}

	public LocalDate getDu() {
		return du;
	}

	public LocalDate getAu() {
		return au;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public boolean estActiveA(LocalDate date) {
		return !du.isAfter(date) && (au == null || !au.isBefore(date));
	}
}
