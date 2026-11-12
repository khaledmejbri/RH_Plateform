package com.hr.presence.entity;

import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.SignalVigilance;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Demande de pointage manuel après un rejet (D-P04) : validée par le responsable D-H03, sinon par les RH.
 * Identités et noms figés à la création (snapshot référentiel).
 */
@Entity
@Table(
		name = "presence_demande_manuelle",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_demande_manuelle_rejet", columnNames = "pointage_rejete_id"),
				@UniqueConstraint(name = "uk_demande_manuelle_numero", columnNames = "numero")
		},
		indexes = {
				@Index(name = "idx_demande_manuelle_collab", columnList = "collaborateur_id,cree_le"),
				@Index(name = "idx_demande_manuelle_valideur", columnList = "valideur_id,statut"),
				@Index(name = "idx_demande_manuelle_statut", columnList = "statut,cree_le"),
				@Index(name = "idx_demande_manuelle_echeance", columnList = "statut,echeance")
		}
)
public class PresenceDemandeManuelle {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	/** Numéro lisible PM-AAAAMM-NNNN (D-L02) : attribué à la création, unique, immuable. Nul avant la reprise. */
	@Column(name = "numero", length = 32, updatable = false)
	private String numero;

	@Column(name = "pointage_rejete_id", nullable = false)
	private UUID pointageRejeteId;

	@Column(name = "collaborateur_id", nullable = false, length = 64)
	private String collaborateurId;

	@Column(name = "collaborateur_compte_id", length = 64)
	private String collaborateurCompteId;

	@Column(name = "collaborateur_nom", length = 200)
	private String collaborateurNom;

	@Column(name = "site_id")
	private UUID siteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointageType type;

	@Column(name = "heure_declaree", nullable = false)
	private Instant heureDeclaree;

	@Column(nullable = false, length = 500)
	private String justification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatutDemandeManuelle statut = StatutDemandeManuelle.EN_ATTENTE;

	/** Fiche du valideur attendu ; NULL = validation par les RH. */
	@Column(name = "valideur_id", length = 64)
	private String valideurId;

	@Column(name = "valideur_compte_id", length = 64)
	private String valideurCompteId;

	@Column(name = "valideur_nom", length = 200)
	private String valideurNom;

	@Column(name = "decide_par", length = 200)
	private String decidePar;

	@Column(name = "decide_le")
	private Instant decideLe;

	@Column(name = "motif_refus", length = 500)
	private String motifRefus;

	@Column(name = "pointage_id")
	private UUID pointageId;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	// ---- Données figées à la création (D-P26) : nulles pour les demandes antérieures ----

	/** Statut du refus d'origine (code PointageStatut), en texte : pas de CHECK à maintenir. */
	@Column(name = "refus_statut", length = 40)
	private String refusStatut;

	@Column(name = "distance_metres")
	private Double distanceMetres;

	@Column(name = "precision_metres")
	private Double precisionMetres;

	@Column(name = "rayon_metres")
	private Integer rayonMetres;

	/** PAPIER / ECRAN, en texte. */
	@Column(name = "mode_qr", length = 10)
	private String modeQr;

	/** Plafond de distance appliqué à la création ; nul pour une demande reprise (D-P26.a). */
	@Column(name = "plafond_metres")
	private Integer plafondMetres;

	/** Minutes entières, tronquées : création - tentative. */
	@Column(name = "delai_demande_minutes")
	private Long delaiDemandeMinutes;

	/** Instant à partir duquel la demande est expirée (tentative + délai de décision, ou démarrage + délai si reprise). */
	@Column(name = "echeance")
	private Instant echeance;

	@Convert(converter = SignauxVigilanceConverter.class)
	@Column(name = "signaux", length = 200)
	private List<SignalVigilance> signaux = new ArrayList<>();

	@Column(name = "commentaire_valideur", length = 500)
	private String commentaireValideur;

	/** Minutes entières, tronquées : décision - tentative. */
	@Column(name = "delai_decision_minutes")
	private Long delaiDecisionMinutes;

