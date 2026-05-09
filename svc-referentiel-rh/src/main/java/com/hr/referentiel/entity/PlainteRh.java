package com.hr.referentiel.entity;

import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CDC v2 §M04 :
 * - Plainte INTERNE : auteur = collaborateur → destinataire RH uniquement.
 * - Plainte EXTERNE : auteur = RO/RH → notifie simultanément RH + Services Techniques + Direction E&S.
 * - Numéro de ticket auto-généré (lisible).
 * - Pièces jointes (URLs S3) stockées en JSON.
 * - Log d'actions horodaté (qui a changé quoi et quand).
 */
@Entity
@Table(name = "rh_plainte", indexes = {
		@Index(name = "idx_plainte_auteur", columnList = "auteur_collaborateur_identifiant"),
		@Index(name = "idx_plainte_statut", columnList = "statut"),
		@Index(name = "idx_plainte_type", columnList = "type_plainte"),
		@Index(name = "idx_plainte_numero", columnList = "numero_ticket", unique = true)
})
public class PlainteRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	/** Numéro lisible : PLT-YYYYMM-XXXX, généré à la création. */
	@Column(name = "numero_ticket", nullable = false, updatable = false, length = 32)
	private String numeroTicket;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_plainte", nullable = false, length = 32)
	private TypePlainteRh typePlainte;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auteur_collaborateur_identifiant", nullable = false)
	private Collaborateur auteur;

	@Column(name = "titre", nullable = false, length = 255)
	private String titre;

	@Column(name = "description", nullable = false, length = 4000)
	private String description;

	/**
	 * URLs S3 des pièces jointes (photos, vidéos, audio). CDC §M04 : vocal transcrit séparément.
	 * Stocké en JSON : ["s3://bucket/plaintes/xxx/photo1.jpg", ...].
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "pieces_jointes", columnDefinition = "jsonb")
	private List<String> piecesJointes = new ArrayList<>();

	/** Pour les plaintes vocales : texte transcrit par Whisper (CDC §M04). */
	@Column(name = "transcription_audio", length = 8000)
	private String transcriptionAudio;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 32)
	private StatutPlainteRh statut = StatutPlainteRh.NOUVEAU;

	@Column(name = "commentaire_rh", length = 2000)
	private String commentaireRh;

	/**
	 * Log horodaté des changements de statut et actions RH.
	 * Format JSON : [{"statut":"EN_ANALYSE","acteur_id":"uuid","horodatage":"...","commentaire":"..."}]
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "log_actions", columnDefinition = "jsonb")
	private List<java.util.Map<String, String>> logActions = new ArrayList<>();

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public PlainteRh() {
	}

	@PrePersist
	public void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
		if (numeroTicket == null) {
			java.time.YearMonth ym = java.time.YearMonth.now();
			String suffix = String.format("%04d", (int) (Math.random() * 10000));
			this.numeroTicket = String.format("PLT-%d%02d-%s", ym.getYear(), ym.getMonthValue(), suffix);
		}
	}

	@PreUpdate
	public void preUpdate() {
		modifieLe = Instant.now();
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public String getNumeroTicket() { return numeroTicket; }
	public void setNumeroTicket(String numeroTicket) { this.numeroTicket = numeroTicket; }

	public TypePlainteRh getTypePlainte() { return typePlainte; }
	public void setTypePlainte(TypePlainteRh typePlainte) { this.typePlainte = typePlainte; }

	public Collaborateur getAuteur() { return auteur; }
	public void setAuteur(Collaborateur auteur) { this.auteur = auteur; }

	public String getTitre() { return titre; }
	public void setTitre(String titre) { this.titre = titre; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public List<String> getPiecesJointes() { return piecesJointes; }
	public void setPiecesJointes(List<String> piecesJointes) {
		this.piecesJointes = piecesJointes != null ? piecesJointes : new ArrayList<>();
	}

	public String getTranscriptionAudio() { return transcriptionAudio; }
	public void setTranscriptionAudio(String transcriptionAudio) { this.transcriptionAudio = transcriptionAudio; }

	public StatutPlainteRh getStatut() { return statut; }
	public void setStatut(StatutPlainteRh statut) { this.statut = statut; }

	public String getCommentaireRh() { return commentaireRh; }
	public void setCommentaireRh(String commentaireRh) { this.commentaireRh = commentaireRh; }

	public List<java.util.Map<String, String>> getLogActions() { return logActions; }
	public void setLogActions(List<java.util.Map<String, String>> logActions) {
		this.logActions = logActions != null ? logActions : new ArrayList<>();
	}

	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }

	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
