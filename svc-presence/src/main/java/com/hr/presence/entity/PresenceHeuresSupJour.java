package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne par bénéficiaire x jour ouvert d'une demande EN_ATTENTE ou VALIDEE. La contrainte unique
 * (collaborateur, site, journée) est le garde-fou de concurrence de D-P13.f ; les lignes sont supprimées quand
 * la demande sort de ces deux statuts (ou quand le jour est retiré par un changement d'horaire).
 */
@Entity
@Table(name = "presence_heures_sup_jour",
		uniqueConstraints = @UniqueConstraint(name = "uk_presence_hs_jour_collab_site_journee",
				columnNames = {"collaborateur_id", "site_id", "journee"}),
		indexes = {
				@Index(name = "idx_presence_hs_jour_site_journee", columnList = "site_id,journee"),
				@Index(name = "idx_presence_hs_jour_demande", columnList = "demande_id")
		})
public class PresenceHeuresSupJour {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "demande_id", nullable = false)
	private UUID demandeId;

	@Column(name = "beneficiaire_id", nullable = false)
	private UUID beneficiaireId;

	/** Fiche du bénéficiaire (texte, comme presence_pointage.collaborateur_id). */
	@Column(name = "collaborateur_id", nullable = false, length = 64)
	private String collaborateurId;

	/** Compte du bénéficiaire : un pointage peut porter la fiche (en-tête) ou le compte (JWT). */
	@Column(name = "compte_id", length = 64)
	private String compteId;

	@Column(name = "site_id", nullable = false)
	private UUID siteId;

	@Column(nullable = false)
	private LocalDate journee;

	/** Direction du bénéficiaire à la création (filtre des heures comptées). */
	@Column(name = "direction_id")
	private UUID directionId;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getDemandeId() {
		return demandeId;
	}

	public void setDemandeId(UUID demandeId) {
		this.demandeId = demandeId;
	}

	public UUID getBeneficiaireId() {
		return beneficiaireId;
	}

	public void setBeneficiaireId(UUID beneficiaireId) {
		this.beneficiaireId = beneficiaireId;
	}

	public String getCollaborateurId() {
		return collaborateurId;
	}

	public void setCollaborateurId(String collaborateurId) {
		this.collaborateurId = collaborateurId;
	}

	public String getCompteId() {
		return compteId;
	}

	public void setCompteId(String compteId) {
		this.compteId = compteId;
	}

	public UUID getSiteId() {
		return siteId;
	}

	public void setSiteId(UUID siteId) {
		this.siteId = siteId;
	}

	public LocalDate getJournee() {
		return journee;
	}

	public void setJournee(LocalDate journee) {
		this.journee = journee;
	}

	public UUID getDirectionId() {
		return directionId;
	}

	public void setDirectionId(UUID directionId) {
		this.directionId = directionId;
	}
}
