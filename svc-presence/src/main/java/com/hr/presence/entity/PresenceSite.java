package com.hr.presence.entity;

import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.TypeLieu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "presence_site",
		uniqueConstraints = @UniqueConstraint(name = "uk_presence_site_code", columnNames = "code"),
		indexes = {
				@Index(name = "idx_presence_site_actif", columnList = "actif")
		}
)
public class PresenceSite {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 64)
	private String code;

	@Column(nullable = false, length = 255)
	private String libelle;

	@Column
	private Double latitude;

	@Column
	private Double longitude;

	@Column(name = "rayon_metres", nullable = false)
	private int rayonMetres = 50;

	/** NULL pour les sites antérieurs à D-P01 : lu comme {@link TypeLieu#EXTERIEUR}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "type_lieu", length = 16)
	private TypeLieu typeLieu;

	/** Précision (m) de la pose de l'emplacement, moyenne de plusieurs mesures (D-P03). */
	@Column(name = "precision_ancrage_metres")
	private Double precisionAncrageMetres;

	@Column(name = "nb_mesures_ancrage")
	private Integer nbMesuresAncrage;

	/** NULL pour les sites antérieurs à D-P05 : lu comme {@link ModeQr#PAPIER}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "mode_qr", length = 16)
	private ModeQr modeQr;

	/** SHA-256 du jeton de la borne écran ; NULL = pas de borne active (D-P05). */
	@Column(name = "borne_jeton_hash", length = 64)
	private String borneJetonHash;

	/** Incrémentée à chaque génération / révocation : invalide les QR de l'ancienne borne. */
	@Column(name = "borne_version")
	private Integer borneVersion;

	@Column(name = "borne_cree_le")
	private Instant borneCreeLe;

	@Column(nullable = false)
	private boolean actif = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", length = 100)
	private String updatedBy;

	@Column(name = "emplacement_updated_at")
	private Instant emplacementUpdatedAt;

	@Column(name = "emplacement_updated_by", length = 100)
	private String emplacementUpdatedBy;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ModeQr getModeQr() {
		return modeQr != null ? modeQr : ModeQr.PAPIER;
	}

	public void setModeQr(ModeQr modeQr) {
		this.modeQr = modeQr;
	}

	public String getBorneJetonHash() {
		return borneJetonHash;
	}

	public void setBorneJetonHash(String borneJetonHash) {
		this.borneJetonHash = borneJetonHash;
	}

	public int getBorneVersion() {
		return borneVersion != null ? borneVersion : 0;
	}

	public void setBorneVersion(int borneVersion) {
		this.borneVersion = borneVersion;
	}

	public Instant getBorneCreeLe() {
		return borneCreeLe;
	}

	public void setBorneCreeLe(Instant borneCreeLe) {
		this.borneCreeLe = borneCreeLe;
	}

	public TypeLieu getTypeLieu() {
		return typeLieu != null ? typeLieu : TypeLieu.EXTERIEUR;
	}

	public void setTypeLieu(TypeLieu typeLieu) {
		this.typeLieu = typeLieu;
	}

	public Double getPrecisionAncrageMetres() {
		return precisionAncrageMetres;
	}

	public void setPrecisionAncrageMetres(Double precisionAncrageMetres) {
		this.precisionAncrageMetres = precisionAncrageMetres;
	}

	public Integer getNbMesuresAncrage() {
		return nbMesuresAncrage;
	}

	public void setNbMesuresAncrage(Integer nbMesuresAncrage) {
		this.nbMesuresAncrage = nbMesuresAncrage;
	}

	public void setId(UUID id) {
		this.id = id;
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

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public int getRayonMetres() {
		return rayonMetres;
	}

	public void setRayonMetres(int rayonMetres) {
		this.rayonMetres = rayonMetres;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public Instant getEmplacementUpdatedAt() {
		return emplacementUpdatedAt;
	}

	public void setEmplacementUpdatedAt(Instant emplacementUpdatedAt) {
		this.emplacementUpdatedAt = emplacementUpdatedAt;
	}

	public String getEmplacementUpdatedBy() {
		return emplacementUpdatedBy;
	}

	public void setEmplacementUpdatedBy(String emplacementUpdatedBy) {
		this.emplacementUpdatedBy = emplacementUpdatedBy;
	}

	public boolean hasEmplacement() {
		return latitude != null && longitude != null;
	}
}
