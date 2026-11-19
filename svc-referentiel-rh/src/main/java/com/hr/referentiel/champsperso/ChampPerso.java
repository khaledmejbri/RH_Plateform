package com.hr.referentiel.champsperso;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Définition d'un champ personnalisé (ADR partie 2, P2-2). Le code est généré, immuable et jamais réattribué. */
@Entity
@Table(name = "rh_champ_perso", uniqueConstraints = @UniqueConstraint(name = "uk_rh_champ_perso_code",
		columnNames = {"type_import", "code"}))
public class ChampPerso {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "type_import", nullable = false, length = 20, updatable = false)
	private String typeImport;

	@Column(name = "code", nullable = false, length = 40, updatable = false)
	private String code;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "finalite", nullable = false, length = 200)
	private String finalite;

	@Convert(converter = TypeValeurPersoConverter.class)
	@Column(name = "type_valeur", nullable = false, length = 10)
	private TypeValeurPerso typeValeur;

	@Column(name = "longueur_max")
	private Integer longueurMax;

	@Column(name = "decimales")
	private Integer decimales;

	@Column(name = "minimum", precision = 15, scale = 4)
	private BigDecimal minimum;

	@Column(name = "maximum", precision = 15, scale = 4)
	private BigDecimal maximum;

	@Column(name = "date_min")
	private LocalDate dateMin;

	@Column(name = "date_max")
	private LocalDate dateMax;

	@Column(name = "visible_collaborateur", nullable = false)
	private boolean visibleCollaborateur;

	@Column(name = "actif", nullable = false)
	private boolean actif;

	@Column(name = "confirme_non_sensible_le", nullable = false, updatable = false)
	private Instant confirmeNonSensibleLe;

	@Column(name = "confirme_non_sensible_par", nullable = false, length = 160, updatable = false)
	private String confirmeNonSensiblePar;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "cree_par_nom", nullable = false, length = 160, updatable = false)
	private String creeParNom;

	protected ChampPerso() {
	}

	public ChampPerso(String typeImport, String code, String libelle, String finalite, TypeValeurPerso typeValeur,
			boolean visibleCollaborateur, Instant maintenant, String auteurNom) {
		this.id = UUID.randomUUID();
		this.typeImport = typeImport;
		this.code = code;
		this.libelle = libelle;
		this.finalite = finalite;
		this.typeValeur = typeValeur;
		this.visibleCollaborateur = visibleCollaborateur;
		this.actif = true;
		this.confirmeNonSensibleLe = maintenant;
		this.confirmeNonSensiblePar = auteurNom;
		this.creeLe = maintenant;
		this.creeParNom = auteurNom;
	}

	/** Règles chiffrées du type courant ; les autres sont remises à null. */
	public void regler(ReglesPerso r) {
		this.longueurMax = r.longueurMax();
		this.decimales = r.decimales();
		this.minimum = r.minimum();
		this.maximum = r.maximum();
		this.dateMin = r.dateMin();
		this.dateMax = r.dateMax();
	}

	public ReglesPerso regles() {
		return new ReglesPerso(longueurMax, decimales, minimum, maximum, dateMin, dateMax);
	}

	public UUID getId() {
		return id;
	}

	public String getTypeImport() {
		return typeImport;
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

	public String getFinalite() {
		return finalite;
	}

	public void setFinalite(String finalite) {
		this.finalite = finalite;
	}

	public TypeValeurPerso getTypeValeur() {
		return typeValeur;
	}

	public void setTypeValeur(TypeValeurPerso typeValeur) {
		this.typeValeur = typeValeur;
	}

	public boolean isVisibleCollaborateur() {
		return visibleCollaborateur;
	}

	public void setVisibleCollaborateur(boolean v) {
		this.visibleCollaborateur = v;
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

	public String getCreeParNom() {
		return creeParNom;
	}

	public Instant getConfirmeNonSensibleLe() {
		return confirmeNonSensibleLe;
	}

	public String getConfirmeNonSensiblePar() {
		return confirmeNonSensiblePar;
	}
}
