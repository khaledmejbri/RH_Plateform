package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public class CollaborateurMiseAJourRequest {

	@Size(max = 120)
	@JsonProperty("prenom")
	private String prenom;

	@Size(max = 120)
	@JsonProperty("nom")
	private String nom;

	@Size(max = 255)
	@JsonProperty("courriel_professionnel")
	private String courrielProfessionnel;

	@Size(max = 255)
	@JsonProperty("poste_libelle")
	private String posteLibelle;

	@Size(max = 255)
	@JsonProperty("fonction")
	private String fonction;

	@Size(max = 500)
	@JsonProperty("qualification_affectation")
	private String qualificationAffectation;

	@Size(max = 255)
	@JsonProperty("qualite")
	private String qualite;

	@Size(max = 500)
	@JsonProperty("affectation")
	private String affectation;

	@Size(max = 255)
	@JsonProperty("departement_libelle")
	private String departementLibelle;

	@JsonProperty("date_recrutement")
	private LocalDate dateRecrutement;

	@Size(max = 32)
	@JsonProperty("statut")
	private String statut;

	@JsonProperty("unite_identifiant")
	private UUID uniteIdentifiant;

	@JsonProperty("superieur_identifiant")
	private UUID superieurIdentifiant;

	@JsonProperty("compte_utilisateur_id")
	private UUID compteUtilisateurId;

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

	public String getStatut() {
		return statut;
	}

	public void setStatut(String statut) {
		this.statut = statut;
	}

	public UUID getUniteIdentifiant() {
		return uniteIdentifiant;
	}

	public void setUniteIdentifiant(UUID uniteIdentifiant) {
		this.uniteIdentifiant = uniteIdentifiant;
	}

	public UUID getSuperieurIdentifiant() {
		return superieurIdentifiant;
	}

	public void setSuperieurIdentifiant(UUID superieurIdentifiant) {
		this.superieurIdentifiant = superieurIdentifiant;
	}

	public UUID getCompteUtilisateurId() {
		return compteUtilisateurId;
	}

	public void setCompteUtilisateurId(UUID compteUtilisateurId) {
		this.compteUtilisateurId = compteUtilisateurId;
	}
}
