package com.hr.referentiel.entity;

import com.hr.referentiel.domain.NatureParametre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Valeur d'un référentiel paramétrable (ADR-UAT-lot3 § 2). Le {@code code} est immuable (D-R02.a) ; l'unicité du
 * libellé (D-R02.b) porte sur {@code libelle_normalise}, valeurs actives et inactives.
 */
@Entity
@Table(name = "rh_parametre_valeur", uniqueConstraints = {
		@UniqueConstraint(name = "uk_rh_parametre_ref_code", columnNames = {"referentiel", "code"}),
		@UniqueConstraint(name = "uk_rh_parametre_ref_libelle", columnNames = {"referentiel", "libelle_normalise"})
})
public class ParametreValeur {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "referentiel", nullable = false, length = 40, updatable = false)
	private String referentiel;

	@Column(name = "code", nullable = false, length = 40, updatable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "nature", nullable = false, length = 12, updatable = false)
	private NatureParametre nature = NatureParametre.VALEUR;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "libelle_normalise", nullable = false, length = 80)
	private String libelleNormalise;

	@Column(name = "description", length = 300)
	private String description;

	/** Code de palette ; null = NEUTRE. */
	@Column(name = "couleur", length = 16)
	private String couleur;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	/** Cadenas : jamais supprimée, désactivation interdite. */
	@Column(name = "systeme", nullable = false)
	private boolean systeme;

	/** Marque collante « déjà utilisée » (§ 6) : posée une fois, jamais retirée. */
	@Column(name = "premiere_utilisation_le")
	private Instant premiereUtilisationLe;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	/** TYPE_DOCUMENT : délai de traitement en heures (1 à 720). */
	@Column(name = "sla_heures")
	private Integer slaHeures;

	/** TYPE_CONGE : pièce jointe exigée à la demande. */
	@Column(name = "piece_jointe_obligatoire")
	private Boolean pieceJointeObligatoire;

	/** TYPE_CONGE : exigence non modifiable (MALADIE, MATERNITE). */
	@Column(name = "piece_jointe_verrouillee")
	private Boolean pieceJointeVerrouillee;

	public ParametreValeur() {
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getReferentiel() {
		return referentiel;
	}

	public void setReferentiel(String referentiel) {
		this.referentiel = referentiel;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public NatureParametre getNature() {
		return nature;
	}

	public void setNature(NatureParametre nature) {
		this.nature = nature;
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

	public String getCouleur() {
		return couleur;
	}

	public void setCouleur(String couleur) {
		this.couleur = couleur;
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

	public boolean isSysteme() {
		return systeme;
	}

	public void setSysteme(boolean systeme) {
		this.systeme = systeme;
	}

	public Instant getPremiereUtilisationLe() {
		return premiereUtilisationLe;
	}

	public void setPremiereUtilisationLe(Instant premiereUtilisationLe) {
		this.premiereUtilisationLe = premiereUtilisationLe;
	}

	public long getVersion() {
		return version == null ? 0L : version;
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

	public UUID getModifieParCompteId() {
		return modifieParCompteId;
	}

	public void setModifieParCompteId(UUID modifieParCompteId) {
		this.modifieParCompteId = modifieParCompteId;
	}

	public Integer getSlaHeures() {
		return slaHeures;
	}

	public void setSlaHeures(Integer slaHeures) {
		this.slaHeures = slaHeures;
	}

	public Boolean getPieceJointeObligatoire() {
		return pieceJointeObligatoire;
	}

	public void setPieceJointeObligatoire(Boolean pieceJointeObligatoire) {
		this.pieceJointeObligatoire = pieceJointeObligatoire;
	}

	public Boolean getPieceJointeVerrouillee() {
		return pieceJointeVerrouillee;
	}

	public void setPieceJointeVerrouillee(Boolean pieceJointeVerrouillee) {
		this.pieceJointeVerrouillee = pieceJointeVerrouillee;
	}
}
