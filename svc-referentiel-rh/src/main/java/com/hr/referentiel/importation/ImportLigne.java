package com.hr.referentiel.importation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Historique par ligne d'un import confirmé (5 ans) ; sert aussi de trace par fiche (D-I10). Ne contient jamais de valeur
 * de cellule hors matricule et champs modifiés ; les lignes « Inchangée » ne sont pas écrites.
 */
@Entity
@Immutable
@Table(name = "rh_import_ligne", indexes = {
		@Index(name = "idx_rh_import_ligne_import", columnList = "import_id, numero_ligne"),
		@Index(name = "idx_rh_import_ligne_collab", columnList = "collaborateur_id")})
public class ImportLigne implements org.springframework.data.domain.Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "import_id", nullable = false, updatable = false)
	private UUID importId;

	@Column(name = "numero_ligne", updatable = false)
	private int numeroLigne;

	@Column(name = "matricule", length = 64, updatable = false)
	private String matricule;

	@Column(name = "collaborateur_id", updatable = false)
	private UUID collaborateurId;

	@Column(name = "action", length = 20, updatable = false)
	private String action;

	/** JSON : {@code [{champ, avant, apres}]}. */
	@Column(name = "changements", columnDefinition = "text", updatable = false)
	private String changements;

	/** JSON : {@code [{code, colonne}]}, sans valeur de cellule. */
	@Column(name = "erreurs", columnDefinition = "text", updatable = false)
	private String erreurs;

	@jakarta.persistence.Transient
	private boolean nouvelle = true;

	public ImportLigne() {
	}

	public ImportLigne(UUID importId, int numeroLigne, String matricule, UUID collaborateurId, String action,
			String changements, String erreurs) {
		this.id = UUID.randomUUID();
		this.importId = importId;
		this.numeroLigne = numeroLigne;
		this.matricule = matricule;
		this.collaborateurId = collaborateurId;
		this.action = action;
		this.changements = changements;
		this.erreurs = erreurs;
	}

	@Override
	public UUID getId() {
		return id;
	}

	/** Insertion tant que l'entité n'a pas été persistée ou relue : évite le SELECT de {@code merge} pour chaque ligne importée. */
	@Override
	public boolean isNew() {
		return nouvelle;
	}

	@jakarta.persistence.PostLoad
	@jakarta.persistence.PostPersist
	void marquerExistante() {
		this.nouvelle = false;
	}

	public UUID getImportId() {
		return importId;
	}

	public int getNumeroLigne() {
		return numeroLigne;
	}

	public String getMatricule() {
		return matricule;
	}

	public UUID getCollaborateurId() {
		return collaborateurId;
	}

	public String getAction() {
		return action;
	}

	public String getChangements() {
		return changements;
	}

	public String getErreurs() {
		return erreurs;
	}
}
