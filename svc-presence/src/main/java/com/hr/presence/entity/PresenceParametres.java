package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Réglages numériques du pointage (D-R06) : ligne unique {@code id = 1}. Les colonnes sont nullables (une valeur
 * absente se lit comme le défaut du produit) ; la version est celle de la ligne entière.
 */
@Entity
@Table(name = "presence_parametres")
public class PresenceParametres {

	public static final int ID_UNIQUE = 1;

	@Id
	private Integer id = ID_UNIQUE;

	@Column(name = "seuil_pointages_manuels_mois")
	private Integer seuilPointagesManuelsMois;

	@Column(name = "delai_grace_retard_minutes")
	private Integer delaiGraceRetardMinutes;

	@Column(name = "rayon_defaut_exterieur_metres")
	private Integer rayonDefautExterieurMetres;

	@Column(name = "rayon_defaut_interieur_metres")
	private Integer rayonDefautInterieurMetres;

	@Version
	private Long version;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par", length = 64)
	private String modifiePar;

	public Integer getId() { return id; }
	public Integer getSeuilPointagesManuelsMois() { return seuilPointagesManuelsMois; }
	public void setSeuilPointagesManuelsMois(Integer v) { this.seuilPointagesManuelsMois = v; }
	public Integer getDelaiGraceRetardMinutes() { return delaiGraceRetardMinutes; }
	public void setDelaiGraceRetardMinutes(Integer v) { this.delaiGraceRetardMinutes = v; }
	public Integer getRayonDefautExterieurMetres() { return rayonDefautExterieurMetres; }
	public void setRayonDefautExterieurMetres(Integer v) { this.rayonDefautExterieurMetres = v; }
	public Integer getRayonDefautInterieurMetres() { return rayonDefautInterieurMetres; }
	public void setRayonDefautInterieurMetres(Integer v) { this.rayonDefautInterieurMetres = v; }
	public Long getVersion() { return version; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
	public String getModifiePar() { return modifiePar; }
	public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }
}
