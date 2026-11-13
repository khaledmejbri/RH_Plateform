package com.hr.referentiel.importation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Contenu extrait d'une analyse (cellules de toutes les colonnes, JSON compressé en gzip) et résultat de la dernière
 * évaluation. Temporaire : supprimé 30 minutes après la dernière action (ADR § 6). Visible de son seul auteur.
 */
@Entity
@Table(name = "rh_import_contenu")
public class ImportContenu {

	@Id
	@Column(name = "import_id", nullable = false, updatable = false)
	private UUID importId;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(name = "cellules", columnDefinition = "bytea")
	private byte[] cellules;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(name = "resultat", columnDefinition = "bytea")
	private byte[] resultat;

	public ImportContenu() {
	}

	public ImportContenu(UUID importId, byte[] cellules, byte[] resultat) {
		this.importId = importId;
		this.cellules = cellules;
		this.resultat = resultat;
	}

	public UUID getImportId() {
		return importId;
	}

	public byte[] getCellules() {
		return cellules;
	}

	public void setCellules(byte[] cellules) {
		this.cellules = cellules;
	}

	public byte[] getResultat() {
		return resultat;
	}

	public void setResultat(byte[] resultat) {
		this.resultat = resultat;
	}
}
