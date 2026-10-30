package com.hr.presence.entity;

import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutEnrolement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Trace d'audit facial : ajout seul (jamais modifiée ni supprimée), conservée emploi + 5 ans (C-L-02 / C-L-07). */
@Entity
@Immutable
@Table(
		name = "presence_audit_facial",
		indexes = @Index(name = "idx_presence_audit_facial_enrolement", columnList = "enrolement_id,horodatage")
)
public class PresenceAuditFacial {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	/** Enrôlement concerné ; nul pour les actions de réglage / de publication de texte. */
	@Column(name = "enrolement_id", updatable = false)
	private UUID enrolementId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 32)
	private ActionAuditFacial action;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut_avant", updatable = false, length = 16)
	private StatutEnrolement statutAvant;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut_apres", updatable = false, length = 16)
	private StatutEnrolement statutApres;

	@Column(name = "acteur_compte_id", length = 64, updatable = false)
	private String acteurCompteId;

	@Column(name = "acteur_nom", length = 255, updatable = false)
	private String acteurNom;

	@Column(name = "acteur_role", length = 32, updatable = false)
	private String acteurRole;

	@Column(name = "device_id", length = 128, updatable = false)
	private String deviceId;

	@Column(length = 32, updatable = false)
	private String version;

	@Column(length = 500, updatable = false)
	private String motif;

	@Column(nullable = false, updatable = false)
	private Instant horodatage;

	protected PresenceAuditFacial() {
	}

	public PresenceAuditFacial(ActionAuditFacial action, UUID enrolementId, StatutEnrolement statutAvant,
			StatutEnrolement statutApres, String acteurCompteId, String acteurNom, String acteurRole,
			String deviceId, String version, String motif, Instant horodatage) {
		this.action = action;
		this.enrolementId = enrolementId;
		this.statutAvant = statutAvant;
		this.statutApres = statutApres;
		this.acteurCompteId = acteurCompteId;
		this.acteurNom = acteurNom;
		this.acteurRole = acteurRole;
		this.deviceId = deviceId;
		this.version = version;
		this.motif = motif != null && motif.length() > 500 ? motif.substring(0, 500) : motif;
		this.horodatage = horodatage;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() { return id; }
	public UUID getEnrolementId() { return enrolementId; }
	public ActionAuditFacial getAction() { return action; }
	public StatutEnrolement getStatutAvant() { return statutAvant; }
	public StatutEnrolement getStatutApres() { return statutApres; }
	public String getActeurCompteId() { return acteurCompteId; }
	public String getActeurNom() { return acteurNom; }
	public String getActeurRole() { return acteurRole; }
	public String getDeviceId() { return deviceId; }
	public String getVersion() { return version; }
	public String getMotif() { return motif; }
	public Instant getHorodatage() { return horodatage; }
}
