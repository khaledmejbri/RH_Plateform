package com.hr.presence.entity;

import com.hr.presence.domain.StatutHeuresSup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Demande d'heures supplémentaires (D-P13) : un site, une plage de dates, 1 à 50 bénéficiaires, une ouverture
 * avant et / ou après l'horaire. Décision globale ; {@code version} protège les transitions concurrentes.
 */
@Entity
@Table(name = "presence_heures_sup_demande", uniqueConstraints = @UniqueConstraint(name = "uk_presence_hs_demande_numero", columnNames = "numero"), indexes = {
		@Index(name = "idx_presence_hs_demande_statut_debut", columnList = "statut,debut_plage_ts"),
		@Index(name = "idx_presence_hs_demande_site", columnList = "site_id"),
		@Index(name = "idx_presence_hs_demande_demandeur", columnList = "demandeur_compte_id")
})
public class PresenceHeuresSupDemande {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 16)
	private String reference;

	/** Numéro lisible HS-AAAAMM-NNNN (D-L02) : attribué à la création, unique, immuable. Nul avant la reprise. */
	@Column(name = "numero", length = 32, updatable = false)
	private String numero;

	@Column(name = "site_id", nullable = false)
	private UUID siteId;

	@Column(name = "date_debut", nullable = false)
	private LocalDate dateDebut;

	@Column(name = "date_fin", nullable = false)
	private LocalDate dateFin;

	@Column(name = "ouverture_avant_minutes", nullable = false)
	private int ouvertureAvantMinutes;

	@Column(name = "ouverture_apres_minutes", nullable = false)
	private int ouvertureApresMinutes;

	@Column(nullable = false, length = 1000)
	private String motif;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatutHeuresSup statut;

	/** Ouverture de la fenêtre du premier jour ouvert restant : début - ouverture avant - tolérance avant. */
	@Column(name = "debut_plage_ts")
	private Instant debutPlageTs;

	@Column(name = "demandeur_fiche_id", length = 64)
	private String demandeurFicheId;

	@Column(name = "demandeur_compte_id", length = 64)
	private String demandeurCompteId;

	@Column(name = "demandeur_nom", length = 255)
	private String demandeurNom;

	@Column(name = "creee_validee", nullable = false)
	private boolean creeeValidee;

	@Column(name = "decide_par_compte_id", length = 64)
	private String decideParCompteId;

	@Column(name = "decide_par_nom", length = 255)
	private String decideParNom;

	@Column(name = "decide_le")
	private Instant decideLe;

	@Column(name = "motif_refus", length = 1000)
	private String motifRefus;

	@Column(name = "annule_par_compte_id", length = 64)
	private String annuleParCompteId;

	@Column(name = "annule_par_nom", length = 255)
	private String annuleParNom;

	@Column(name = "annule_le")
	private Instant annuleLe;

	@Column(name = "motif_annulation", length = 1000)
	private String motifAnnulation;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Version
	@Column(name = "version")
	private Long version;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (reference == null) {
			reference = "HS-" + id.toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
		}
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	public String getNumero() {
		return numero;
	}

	/** Attribue le numéro (une seule fois) et aligne l'ancienne référence dessus. */
	public void setNumero(String numero) {
		if (this.numero != null && !this.numero.equals(numero)) {
			throw new IllegalStateException("Le numéro d'une demande est immuable");
		}
		this.numero = numero;
		this.reference = numero;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public String getReference() {
		return reference;
	}

	public UUID getSiteId() {
		return siteId;
	}

	public void setSiteId(UUID siteId) {
		this.siteId = siteId;
	}

	public LocalDate getDateDebut() {
		return dateDebut;
	}

	public void setDateDebut(LocalDate dateDebut) {
		this.dateDebut = dateDebut;
	}

	public LocalDate getDateFin() {
		return dateFin;
	}

	public void setDateFin(LocalDate dateFin) {
		this.dateFin = dateFin;
	}

	public int getOuvertureAvantMinutes() {
		return ouvertureAvantMinutes;
	}

	public void setOuvertureAvantMinutes(int ouvertureAvantMinutes) {
		this.ouvertureAvantMinutes = ouvertureAvantMinutes;
	}

	public int getOuvertureApresMinutes() {
		return ouvertureApresMinutes;
	}

	public void setOuvertureApresMinutes(int ouvertureApresMinutes) {
		this.ouvertureApresMinutes = ouvertureApresMinutes;
	}

	public String getMotif() {
		return motif;
	}

	public void setMotif(String motif) {
		this.motif = motif;
	}

	public StatutHeuresSup getStatut() {
		return statut;
	}

	public void setStatut(StatutHeuresSup statut) {
		this.statut = statut;
	}

	public Instant getDebutPlageTs() {
		return debutPlageTs;
	}

	public void setDebutPlageTs(Instant debutPlageTs) {
		this.debutPlageTs = debutPlageTs;
	}

	public String getDemandeurFicheId() {
		return demandeurFicheId;
	}

	public void setDemandeurFicheId(String demandeurFicheId) {
		this.demandeurFicheId = demandeurFicheId;
	}

	public String getDemandeurCompteId() {
		return demandeurCompteId;
	}

	public void setDemandeurCompteId(String demandeurCompteId) {
		this.demandeurCompteId = demandeurCompteId;
	}

	public String getDemandeurNom() {
		return demandeurNom;
	}

	public void setDemandeurNom(String demandeurNom) {
		this.demandeurNom = demandeurNom;
	}

	public boolean isCreeeValidee() {
		return creeeValidee;
	}

	public void setCreeeValidee(boolean creeeValidee) {
		this.creeeValidee = creeeValidee;
	}

	public String getDecideParCompteId() {
		return decideParCompteId;
	}

	public void setDecideParCompteId(String decideParCompteId) {
		this.decideParCompteId = decideParCompteId;
	}

	public String getDecideParNom() {
		return decideParNom;
	}

	public void setDecideParNom(String decideParNom) {
		this.decideParNom = decideParNom;
	}

	public Instant getDecideLe() {
		return decideLe;
	}

	public void setDecideLe(Instant decideLe) {
		this.decideLe = decideLe;
	}

	public String getMotifRefus() {
		return motifRefus;
	}

	public void setMotifRefus(String motifRefus) {
		this.motifRefus = motifRefus;
	}

	public String getAnnuleParCompteId() {
		return annuleParCompteId;
	}

	public void setAnnuleParCompteId(String annuleParCompteId) {
		this.annuleParCompteId = annuleParCompteId;
	}

	public String getAnnuleParNom() {
		return annuleParNom;
	}

	public void setAnnuleParNom(String annuleParNom) {
		this.annuleParNom = annuleParNom;
	}

	public Instant getAnnuleLe() {
		return annuleLe;
	}

	public void setAnnuleLe(Instant annuleLe) {
		this.annuleLe = annuleLe;
	}

	public String getMotifAnnulation() {
		return motifAnnulation;
	}

	public void setMotifAnnulation(String motifAnnulation) {
		this.motifAnnulation = motifAnnulation;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public void setCreeLe(Instant creeLe) {
		this.creeLe = creeLe;
	}

	public Long getVersion() {
		return version;
	}
}
