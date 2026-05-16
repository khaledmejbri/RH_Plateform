package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.CibleDemandeFormationRh;
import com.hr.referentiel.domain.OrigineDemandeFormationRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public class DemandeFormationRhResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("demandeur_identifiant")
	private UUID demandeurIdentifiant;

	@JsonProperty("demandeur_nom")
	private String demandeurNom;

	@JsonProperty("origine")
	private OrigineDemandeFormationRh origine;

	@JsonProperty("cible")
	private CibleDemandeFormationRh cible;

	@JsonProperty("unite_cible_identifiant")
	private UUID uniteCibleIdentifiant;

	@JsonProperty("unite_cible_libelle")
	private String uniteCibleLibelle;

	@JsonProperty("collaborateurs_cibles_identifiants")
	private Set<UUID> collaborateursCiblesIdentifiants;

	@JsonProperty("type_formation")
	private String typeFormation;

	@JsonProperty("organisme")
	private String organisme;

	@JsonProperty("duree_heures")
	private Integer dureeHeures;

	@JsonProperty("cout_estime")
	private BigDecimal coutEstime;

	@JsonProperty("objectifs_pedagogiques")
	private String objectifsPedagogiques;

	@JsonProperty("justification")
	private String justification;

	@JsonProperty("date_souhaitee_debut")
	private LocalDate dateSouhaiteeDebut;

	@JsonProperty("date_souhaitee_fin")
	private LocalDate dateSouhaiteeFin;

	@JsonProperty("statut")
	private StatutDemandeFormationRh statut;

	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	public UUID getIdentifiant() { return identifiant; }
	public void setIdentifiant(UUID identifiant) { this.identifiant = identifiant; }
	public UUID getDemandeurIdentifiant() { return demandeurIdentifiant; }
	public void setDemandeurIdentifiant(UUID demandeurIdentifiant) { this.demandeurIdentifiant = demandeurIdentifiant; }
	public String getDemandeurNom() { return demandeurNom; }
	public void setDemandeurNom(String demandeurNom) { this.demandeurNom = demandeurNom; }
	public OrigineDemandeFormationRh getOrigine() { return origine; }
	public void setOrigine(OrigineDemandeFormationRh origine) { this.origine = origine; }
	public CibleDemandeFormationRh getCible() { return cible; }
	public void setCible(CibleDemandeFormationRh cible) { this.cible = cible; }
	public UUID getUniteCibleIdentifiant() { return uniteCibleIdentifiant; }
	public void setUniteCibleIdentifiant(UUID uniteCibleIdentifiant) { this.uniteCibleIdentifiant = uniteCibleIdentifiant; }
	public String getUniteCibleLibelle() { return uniteCibleLibelle; }
	public void setUniteCibleLibelle(String uniteCibleLibelle) { this.uniteCibleLibelle = uniteCibleLibelle; }
	public Set<UUID> getCollaborateursCiblesIdentifiants() { return collaborateursCiblesIdentifiants; }
	public void setCollaborateursCiblesIdentifiants(Set<UUID> collaborateursCiblesIdentifiants) {
		this.collaborateursCiblesIdentifiants = collaborateursCiblesIdentifiants;
	}
	public String getTypeFormation() { return typeFormation; }
	public void setTypeFormation(String typeFormation) { this.typeFormation = typeFormation; }
	public String getOrganisme() { return organisme; }
	public void setOrganisme(String organisme) { this.organisme = organisme; }
	public Integer getDureeHeures() { return dureeHeures; }
	public void setDureeHeures(Integer dureeHeures) { this.dureeHeures = dureeHeures; }
	public BigDecimal getCoutEstime() { return coutEstime; }
	public void setCoutEstime(BigDecimal coutEstime) { this.coutEstime = coutEstime; }
	public String getObjectifsPedagogiques() { return objectifsPedagogiques; }
	public void setObjectifsPedagogiques(String objectifsPedagogiques) { this.objectifsPedagogiques = objectifsPedagogiques; }
	public String getJustification() { return justification; }
	public void setJustification(String justification) { this.justification = justification; }
	public LocalDate getDateSouhaiteeDebut() { return dateSouhaiteeDebut; }
	public void setDateSouhaiteeDebut(LocalDate dateSouhaiteeDebut) { this.dateSouhaiteeDebut = dateSouhaiteeDebut; }
	public LocalDate getDateSouhaiteeFin() { return dateSouhaiteeFin; }
	public void setDateSouhaiteeFin(LocalDate dateSouhaiteeFin) { this.dateSouhaiteeFin = dateSouhaiteeFin; }
	public StatutDemandeFormationRh getStatut() { return statut; }
	public void setStatut(StatutDemandeFormationRh statut) { this.statut = statut; }
	public String getCommentaireRh() { return commentaireRh; }
	public void setCommentaireRh(String commentaireRh) { this.commentaireRh = commentaireRh; }
	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
