package com.hr.referentiel.importation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Analyse d'import puis historique (5 ans, C-L-02). {@code typeImport} et {@code statut} sont des textes (aucune contrainte
 * CHECK). Les champs JSON sont stockés en texte. Le fichier brut n'est jamais écrit (C-L-03).
 */
@Entity
@Table(name = "rh_import", indexes = {
		@Index(name = "idx_rh_import_statut", columnList = "statut, derniere_action_le"),
		@Index(name = "idx_rh_import_type", columnList = "type_import, confirme_le")})
public class ImportAnalyse {

	public static final String STATUT_ANALYSEE = "ANALYSEE";
	public static final String STATUT_CONFIRMEE = "CONFIRMEE";
	public static final String TYPE_COLLABORATEURS = "COLLABORATEURS";
	public static final String TYPE_SOLDES = "SOLDES";

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "type_import", nullable = false, length = 20)
	private String typeImport;

	@Column(name = "statut", nullable = false, length = 20)
	private String statut;

	@Column(name = "auteur_compte_id", nullable = false)
	private UUID auteurCompteId;

	@Column(name = "auteur_fiche_id")
	private UUID auteurFicheId;

	@Column(name = "auteur_nom", length = 160)
	private String auteurNom;

	@Column(name = "nom_fichier", length = 255)
	private String nomFichier;

	@Column(name = "ligne_en_tete")
	private Integer ligneEnTete;

	/** JSON : {@code [{index, champ}]}. */
	@Column(name = "correspondance", columnDefinition = "text")
	private String correspondance;

	@Column(name = "creer_comptes")
	private Boolean creerComptes;

	@Column(name = "motif_global", length = 500)
	private String motifGlobal;

	/** JSON : erreurs et avertissements de fichier. */
	@Column(name = "erreurs_fichier", columnDefinition = "text")
	private String erreursFichier;

	/** JSON : totaux (analyse, puis compte rendu à la confirmation). */
	@Column(name = "totaux", columnDefinition = "text")
	private String totaux;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Column(name = "derniere_action_le", nullable = false)
	private Instant derniereActionLe;

	@Column(name = "analyse_le")
	private Instant analyseLe;

	@Column(name = "confirme_le")
	private Instant confirmeLe;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getTypeImport() {
		return typeImport;
	}

	public void setTypeImport(String typeImport) {
		this.typeImport = typeImport;
	}

	public String getStatut() {
		return statut;
	}

	public void setStatut(String statut) {
		this.statut = statut;
	}

	public UUID getAuteurCompteId() {
		return auteurCompteId;
	}

	public void setAuteurCompteId(UUID auteurCompteId) {
		this.auteurCompteId = auteurCompteId;
	}

	public UUID getAuteurFicheId() {
		return auteurFicheId;
	}

	public void setAuteurFicheId(UUID auteurFicheId) {
		this.auteurFicheId = auteurFicheId;
	}

	public String getAuteurNom() {
		return auteurNom;
	}

	public void setAuteurNom(String auteurNom) {
		this.auteurNom = auteurNom;
	}

	public String getNomFichier() {
		return nomFichier;
	}

	public void setNomFichier(String nomFichier) {
		this.nomFichier = nomFichier;
	}

	public Integer getLigneEnTete() {
		return ligneEnTete;
	}

	public void setLigneEnTete(Integer ligneEnTete) {
		this.ligneEnTete = ligneEnTete;
	}

	public String getCorrespondance() {
		return correspondance;
	}

	public void setCorrespondance(String correspondance) {
		this.correspondance = correspondance;
	}

	public Boolean getCreerComptes() {
		return creerComptes;
	}

	public void setCreerComptes(Boolean creerComptes) {
		this.creerComptes = creerComptes;
	}

	public String getMotifGlobal() {
		return motifGlobal;
	}

	public void setMotifGlobal(String motifGlobal) {
		this.motifGlobal = motifGlobal;
	}

	public String getErreursFichier() {
		return erreursFichier;
	}

	public void setErreursFichier(String erreursFichier) {
		this.erreursFichier = erreursFichier;
	}

	public String getTotaux() {
		return totaux;
	}

	public void setTotaux(String totaux) {
		this.totaux = totaux;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public void setCreeLe(Instant creeLe) {
		this.creeLe = creeLe;
	}

	public Instant getDerniereActionLe() {
		return derniereActionLe;
	}

	public void setDerniereActionLe(Instant derniereActionLe) {
		this.derniereActionLe = derniereActionLe;
	}

	public Instant getAnalyseLe() {
		return analyseLe;
	}

	public void setAnalyseLe(Instant analyseLe) {
		this.analyseLe = analyseLe;
	}

	public Instant getConfirmeLe() {
		return confirmeLe;
	}

	public void setConfirmeLe(Instant confirmeLe) {
		this.confirmeLe = confirmeLe;
	}
}
