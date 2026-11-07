package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;

import java.util.List;
import java.util.UUID;

/**
 * Suivi du workflow M01 : employé → responsable de service (si snapshot) → RRH → clôture.
 */
public class DemandeAdministrativeSuiviResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	/** D-L02 : numéro lisible (DEM-/DOC-/FOR-AAAAMM-NNNN). */
	@JsonProperty("numero")
	private String numero;

	@JsonProperty("type_demande")
	private TypeDemandeAdministrativeRh typeDemande;

	@JsonProperty("statut")
	private StatutDemandeAdministrativeRh statut;

	@JsonProperty("etape_superieur_requise")
	private boolean etapeSuperieurRequise;

	@JsonProperty("valideur_attendu_identifiant")
	private UUID valideurAttenduIdentifiant;

	@JsonProperty("valideur_attendu_matricule")
	private String valideurAttenduMatricule;

	@JsonProperty("valideur_attendu_nom_complet")
	private String valideurAttenduNomComplet;

	@JsonProperty("valideur_attendu_libelle_role")
	private String valideurAttenduLibelleRole;

	@JsonProperty("message_explication")
	private String messageExplication;

	@JsonProperty("etapes")
	private List<WorkflowEtapeResponse> etapes;

	public DemandeAdministrativeSuiviResponse() {
	}

	public DemandeAdministrativeSuiviResponse(UUID identifiant, TypeDemandeAdministrativeRh typeDemande,
			StatutDemandeAdministrativeRh statut, boolean etapeSuperieurRequise,
			List<WorkflowEtapeResponse> etapes) {
		this.identifiant = identifiant;
		this.typeDemande = typeDemande;
		this.statut = statut;
		this.etapeSuperieurRequise = etapeSuperieurRequise;
		this.etapes = etapes;
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

	public void setIdentifiant(UUID identifiant) {
		this.identifiant = identifiant;
	}

	public TypeDemandeAdministrativeRh getTypeDemande() {
		return typeDemande;
	}

	public void setTypeDemande(TypeDemandeAdministrativeRh typeDemande) {
		this.typeDemande = typeDemande;
	}

	public StatutDemandeAdministrativeRh getStatut() {
		return statut;
	}

	public void setStatut(StatutDemandeAdministrativeRh statut) {
		this.statut = statut;
	}

	public boolean isEtapeSuperieurRequise() {
		return etapeSuperieurRequise;
	}

	public void setEtapeSuperieurRequise(boolean etapeSuperieurRequise) {
		this.etapeSuperieurRequise = etapeSuperieurRequise;
	}

	public UUID getValideurAttenduIdentifiant() {
		return valideurAttenduIdentifiant;
	}

	public void setValideurAttenduIdentifiant(UUID valideurAttenduIdentifiant) {
		this.valideurAttenduIdentifiant = valideurAttenduIdentifiant;
	}

	public String getValideurAttenduMatricule() {
		return valideurAttenduMatricule;
	}

	public void setValideurAttenduMatricule(String valideurAttenduMatricule) {
		this.valideurAttenduMatricule = valideurAttenduMatricule;
	}

	public String getValideurAttenduNomComplet() {
		return valideurAttenduNomComplet;
	}

	public void setValideurAttenduNomComplet(String valideurAttenduNomComplet) {
		this.valideurAttenduNomComplet = valideurAttenduNomComplet;
	}

	public String getValideurAttenduLibelleRole() {
		return valideurAttenduLibelleRole;
	}

	public void setValideurAttenduLibelleRole(String valideurAttenduLibelleRole) {
		this.valideurAttenduLibelleRole = valideurAttenduLibelleRole;
	}

	public String getMessageExplication() {
		return messageExplication;
	}

	public void setMessageExplication(String messageExplication) {
		this.messageExplication = messageExplication;
	}

	public List<WorkflowEtapeResponse> getEtapes() {
		return etapes;
	}

	public void setEtapes(List<WorkflowEtapeResponse> etapes) {
		this.etapes = etapes;
	}
}
