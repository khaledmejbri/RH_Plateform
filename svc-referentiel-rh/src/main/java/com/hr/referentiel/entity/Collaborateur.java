package com.hr.referentiel.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Données collaborateur alignées sur le cahier des charges RH-Évènement (fiches agent, pointage,
 * besoin en recrutement, EPI, évaluations) : matricule, identité, fonction, poste, qualification,
 * affectation, département, qualité métier, date de recrutement, rattachement organisationnel.
 */
@Entity
@Table(name = "rh_collaborateur", indexes = {
		@Index(name = "idx_collab_matricule", columnList = "matricule", unique = true),
		@Index(name = "idx_collab_unite", columnList = "unite_identifiant"),
		@Index(name = "idx_collab_statut", columnList = "statut")
})
public class Collaborateur {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "matricule", nullable = false, unique = true, length = 64)
	private String matricule;

	@Column(name = "prenom", nullable = false, length = 120)
	private String prenom;

	@Column(name = "nom", nullable = false, length = 120)
	private String nom;

	@Column(name = "courriel_professionnel", length = 255)
	private String courrielProfessionnel;

	@Column(name = "poste_libelle", length = 255)
	private String posteLibelle;

	/** Fiche agent / évaluation (CDC) — ex. intitulé de fonction. */
	@Column(name = "fonction", length = 255)
	private String fonction;

	/** Annexe besoin en recrutement — « Qualification et affectation ». */
	@Column(name = "qualification_affectation", length = 500)
	private String qualificationAffectation;

	/** Feuille de pointage — colonne « Qualité » (métier / catégorie courte). */
	@Column(name = "qualite", length = 255)
	private String qualite;

	/** Formulaires CDC — affectation / lieu de travail (texte libre ou complément au service). */
	@Column(name = "affectation", length = 500)
	private String affectation;

	/** Feuille de pointage — « Département » (libellé ; complément à l’unité organisationnelle). */
	@Column(name = "departement_libelle", length = 255)
	private String departementLibelle;

	/** Fiches évaluation / agent — « Date de Recrutement ». */
	@Column(name = "date_recrutement")
	private LocalDate dateRecrutement;

	/** Identifiant utilisateur (JWT claim identifiant_utilisateur) — liaison compte ↔ collaborateur. */
	@Column(name = "compte_utilisateur_id", unique = true)
	private UUID compteUtilisateurId;

	@Column(name = "statut", nullable = false, length = 32)
	private String statut;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unite_identifiant", nullable = false)
	private UniteOrganisation unite;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "superieur_identifiant")
	private Collaborateur superieur;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public Collaborateur() {
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

	public String getMatricule() {
		return matricule;
	}

	public void setMatricule(String matricule) {
		this.matricule = matricule;
	}

	public String getPrenom() {
		return prenom;
	}

	public void setPrenom(String prenom) {
		this.prenom = prenom;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	public String getCourrielProfessionnel() {
		return courrielProfessionnel;
	}

	public void setCourrielProfessionnel(String courrielProfessionnel) {
		this.courrielProfessionnel = courrielProfessionnel;
	}

	public String getPosteLibelle() {
		return posteLibelle;
	}

	public void setPosteLibelle(String posteLibelle) {
		this.posteLibelle = posteLibelle;
	}

	public String getFonction() {
		return fonction;
	}

	public void setFonction(String fonction) {
		this.fonction = fonction;
	}

	public String getQualificationAffectation() {
		return qualificationAffectation;
	}

	public void setQualificationAffectation(String qualificationAffectation) {
		this.qualificationAffectation = qualificationAffectation;
	}

	public String getQualite() {
		return qualite;
	}

	public void setQualite(String qualite) {
		this.qualite = qualite;
	}

	public String getAffectation() {
		return affectation;
	}

	public void setAffectation(String affectation) {
		this.affectation = affectation;
	}

	public String getDepartementLibelle() {
		return departementLibelle;
	}

	public void setDepartementLibelle(String departementLibelle) {
		this.departementLibelle = departementLibelle;
	}

	public LocalDate getDateRecrutement() {
		return dateRecrutement;
	}

	public void setDateRecrutement(LocalDate dateRecrutement) {
		this.dateRecrutement = dateRecrutement;
	}

	public UUID getCompteUtilisateurId() {
		return compteUtilisateurId;
	}

	public void setCompteUtilisateurId(UUID compteUtilisateurId) {
		this.compteUtilisateurId = compteUtilisateurId;
	}

	public String getStatut() {
		return statut;
	}

	public void setStatut(String statut) {
		this.statut = statut;
	}

	public UniteOrganisation getUnite() {
		return unite;
	}

	public void setUnite(UniteOrganisation unite) {
		this.unite = unite;
	}

	public Collaborateur getSuperieur() {
		return superieur;
	}

	public void setSuperieur(Collaborateur superieur) {
		this.superieur = superieur;
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
