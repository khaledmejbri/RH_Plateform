package com.hr.referentiel.importation.parametrage;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal du paramétrage des champs d'import (D-I23) : ajout seul, texte déjà en clair, écrit dans la transaction de
 * l'écriture, sans purge (au moins 5 ans, C-L-02).
 */
@Entity
@Immutable
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_import_champ_journal", indexes = @Index(name = "idx_rh_import_champ_journal_type",
		columnList = "societe_id, type_import, horodatage"))
public class ImportChampJournal {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@Column(name = "type_import", nullable = false, length = 20, updatable = false)
	private String typeImport;

	/** Version du paramétrage APRÈS l'écriture (regroupe les lignes d'un même enregistrement). */
	@Column(name = "version", nullable = false, updatable = false)
	private long version;

	@Column(name = "champ_code", length = 40, updatable = false)
	private String champCode;

	@Column(name = "attribut", nullable = false, length = 32, updatable = false)
	private String attribut;

	@Column(name = "avant", length = 2000, updatable = false)
	private String avant;

	@Column(name = "apres", length = 2000, updatable = false)
	private String apres;

	@Column(name = "auteur_compte_id", updatable = false)
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", nullable = false, length = 160, updatable = false)
	private String auteurNom;

	@Column(name = "horodatage", nullable = false, updatable = false)
	private Instant horodatage;

	/** Libellé figé d'un champ personnalisé (lisible après sa suppression) ; null pour un champ fixe. */
	@Column(name = "champ_libelle", length = 80, updatable = false)
	private String champLibelle;

	protected ImportChampJournal() {
	}

	public ImportChampJournal(String typeImport, long version, String champCode, String attribut, String avant, String apres,
			UUID auteurCompteId, String auteurNom, Instant horodatage, String champLibelle) {
		this(typeImport, version, champCode, attribut, avant, apres, auteurCompteId, auteurNom, horodatage);
		this.champLibelle = champLibelle;
	}

	public String getChampLibelle() {
		return champLibelle;
	}

	public ImportChampJournal(String typeImport, long version, String champCode, String attribut, String avant, String apres,
			UUID auteurCompteId, String auteurNom, Instant horodatage) {
		this.id = UUID.randomUUID();
		this.typeImport = typeImport;
		this.version = version;
		this.champCode = champCode;
		this.attribut = attribut;
		this.avant = avant;
		this.apres = apres;
		this.auteurCompteId = auteurCompteId;
		this.auteurNom = auteurNom;
		this.horodatage = horodatage;
	}

	public UUID getId() {
		return id;
	}

	public String getTypeImport() {
		return typeImport;
	}

	public long getVersion() {
		return version;
	}

	public String getChampCode() {
		return champCode;
	}

	public String getAttribut() {
		return attribut;
	}

	public String getAvant() {
		return avant;
	}

	public String getApres() {
		return apres;
	}

	public UUID getAuteurCompteId() {
		return auteurCompteId;
	}

	public String getAuteurNom() {
		return auteurNom;
	}

	public Instant getHorodatage() {
		return horodatage;
	}
}
