package com.hr.referentiel.importation.parametrage;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/** Réglage d'un champ d'import : en-tête du modèle, présence dans le modèle, exigence, ordre (D-I14 à D-I20). */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_import_champ", uniqueConstraints = @UniqueConstraint(name = "uk_rh_import_champ_type_code",
		columnNames = {"societe_id", "type_import", "champ_code"}))
public class ImportChampRegle {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@Column(name = "type_import", nullable = false, length = 20, updatable = false)
	private String typeImport;

	@Column(name = "champ_code", nullable = false, length = 40, updatable = false)
	private String champCode;

	@Column(name = "en_tete", nullable = false, length = 80)
	private String enTete;

	@Column(name = "dans_modele", nullable = false)
	private boolean dansModele;

	@Convert(converter = ExigenceChampConverter.class)
	@Column(name = "exigence", nullable = false, length = 16)
	private ExigenceChamp exigence;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	protected ImportChampRegle() {
	}

	public ImportChampRegle(String typeImport, String champCode, String enTete, boolean dansModele, ExigenceChamp exigence,
			int ordre) {
		this.id = UUID.randomUUID();
		this.typeImport = typeImport;
		this.champCode = champCode;
		this.enTete = enTete;
		this.dansModele = dansModele;
		this.exigence = exigence;
		this.ordre = ordre;
	}

	public UUID getId() {
		return id;
	}

	public String getTypeImport() {
		return typeImport;
	}

	public String getChampCode() {
		return champCode;
	}

	public String getEnTete() {
		return enTete;
	}

	public void setEnTete(String enTete) {
		this.enTete = enTete;
	}

	public boolean isDansModele() {
		return dansModele;
	}

	public void setDansModele(boolean dansModele) {
		this.dansModele = dansModele;
	}

	public ExigenceChamp getExigence() {
		return exigence;
	}

	public void setExigence(ExigenceChamp exigence) {
		this.exigence = exigence;
	}

	public int getOrdre() {
		return ordre;
	}

	public void setOrdre(int ordre) {
		this.ordre = ordre;
	}
}
