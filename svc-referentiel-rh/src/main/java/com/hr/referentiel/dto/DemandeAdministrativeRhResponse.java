package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public class DemandeAdministrativeRhResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	/** D-L02 : numéro lisible (DEM-/DOC-/FOR-AAAAMM-NNNN). */
	@JsonProperty("numero")
	private String numero;

	@JsonProperty("type_demande")
	private TypeDemandeAdministrativeRh typeDemande;

	@JsonProperty("demandeur_identifiant")
	private UUID demandeurIdentifiant;

	/** SCRUM-15 : identité lisible du demandeur pour les écrans RH. */
	@JsonProperty("demandeur_nom")
	private String demandeurNom;

	@JsonProperty("demandeur_matricule")
	private String demandeurMatricule;

	@JsonProperty("demandeur_unite")
	private String demandeurUnite;

	@JsonProperty("statut")
	private StatutDemandeAdministrativeRh statut;

	/** Lot 3 : libellé courant du statut (paramétrable), lu dans l'instantané des paramètres. */
	@JsonProperty("statut_libelle")
	private String statutLibelle;

	/** Lot 3 : libellé courant du type de congé (demande de congé seulement), même désactivé. */
	@JsonProperty("type_conge_libelle")
	private String typeCongeLibelle;

	@JsonProperty("contenu")
	private Map<String, Object> contenu;

	@JsonProperty("periode_debut")
	private LocalDate periodeDebut;

	@JsonProperty("periode_fin")
	private LocalDate periodeFin;

	@JsonProperty("motif_refus")
	private String motifRefus;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	/** Snapshot M01 (H2 Should) — null si skip RRH. */
	@JsonProperty("valideur_attendu_identifiant")
	private UUID valideurAttenduIdentifiant;

	@JsonProperty("valideur_attendu_nom_complet")
	private String valideurAttenduNomComplet;

	public DemandeAdministrativeRhResponse() {
	}

	public DemandeAdministrativeRhResponse(UUID identifiant, TypeDemandeAdministrativeRh typeDemande,
			UUID demandeurIdentifiant, StatutDemandeAdministrativeRh statut, Map<String, Object> contenu,
			LocalDate periodeDebut, LocalDate periodeFin, String motifRefus, Instant creeLe, Instant modifieLe) {
		this.identifiant = identifiant;
		this.typeDemande = typeDemande;
		this.demandeurIdentifiant = demandeurIdentifiant;
		this.statut = statut;
		this.contenu = contenu;
		this.periodeDebut = periodeDebut;
		this.periodeFin = periodeFin;
		this.motifRefus = motifRefus;
		this.creeLe = creeLe;
		this.modifieLe = modifieLe;
	}

	public String getNumero() {
		return numero;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public TypeDemandeAdministrativeRh getTypeDemande() {
		return typeDemande;
	}

	public UUID getDemandeurIdentifiant() {
		return demandeurIdentifiant;
	}

	public StatutDemandeAdministrativeRh getStatut() {
		return statut;
	}

	public Map<String, Object> getContenu() {
		return contenu;
	}

	public LocalDate getPeriodeDebut() {
		return periodeDebut;
	}

	public LocalDate getPeriodeFin() {
		return periodeFin;
	}

	public String getMotifRefus() {
		return motifRefus;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public UUID getValideurAttenduIdentifiant() {
		return valideurAttenduIdentifiant;
	}

	public void setValideurAttenduIdentifiant(UUID valideurAttenduIdentifiant) {
		this.valideurAttenduIdentifiant = valideurAttenduIdentifiant;
	}

	public String getValideurAttenduNomComplet() {
		return valideurAttenduNomComplet;
	}

	public void setValideurAttenduNomComplet(String valideurAttenduNomComplet) {
		this.valideurAttenduNomComplet = valideurAttenduNomComplet;
	}

	public String getDemandeurNom() {
		return demandeurNom;
	}

	public void setDemandeurNom(String demandeurNom) {
		this.demandeurNom = demandeurNom;
	}

	public String getDemandeurMatricule() {
		return demandeurMatricule;
	}

	public void setDemandeurMatricule(String demandeurMatricule) {
		this.demandeurMatricule = demandeurMatricule;
	}

	public String getStatutLibelle() {
		return statutLibelle;
	}

	public void setStatutLibelle(String statutLibelle) {
		this.statutLibelle = statutLibelle;
	}

	public String getTypeCongeLibelle() {
		return typeCongeLibelle;
	}

	public void setTypeCongeLibelle(String typeCongeLibelle) {
		this.typeCongeLibelle = typeCongeLibelle;
	}

	public String getDemandeurUnite() {
		return demandeurUnite;
	}

	public void setDemandeurUnite(String demandeurUnite) {
		this.demandeurUnite = demandeurUnite;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("jours_decomptes")
	private java.math.BigDecimal joursDecomptes;

	public java.math.BigDecimal getJoursDecomptes() {
		return joursDecomptes;
	}

	public void setJoursDecomptes(java.math.BigDecimal joursDecomptes) {
		this.joursDecomptes = joursDecomptes;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("periode_premier_jour")
	private String periodePremierJour;

	public String getPeriodePremierJour() {
		return periodePremierJour;
	}

	public void setPeriodePremierJour(String periodePremierJour) {
		this.periodePremierJour = periodePremierJour;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("periode_premier_jour_libelle")
	private String periodePremierJourLibelle;

	public String getPeriodePremierJourLibelle() {
		return periodePremierJourLibelle;
	}

	public void setPeriodePremierJourLibelle(String periodePremierJourLibelle) {
		this.periodePremierJourLibelle = periodePremierJourLibelle;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("periode_dernier_jour")
	private String periodeDernierJour;

	public String getPeriodeDernierJour() {
		return periodeDernierJour;
	}

	public void setPeriodeDernierJour(String periodeDernierJour) {
		this.periodeDernierJour = periodeDernierJour;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("periode_dernier_jour_libelle")
	private String periodeDernierJourLibelle;

	public String getPeriodeDernierJourLibelle() {
		return periodeDernierJourLibelle;
	}

	public void setPeriodeDernierJourLibelle(String periodeDernierJourLibelle) {
		this.periodeDernierJourLibelle = periodeDernierJourLibelle;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("annulee_apres_approbation")
	private Boolean annuleeApresApprobation;

	public Boolean getAnnuleeApresApprobation() {
		return annuleeApresApprobation;
	}

	public void setAnnuleeApresApprobation(Boolean annuleeApresApprobation) {
		this.annuleeApresApprobation = annuleeApresApprobation;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("motif_annulation")
	private String motifAnnulation;

	public String getMotifAnnulation() {
		return motifAnnulation;
	}

	public void setMotifAnnulation(String motifAnnulation) {
		this.motifAnnulation = motifAnnulation;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("annule_apres_approbation_le")
	private Instant annuleApresApprobationLe;

	public Instant getAnnuleApresApprobationLe() {
		return annuleApresApprobationLe;
	}

	public void setAnnuleApresApprobationLe(Instant annuleApresApprobationLe) {
		this.annuleApresApprobationLe = annuleApresApprobationLe;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("annule_par_nom")
	private String annuleParNom;

	public String getAnnuleParNom() {
		return annuleParNom;
	}

	public void setAnnuleParNom(String annuleParNom) {
		this.annuleParNom = annuleParNom;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("jours_recredites")
	private java.math.BigDecimal joursRecredites;

	public java.math.BigDecimal getJoursRecredites() {
		return joursRecredites;
	}

	public void setJoursRecredites(java.math.BigDecimal joursRecredites) {
		this.joursRecredites = joursRecredites;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("peut_annuler_apres_approbation")
	private Boolean peutAnnulerApresApprobation;

	public Boolean getPeutAnnulerApresApprobation() {
		return peutAnnulerApresApprobation;
	}

	public void setPeutAnnulerApresApprobation(Boolean peutAnnulerApresApprobation) {
		this.peutAnnulerApresApprobation = peutAnnulerApresApprobation;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("solde_disponible_demandeur")
	private java.math.BigDecimal soldeDisponibleDemandeur;

	public java.math.BigDecimal getSoldeDisponibleDemandeur() {
		return soldeDisponibleDemandeur;
	}

	public void setSoldeDisponibleDemandeur(java.math.BigDecimal soldeDisponibleDemandeur) {
		this.soldeDisponibleDemandeur = soldeDisponibleDemandeur;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("solde_apres_demande")
	private java.math.BigDecimal soldeApresDemande;

	public java.math.BigDecimal getSoldeApresDemande() {
		return soldeApresDemande;
	}

	public void setSoldeApresDemande(java.math.BigDecimal soldeApresDemande) {
		this.soldeApresDemande = soldeApresDemande;
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("solde_reserve_autres_demandes")
	private java.math.BigDecimal soldeReserveAutresDemandes;

	public java.math.BigDecimal getSoldeReserveAutresDemandes() {
		return soldeReserveAutresDemandes;
	}

	public void setSoldeReserveAutresDemandes(java.math.BigDecimal soldeReserveAutresDemandes) {
		this.soldeReserveAutresDemandes = soldeReserveAutresDemandes;
	}

	/** Vrai seulement si l'appelant est le demandeur et la demande est encore en cours de validation (annulable par lui). */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@JsonProperty("peut_annuler")
	private Boolean peutAnnuler;

	public Boolean getPeutAnnuler() {
		return peutAnnuler;
	}

	public void setPeutAnnuler(Boolean peutAnnuler) {
		this.peutAnnuler = peutAnnuler;
	}
}
