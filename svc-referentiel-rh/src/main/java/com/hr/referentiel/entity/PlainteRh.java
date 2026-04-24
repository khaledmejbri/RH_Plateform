package com.hr.referentiel.entity;

import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rh_plainte", indexes = {
		@Index(name = "idx_plainte_auteur", columnList = "auteur_collaborateur_identifiant"),
		@Index(name = "idx_plainte_statut", columnList = "statut")
})
public class PlainteRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_plainte", nullable = false, length = 32)
	private TypePlainteRh typePlainte;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auteur_collaborateur_identifiant", nullable = false)
	private Collaborateur auteur;

	@Column(name = "titre", nullable = false, length = 255)
	private String titre;

	@Column(name = "description", nullable = false, length = 4000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 32)
	private StatutPlainteRh statut = StatutPlainteRh.NOUVEAU;

	@Column(name = "commentaire_rh", length = 2000)
	private String commentaireRh;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public PlainteRh() {
	}

	@PrePersist
	public void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		modifieLe = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public TypePlainteRh getTypePlainte() {
		return typePlainte;
	}

	public void setTypePlainte(TypePlainteRh typePlainte) {
		this.typePlainte = typePlainte;
	}

	public Collaborateur getAuteur() {
		return auteur;
	}

	public void setAuteur(Collaborateur auteur) {
		this.auteur = auteur;
	}

	public String getTitre() {
		return titre;
	}

	public void setTitre(String titre) {
		this.titre = titre;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public StatutPlainteRh getStatut() {
		return statut;
	}

	public void setStatut(StatutPlainteRh statut) {
		this.statut = statut;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public void setCommentaireRh(String commentaireRh) {
		this.commentaireRh = commentaireRh;
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
}
