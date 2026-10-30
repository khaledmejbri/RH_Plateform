package com.hr.presence.entity;

import com.hr.presence.domain.PlateformeMobile;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.VerificationFaciale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "presence_pointage",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_presence_pointage_collab_idem",
				columnNames = {"collaborateur_id", "idempotency_key"}
		),
		indexes = {
				@Index(name = "idx_presence_pointage_site_ts", columnList = "site_id,server_ts"),
				@Index(name = "idx_presence_pointage_collab_ts", columnList = "collaborateur_id,server_ts"),
				@Index(name = "idx_presence_pointage_statut_ts", columnList = "statut,server_ts")
		}
)
public class PresencePointage {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "collaborateur_id", nullable = false, length = 64)
	private String collaborateurId;

	@Column(name = "site_id")
	private UUID siteId;

	@Column(name = "qr_credential_id")
	private UUID qrCredentialId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointageType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private PointageStatut statut;

	@Column(name = "server_ts", nullable = false)
	private Instant serverTs;

	@Column(name = "client_ts")
	private Instant clientTs;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(name = "accuracy_metres")
	private Double accuracyMetres;

	@Column(name = "distance_metres")
	private Double distanceMetres;

	/** Demande manuelle à l'origine d'un pointage VALIDE_MANUEL (D-P04). */
	@Column(name = "demande_manuelle_id")
	private UUID demandeManuelleId;

	/** Indicateur OS « position simulée » envoyé par le mobile (D-P01). */
	@Column(name = "position_simulee")
	private Boolean positionSimulee;

	@Column(name = "device_id", length = 128)
	private String deviceId;

	@Column(name = "idempotency_key", length = 64)
	private String idempotencyKey;

	@Column(name = "motif_rejet", length = 255)
	private String motifRejet;

	/** Score facial 0-100 calculé sur l'appareil (D-P08) ; supprimé avec le pointage (C-L-07). Nul si aucun contrôle. */
	@Column(name = "score_facial", columnDefinition = "smallint")
	private Short scoreFacial;

	@Column(name = "vivacite_reussie")
	private Boolean vivaciteReussie;

	/** Indicateur d'un pointage VALIDE (D-P10.4) ; nul si aucun contrôle facial. */
	@Enumerated(EnumType.STRING)
	@Column(name = "verification_faciale", length = 16)
	private VerificationFaciale verificationFaciale;

	@Column(name = "modele_facial_version", length = 32)
	private String modeleFacialVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "plateforme", length = 16)
	private PlateformeMobile plateforme;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (serverTs == null) {
			serverTs = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCollaborateurId() {
		return collaborateurId;
	}

	public void setCollaborateurId(String collaborateurId) {
		this.collaborateurId = collaborateurId;
	}

	public UUID getSiteId() {
		return siteId;
	}

	public void setSiteId(UUID siteId) {
		this.siteId = siteId;
	}

	public UUID getQrCredentialId() {
		return qrCredentialId;
	}

	public void setQrCredentialId(UUID qrCredentialId) {
		this.qrCredentialId = qrCredentialId;
	}

	public PointageType getType() {
		return type;
	}

	public void setType(PointageType type) {
		this.type = type;
	}

	public PointageStatut getStatut() {
		return statut;
	}

	public void setStatut(PointageStatut statut) {
		this.statut = statut;
	}

	public Instant getServerTs() {
		return serverTs;
	}

	public void setServerTs(Instant serverTs) {
		this.serverTs = serverTs;
	}

	public Instant getClientTs() {
		return clientTs;
	}

	public void setClientTs(Instant clientTs) {
		this.clientTs = clientTs;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public Double getAccuracyMetres() {
		return accuracyMetres;
	}

	public void setAccuracyMetres(Double accuracyMetres) {
		this.accuracyMetres = accuracyMetres;
	}

	public Double getDistanceMetres() {
		return distanceMetres;
	}

	public void setDistanceMetres(Double distanceMetres) {
		this.distanceMetres = distanceMetres;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getMotifRejet() {
		return motifRejet;
	}

	public void setMotifRejet(String motifRejet) {
		this.motifRejet = motifRejet;
	}

	public Boolean getPositionSimulee() {
		return positionSimulee;
	}

	public void setPositionSimulee(Boolean positionSimulee) {
		this.positionSimulee = positionSimulee;
	}

	public UUID getDemandeManuelleId() {
		return demandeManuelleId;
	}

	public void setDemandeManuelleId(UUID demandeManuelleId) {
		this.demandeManuelleId = demandeManuelleId;
	}

	public Short getScoreFacial() {
		return scoreFacial;
	}

	public void setScoreFacial(Short scoreFacial) {
		this.scoreFacial = scoreFacial;
	}

	public Boolean getVivaciteReussie() {
		return vivaciteReussie;
	}

	public void setVivaciteReussie(Boolean vivaciteReussie) {
		this.vivaciteReussie = vivaciteReussie;
	}

	public VerificationFaciale getVerificationFaciale() {
		return verificationFaciale;
	}

	public void setVerificationFaciale(VerificationFaciale verificationFaciale) {
		this.verificationFaciale = verificationFaciale;
	}

	public String getModeleFacialVersion() {
		return modeleFacialVersion;
	}

	public void setModeleFacialVersion(String modeleFacialVersion) {
		this.modeleFacialVersion = modeleFacialVersion;
	}

	public PlateformeMobile getPlateforme() {
		return plateforme;
	}

	public void setPlateforme(PlateformeMobile plateforme) {
		this.plateforme = plateforme;
	}
}
