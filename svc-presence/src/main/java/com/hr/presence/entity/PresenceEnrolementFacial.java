package com.hr.presence.entity;

import com.hr.presence.domain.StatutEnrolement;
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

/**
 * Enrôlement facial d'un compte (une ligne par compte). Aucune donnée biométrique : ni image, ni gabarit, ni vecteur
 * (C-L-04) ; seulement l'état, l'appareil, la version du consentement et le compteur d'échecs.
 */
@Entity
@Table(
		name = "presence_enrolement_facial",
		uniqueConstraints = @UniqueConstraint(name = "uk_presence_enrolement_compte", columnNames = "compte_id"),
		indexes = @Index(name = "idx_presence_enrolement_statut", columnList = "statut")
)
public class PresenceEnrolementFacial {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "compte_id", nullable = false, updatable = false, length = 64)
	private String compteId;

	/** Fiche collaborateur (référentiel). */
	@Column(name = "collaborateur_id", nullable = false, length = 64)
	private String collaborateurId;

	@Column(name = "collaborateur_nom", length = 255)
	private String collaborateurNom;

	/** Instantané D-H03 pris au dernier appel du collaborateur. */
	@Column(name = "valideur_id", length = 64)
	private String valideurId;

	@Column(name = "valideur_compte_id", length = 64)
	private String valideurCompteId;

	@Column(name = "valideur_nom", length = 255)
	private String valideurNom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private StatutEnrolement statut = StatutEnrolement.NON_ENROLE;

	/** Appareil enrôlé actif (conservé pendant A_REENROLER). */
	@Column(name = "device_id", length = 128)
	private String deviceId;

	/** Nouvel appareil ayant demandé le ré-enrôlement. */
	@Column(name = "device_id_demande", length = 128)
	private String deviceIdDemande;

	@Column(name = "device_id_incompatible", length = 128)
	private String deviceIdIncompatible;

	/** Version du consentement acceptée ou refusée. */
	@Column(name = "version_consentement", length = 32)
	private String versionConsentement;

	@Column(name = "modele_version", length = 32)
	private String modeleVersion;

	@Column(name = "idempotency_key_enrolement", length = 64)
	private String idempotencyKeyEnrolement;

	@Column(name = "decide_le")
	private Instant decideLe;

	@Column(name = "enrole_le")
	private Instant enroleLe;

	@Column(name = "demande_reenrolement_le")
	private Instant demandeReenrolementLe;

	@Column(name = "echecs_consecutifs", nullable = false, columnDefinition = "integer default 0")
	private int echecsConsecutifs;

	@Column(name = "dernier_echec_le")
	private Instant dernierEchecLe;

	@Column(name = "bloque_jusqu_a")
	private Instant bloqueJusqua;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant maintenant = Instant.now();
		if (creeLe == null) {
			creeLe = maintenant;
		}
		if (modifieLe == null) {
			modifieLe = maintenant;
		}
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getCompteId() { return compteId; }
	public void setCompteId(String compteId) { this.compteId = compteId; }
	public String getCollaborateurId() { return collaborateurId; }
	public void setCollaborateurId(String collaborateurId) { this.collaborateurId = collaborateurId; }
	public String getCollaborateurNom() { return collaborateurNom; }
	public void setCollaborateurNom(String collaborateurNom) { this.collaborateurNom = collaborateurNom; }
	public String getValideurId() { return valideurId; }
	public void setValideurId(String valideurId) { this.valideurId = valideurId; }
	public String getValideurCompteId() { return valideurCompteId; }
	public void setValideurCompteId(String valideurCompteId) { this.valideurCompteId = valideurCompteId; }
	public String getValideurNom() { return valideurNom; }
	public void setValideurNom(String valideurNom) { this.valideurNom = valideurNom; }
	public StatutEnrolement getStatut() { return statut; }
	public void setStatut(StatutEnrolement statut) { this.statut = statut; }
	public String getDeviceId() { return deviceId; }
	public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
	public String getDeviceIdDemande() { return deviceIdDemande; }
	public void setDeviceIdDemande(String deviceIdDemande) { this.deviceIdDemande = deviceIdDemande; }
	public String getDeviceIdIncompatible() { return deviceIdIncompatible; }
	public void setDeviceIdIncompatible(String deviceIdIncompatible) { this.deviceIdIncompatible = deviceIdIncompatible; }
	public String getVersionConsentement() { return versionConsentement; }
	public void setVersionConsentement(String versionConsentement) { this.versionConsentement = versionConsentement; }
	public String getModeleVersion() { return modeleVersion; }
	public void setModeleVersion(String modeleVersion) { this.modeleVersion = modeleVersion; }
	public String getIdempotencyKeyEnrolement() { return idempotencyKeyEnrolement; }
	public void setIdempotencyKeyEnrolement(String idempotencyKeyEnrolement) { this.idempotencyKeyEnrolement = idempotencyKeyEnrolement; }
	public Instant getDecideLe() { return decideLe; }
	public void setDecideLe(Instant decideLe) { this.decideLe = decideLe; }
	public Instant getEnroleLe() { return enroleLe; }
	public void setEnroleLe(Instant enroleLe) { this.enroleLe = enroleLe; }
	public Instant getDemandeReenrolementLe() { return demandeReenrolementLe; }
	public void setDemandeReenrolementLe(Instant demandeReenrolementLe) { this.demandeReenrolementLe = demandeReenrolementLe; }
	public int getEchecsConsecutifs() { return echecsConsecutifs; }
	public void setEchecsConsecutifs(int echecsConsecutifs) { this.echecsConsecutifs = echecsConsecutifs; }
	public Instant getDernierEchecLe() { return dernierEchecLe; }
	public void setDernierEchecLe(Instant dernierEchecLe) { this.dernierEchecLe = dernierEchecLe; }
	public Instant getBloqueJusqua() { return bloqueJusqua; }
	public void setBloqueJusqua(Instant bloqueJusqua) { this.bloqueJusqua = bloqueJusqua; }
	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
