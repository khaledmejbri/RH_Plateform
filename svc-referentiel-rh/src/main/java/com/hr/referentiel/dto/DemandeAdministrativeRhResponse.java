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
}
