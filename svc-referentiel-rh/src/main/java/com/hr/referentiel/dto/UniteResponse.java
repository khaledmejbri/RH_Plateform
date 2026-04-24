package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public class UniteResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("code")
	private String code;

	@JsonProperty("libelle")
	private String libelle;

	@JsonProperty("parent_identifiant")
	private UUID parentIdentifiant;

	@JsonProperty("actif")
	private boolean actif;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	public UniteResponse() {
	}

	public UniteResponse(UUID identifiant, String code, String libelle, UUID parentIdentifiant,
			boolean actif, Instant creeLe, Instant modifieLe) {
		this.identifiant = identifiant;
		this.code = code;
		this.libelle = libelle;
		this.parentIdentifiant = parentIdentifiant;
		this.actif = actif;
		this.creeLe = creeLe;
		this.modifieLe = modifieLe;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public void setIdentifiant(UUID identifiant) {
		this.identifiant = identifiant;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLibelle() {
		return libelle;
	}

	public void setLibelle(String libelle) {
		this.libelle = libelle;
	}

	public UUID getParentIdentifiant() {
		return parentIdentifiant;
	}

	public void setParentIdentifiant(UUID parentIdentifiant) {
		this.parentIdentifiant = parentIdentifiant;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
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
