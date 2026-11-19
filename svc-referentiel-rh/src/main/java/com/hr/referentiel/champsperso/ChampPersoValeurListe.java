package com.hr.referentiel.champsperso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Valeur d'un champ de type liste : code généré immuable, ordre dense 0..n-1. */
@Entity
@Table(name = "rh_champ_perso_valeur_liste", uniqueConstraints = @UniqueConstraint(name = "uk_rh_champ_perso_valeur",
		columnNames = {"champ_perso_id", "code"}))
public class ChampPersoValeurListe {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "champ_perso_id", nullable = false, updatable = false)
	private UUID champPersoId;

	@Column(name = "code", nullable = false, length = 40, updatable = false)
	private String code;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	@Column(name = "actif", nullable = false)
	private boolean actif;

	protected ChampPersoValeurListe() {
	}

	public ChampPersoValeurListe(UUID champPersoId, String code, String libelle, int ordre) {
		this.id = UUID.randomUUID();
		this.champPersoId = champPersoId;
		this.code = code;
		this.libelle = libelle;
		this.ordre = ordre;
		this.actif = true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getChampPersoId() {
		return champPersoId;
	}

	public String getCode() {
		return code;
	}

	public String getLibelle() {
		return libelle;
	}

	public void setLibelle(String libelle) {
		this.libelle = libelle;
	}

	public int getOrdre() {
		return ordre;
	}

	public void setOrdre(int ordre) {
		this.ordre = ordre;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}
}
