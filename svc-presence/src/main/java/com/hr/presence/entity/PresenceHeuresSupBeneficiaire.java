package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** Bénéficiaire d'une demande : instantané lu au référentiel à la création (filtres, CSV, notifications). */
@Entity
@Table(name = "presence_heures_sup_beneficiaire",
		indexes = @Index(name = "idx_presence_hs_benef_demande", columnList = "demande_id"))
public class PresenceHeuresSupBeneficiaire {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "demande_id", nullable = false)
	private UUID demandeId;

	/** Fiche collaborateur. */
	@Column(name = "collaborateur_id", nullable = false)
	private UUID collaborateurId;

	@Column(name = "compte_id")
	private UUID compteId;

	@Column(length = 64)
	private String matricule;

	@Column(length = 255)
	private String nom;

	@Column(name = "direction_id")
	private UUID directionId;

	@Column(name = "direction_libelle", length = 255)
	private String directionLibelle;

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

	public UUID getCollaborateurId() {
		return collaborateurId;
	}

	public void setCollaborateurId(UUID collaborateurId) {
		this.collaborateurId = collaborateurId;
	}

	public UUID getCompteId() {
		return compteId;
	}

	public void setCompteId(UUID compteId) {
		this.compteId = compteId;
	}

	public String getMatricule() {
		return matricule;
	}

	public void setMatricule(String matricule) {
		this.matricule = matricule;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	public UUID getDirectionId() {
		return directionId;
	}

	public void setDirectionId(UUID directionId) {
		this.directionId = directionId;
	}

	public String getDirectionLibelle() {
		return directionLibelle;
	}

	public void setDirectionLibelle(String directionLibelle) {
		this.directionLibelle = directionLibelle;
	}
}
