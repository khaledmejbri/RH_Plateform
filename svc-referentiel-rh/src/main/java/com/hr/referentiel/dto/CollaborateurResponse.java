package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CollaborateurResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("matricule")
	private String matricule;

	@JsonProperty("prenom")
	private String prenom;

	@JsonProperty("nom")
	private String nom;

	@JsonProperty("courriel_professionnel")
	private String courrielProfessionnel;

	@JsonProperty("poste_libelle")
	private String posteLibelle;

	@JsonProperty("fonction")
	private String fonction;

	@JsonProperty("qualification_affectation")
	private String qualificationAffectation;

	@JsonProperty("qualite")
	private String qualite;

	@JsonProperty("affectation")
	private String affectation;

	@JsonProperty("departement_libelle")
	private String departementLibelle;

	@JsonProperty("date_recrutement")
	private LocalDate dateRecrutement;

	@JsonProperty("statut")
	private String statut;

	@JsonProperty("unite")
	private UniteResponse unite;

	@JsonProperty("superieur_identifiant")
	private UUID superieurIdentifiant;

	@JsonProperty("compte_utilisateur_id")
	private UUID compteUtilisateurId;

	@JsonProperty("profil_acces")
	private String profilAcces;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	public CollaborateurResponse() {
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public void setIdentifiant(UUID identifiant) {
		this.identifiant = identifiant;
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

	public String getStatut() {
		return statut;
	}

	public void setStatut(String statut) {
		this.statut = statut;
	}

	public UniteResponse getUnite() {
		return unite;
	}

	public void setUnite(UniteResponse unite) {
		this.unite = unite;
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

	public String getProfilAcces() {
		return profilAcces;
	}

	public void setProfilAcces(String profilAcces) {
		this.profilAcces = profilAcces;
	}
}