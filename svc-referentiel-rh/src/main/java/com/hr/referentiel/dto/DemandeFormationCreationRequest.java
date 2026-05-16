package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class DemandeFormationCreationRequest {

	@NotBlank
	@JsonProperty("type_formation")
	private String typeFormation;

	@NotBlank
	@JsonProperty("organisme")
	private String organisme;

	@NotNull
	@Positive
	@JsonProperty("duree_heures")
	private Integer dureeHeures;

	@DecimalMin(value = "0.0", inclusive = true)
	@JsonProperty("cout_estime")
	private BigDecimal coutEstime;

	@NotBlank
	@JsonProperty("objectifs_pedagogiques")
	private String objectifsPedagogiques;

	@NotBlank
	@JsonProperty("justification")
	private String justification;

	@JsonProperty("date_souhaitee_debut")
	private LocalDate dateSouhaiteeDebut;

	@JsonProperty("date_souhaitee_fin")
	private LocalDate dateSouhaiteeFin;

	@JsonProperty("unite_cible_identifiant")
	private UUID uniteCibleIdentifiant;

	@JsonProperty("collaborateurs_cibles_identifiants")
	private Set<UUID> collaborateursCiblesIdentifiants = new LinkedHashSet<>();

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
	public UUID getUniteCibleIdentifiant() { return uniteCibleIdentifiant; }
	public void setUniteCibleIdentifiant(UUID uniteCibleIdentifiant) { this.uniteCibleIdentifiant = uniteCibleIdentifiant; }
	public Set<UUID> getCollaborateursCiblesIdentifiants() { return collaborateursCiblesIdentifiants; }
	public void setCollaborateursCiblesIdentifiants(Set<UUID> collaborateursCiblesIdentifiants) {
		this.collaborateursCiblesIdentifiants = collaborateursCiblesIdentifiants != null
				? collaborateursCiblesIdentifiants
				: new LinkedHashSet<>();
	}
}
