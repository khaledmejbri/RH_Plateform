package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Fonction ou rôle métier configurable par société (§ 4.3, US-05).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(
		name = "rh_fonction",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_rh_fonction_societe_code", columnNames = {"societe_id", "code"}),
				@UniqueConstraint(name = "uk_rh_fonction_societe_libelle", columnNames = {"societe_id", "libelle_normalise"})
		},
		indexes = {
				@Index(name = "idx_rh_fonction_societe_actif", columnList = "societe_id, actif")
		})
public class Fonction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@Column(name = "code", nullable = false, updatable = false, length = 40)
	private String code;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "libelle_normalise", nullable = false, length = 120)
	private String libelleNormalise;

	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	@Column(name = "utilisee", nullable = false)
	private boolean utilisee = false;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe = Instant.now();

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe = Instant.now();

	protected Fonction() {
	}

	public Fonction(String code, String libelle, String libelleNormalise, String description, Instant maintenant) {
		this.code = code;
		this.libelle = libelle;
		this.libelleNormalise = libelleNormalise;
		this.description = description;
		this.creeLe = maintenant;
		this.modifieLe = maintenant;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSocieteId() {
		return societeId;
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

	public String getLibelleNormalise() {
		return libelleNormalise;
	}

	public void setLibelleNormalise(String libelleNormalise) {
		this.libelleNormalise = libelleNormalise;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public boolean isUtilisee() {
		return utilisee;
	}

	public void setUtilisee(boolean utilisee) {
		this.utilisee = utilisee;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public void setModifieLe(Instant modifieLe) {
		this.modifieLe = modifieLe;
	}
}
