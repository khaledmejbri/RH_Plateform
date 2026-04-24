package com.hr.referentiel.entity;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Congé, autorisation de sortie ou ordre de mission — contenu JSON selon le type (CDC annexes).
 * Workflow CDC : employé → supérieur (si défini) → RRH.
 */
@Entity
@Table(name = "rh_demande_administrative", indexes = {
		@Index(name = "idx_demande_collab", columnList = "demandeur_identifiant"),
		@Index(name = "idx_demande_statut", columnList = "statut"),
		@Index(name = "idx_demande_type", columnList = "type_demande"),
		@Index(name = "idx_demande_periode", columnList = "periode_debut,periode_fin")
})
public class DemandeAdministrativeRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_demande", nullable = false, length = 40)
	private TypeDemandeAdministrativeRh typeDemande;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "demandeur_identifiant", nullable = false)
	private Collaborateur demandeur;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 40)
	private StatutDemandeAdministrativeRh statut = StatutDemandeAdministrativeRh.SOUMISE;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "contenu", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> contenu = new HashMap<>();

	@Column(name = "motif_refus", length = 2000)
	private String motifRefus;

	/** Copie de {@code contenu.date_debut} / {@code date_jour} — recherche et filtres (congés, missions, sorties). */
	@Column(name = "periode_debut")
	private LocalDate periodeDebut;

	/** Copie de {@code contenu.date_fin} ou {@code date_jour} pour une sortie d’un jour. */
	@Column(name = "periode_fin")
	private LocalDate periodeFin;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public DemandeAdministrativeRh() {
	}

	@PrePersist
	public void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		modifieLe = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public TypeDemandeAdministrativeRh getTypeDemande() {
		return typeDemande;
	}

	public void setTypeDemande(TypeDemandeAdministrativeRh typeDemande) {
		this.typeDemande = typeDemande;
	}

	public Collaborateur getDemandeur() {
		return demandeur;
	}

	public void setDemandeur(Collaborateur demandeur) {
		this.demandeur = demandeur;
	}

	public StatutDemandeAdministrativeRh getStatut() {
		return statut;
	}

	public void setStatut(StatutDemandeAdministrativeRh statut) {
		this.statut = statut;
	}

	public Map<String, Object> getContenu() {
		return contenu;
	}

	public void setContenu(Map<String, Object> contenu) {
		this.contenu = contenu != null ? contenu : new HashMap<>();
	}

	public String getMotifRefus() {
		return motifRefus;
	}

	public void setMotifRefus(String motifRefus) {
		this.motifRefus = motifRefus;
	}

	public LocalDate getPeriodeDebut() {
		return periodeDebut;
	}

	public void setPeriodeDebut(LocalDate periodeDebut) {
		this.periodeDebut = periodeDebut;
	}

	public LocalDate getPeriodeFin() {
		return periodeFin;
	}

	public void setPeriodeFin(LocalDate periodeFin) {
		this.periodeFin = periodeFin;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public void setCreeLe(Instant creeLe) {
		this.creeLe = creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public void setModifieLe(Instant modifieLe) {
		this.modifieLe = modifieLe;
	}
}
