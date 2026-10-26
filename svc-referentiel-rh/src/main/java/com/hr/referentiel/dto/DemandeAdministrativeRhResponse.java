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

	public String getDemandeurUnite() {
		return demandeurUnite;
	}

	public void setDemandeurUnite(String demandeurUnite) {
		this.demandeurUnite = demandeurUnite;
	}
}