	/** Vrai si les données figées viennent de la reprise D-P26.a ; nul avant. */
	@Column(name = "reprise")
	private Boolean reprise;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	public boolean validationParRh() {
		return valideurId == null;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public String getNumero() { return numero; }
	public void setNumero(String numero) {
		if (this.numero != null && !this.numero.equals(numero)) {
			throw new IllegalStateException("Le numéro d'une demande est immuable");
		}
		this.numero = numero;
	}

	public UUID getPointageRejeteId() { return pointageRejeteId; }
	public void setPointageRejeteId(UUID pointageRejeteId) { this.pointageRejeteId = pointageRejeteId; }

	public String getCollaborateurId() { return collaborateurId; }
	public void setCollaborateurId(String collaborateurId) { this.collaborateurId = collaborateurId; }

	public String getCollaborateurCompteId() { return collaborateurCompteId; }
	public void setCollaborateurCompteId(String collaborateurCompteId) { this.collaborateurCompteId = collaborateurCompteId; }

	public String getCollaborateurNom() { return collaborateurNom; }
	public void setCollaborateurNom(String collaborateurNom) { this.collaborateurNom = collaborateurNom; }

	public UUID getSiteId() { return siteId; }
	public void setSiteId(UUID siteId) { this.siteId = siteId; }

	public PointageType getType() { return type; }
	public void setType(PointageType type) { this.type = type; }

	public Instant getHeureDeclaree() { return heureDeclaree; }
	public void setHeureDeclaree(Instant heureDeclaree) { this.heureDeclaree = heureDeclaree; }

	public String getJustification() { return justification; }
	public void setJustification(String justification) { this.justification = justification; }

	public StatutDemandeManuelle getStatut() { return statut; }
	public void setStatut(StatutDemandeManuelle statut) { this.statut = statut; }

	public String getValideurId() { return valideurId; }
	public void setValideurId(String valideurId) { this.valideurId = valideurId; }

	public String getValideurCompteId() { return valideurCompteId; }
	public void setValideurCompteId(String valideurCompteId) { this.valideurCompteId = valideurCompteId; }

	public String getValideurNom() { return valideurNom; }
	public void setValideurNom(String valideurNom) { this.valideurNom = valideurNom; }

	public String getDecidePar() { return decidePar; }
	public void setDecidePar(String decidePar) { this.decidePar = decidePar; }

	public Instant getDecideLe() { return decideLe; }
	public void setDecideLe(Instant decideLe) { this.decideLe = decideLe; }

	public String getMotifRefus() { return motifRefus; }
	public void setMotifRefus(String motifRefus) { this.motifRefus = motifRefus; }

	public UUID getPointageId() { return pointageId; }
	public void setPointageId(UUID pointageId) { this.pointageId = pointageId; }

	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }

	public String getRefusStatut() { return refusStatut; }
	public void setRefusStatut(String refusStatut) { this.refusStatut = refusStatut; }

	public Double getDistanceMetres() { return distanceMetres; }
	public void setDistanceMetres(Double distanceMetres) { this.distanceMetres = distanceMetres; }

	public Double getPrecisionMetres() { return precisionMetres; }
	public void setPrecisionMetres(Double precisionMetres) { this.precisionMetres = precisionMetres; }

	public Integer getRayonMetres() { return rayonMetres; }
	public void setRayonMetres(Integer rayonMetres) { this.rayonMetres = rayonMetres; }

	public String getModeQr() { return modeQr; }
	public void setModeQr(String modeQr) { this.modeQr = modeQr; }

	public Integer getPlafondMetres() { return plafondMetres; }
	public void setPlafondMetres(Integer plafondMetres) { this.plafondMetres = plafondMetres; }

	public Long getDelaiDemandeMinutes() { return delaiDemandeMinutes; }
	public void setDelaiDemandeMinutes(Long delaiDemandeMinutes) { this.delaiDemandeMinutes = delaiDemandeMinutes; }

	public Instant getEcheance() { return echeance; }
	public void setEcheance(Instant echeance) { this.echeance = echeance; }

	public List<SignalVigilance> getSignaux() { return signaux != null ? signaux : List.of(); }
	public void setSignaux(List<SignalVigilance> signaux) { this.signaux = signaux != null ? new ArrayList<>(signaux) : new ArrayList<>(); }

	public String getCommentaireValideur() { return commentaireValideur; }
	public void setCommentaireValideur(String commentaireValideur) { this.commentaireValideur = commentaireValideur; }

	public Long getDelaiDecisionMinutes() { return delaiDecisionMinutes; }
	public void setDelaiDecisionMinutes(Long delaiDecisionMinutes) { this.delaiDecisionMinutes = delaiDecisionMinutes; }

	public boolean isReprise() { return Boolean.TRUE.equals(reprise); }
	public void setReprise(Boolean reprise) { this.reprise = reprise; }
}
