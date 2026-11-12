package com.hr.presence.entity;

import com.hr.presence.domain.NatureParametre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Habillage d'une valeur de référentiel de comportement (statut de pointage, de demande manuelle, d'heures sup) :
 * libellé, aide, couleur, ordre (ADR lot 3 § 2). Le code est immuable ; la ligne n'est ni créée ni supprimée par
 * l'utilisateur, elle est générée depuis les enums à chaque démarrage.
 */
@Entity
@Table(name = "presence_parametre_valeur",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_presence_param_code", columnNames = {"referentiel", "code"}),
				@UniqueConstraint(name = "uk_presence_param_libelle", columnNames = {"referentiel", "libelle_normalise"})},
		indexes = @Index(name = "idx_presence_param_ref", columnList = "referentiel, ordre"))
public class PresenceParametreValeur {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(nullable = false, length = 40)
	private String referentiel;

	@Column(nullable = false, length = 40)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private NatureParametre nature = NatureParametre.COMPORTEMENT;

	@Column(nullable = false, length = 80)
	private String libelle;

	@Column(name = "libelle_normalise", nullable = false, length = 80)
	private String libelleNormalise;

	@Column(length = 300)
	private String description;

	@Column(length = 16)
	private String couleur;

	@Column(nullable = false)
	private int ordre;

	@Column(nullable = false)
	private boolean actif = true;

	@Column(nullable = false)
	private boolean systeme = true;

	@Column(name = "premiere_utilisation_le")
	private Instant premiereUtilisationLe;

	@Version
	private Long version;

	@Column(name = "cree_le")
	private Instant creeLe = Instant.now();

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id", length = 64)
	private String modifieParCompteId;

	public UUID getId() { return id; }
	public String getReferentiel() { return referentiel; }
	public void setReferentiel(String referentiel) { this.referentiel = referentiel; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public NatureParametre getNature() { return nature; }
	public void setNature(NatureParametre nature) { this.nature = nature; }
	public String getLibelle() { return libelle; }
	public void setLibelle(String libelle) { this.libelle = libelle; }
	public String getLibelleNormalise() { return libelleNormalise; }
	public void setLibelleNormalise(String libelleNormalise) { this.libelleNormalise = libelleNormalise; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getCouleur() { return couleur; }
	public void setCouleur(String couleur) { this.couleur = couleur; }
	public int getOrdre() { return ordre; }
	public void setOrdre(int ordre) { this.ordre = ordre; }
	public boolean isActif() { return actif; }
	public void setActif(boolean actif) { this.actif = actif; }
	public boolean isSysteme() { return systeme; }
	public void setSysteme(boolean systeme) { this.systeme = systeme; }
	public Instant getPremiereUtilisationLe() { return premiereUtilisationLe; }
	public void setPremiereUtilisationLe(Instant premiereUtilisationLe) { this.premiereUtilisationLe = premiereUtilisationLe; }
	public Long getVersion() { return version; }
	public Instant getCreeLe() { return creeLe; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
	public String getModifieParCompteId() { return modifieParCompteId; }
	public void setModifieParCompteId(String modifieParCompteId) { this.modifieParCompteId = modifieParCompteId; }
}
