package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mouvement immuable du solde de congé (C-L-01 : conservé 10 ans). {@code type} et {@code origine} sont des textes
 * (valeurs de {@code TypeMouvementSolde} / {@code OrigineMouvementSolde}) : aucune contrainte CHECK générée.
 */
@Entity
@Immutable
@Table(name = "rh_mouvement_solde_conge",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_mvt_solde_acquisition", columnNames = {"collaborateur_id", "mois_acquisition"}),
				@UniqueConstraint(name = "uk_mvt_solde_demande", columnNames = {"demande_id", "type"})},
		indexes = @Index(name = "idx_mvt_solde_collab", columnList = "collaborateur_id, cree_le"))
public class MouvementSoldeConge {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "collaborateur_id", nullable = false, updatable = false)
	private UUID collaborateurId;

	@Column(name = "type", nullable = false, length = 30, updatable = false)
	private String type;

	@Column(name = "nombre", nullable = false, precision = 5, scale = 1, updatable = false)
	private BigDecimal nombre;

	@Column(name = "solde_apres", nullable = false, precision = 6, scale = 1, updatable = false)
	private BigDecimal soldeApres;

	@Column(name = "origine", length = 20, updatable = false)
	private String origine;

	@Column(name = "motif", length = 500, updatable = false)
	private String motif;

	@Column(name = "demande_id", updatable = false)
	private UUID demandeId;

	@Column(name = "demande_numero", length = 24, updatable = false)
	private String demandeNumero;

	@Column(name = "mois_acquisition", length = 7, updatable = false)
	private String moisAcquisition;

	@Column(name = "import_id", updatable = false)
	private UUID importId;

	@Column(name = "auteur_compte_id", updatable = false)
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", nullable = false, length = 160, updatable = false)
	private String auteurNom;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	public MouvementSoldeConge() {
	}

	public MouvementSoldeConge(UUID collaborateurId, String type, BigDecimal nombre, BigDecimal soldeApres,
			String origine, String motif, UUID demandeId, String demandeNumero, String moisAcquisition, UUID importId,
			UUID auteurCompteId, String auteurNom, Instant creeLe) {
		this.id = UUID.randomUUID();
		this.collaborateurId = collaborateurId;
		this.type = type;
		this.nombre = nombre;
		this.soldeApres = soldeApres;
		this.origine = origine;
		this.motif = motif;
		this.demandeId = demandeId;
		this.demandeNumero = demandeNumero;
		this.moisAcquisition = moisAcquisition;
		this.importId = importId;
		this.auteurCompteId = auteurCompteId;
		this.auteurNom = auteurNom;
		this.creeLe = creeLe;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCollaborateurId() {
		return collaborateurId;
	}

	public String getType() {
		return type;
	}

	public BigDecimal getNombre() {
		return nombre;
	}

	public BigDecimal getSoldeApres() {
		return soldeApres;
	}

	public String getOrigine() {
		return origine;
	}

	public String getMotif() {
		return motif;
	}

	public UUID getDemandeId() {
		return demandeId;
	}

	public String getDemandeNumero() {
		return demandeNumero;
	}

	public String getMoisAcquisition() {
		return moisAcquisition;
	}

	public UUID getImportId() {
		return importId;
	}

	public UUID getAuteurCompteId() {
		return auteurCompteId;
	}

	public String getAuteurNom() {
		return auteurNom;
	}

	public Instant getCreeLe() {
		return creeLe;
	}
}
