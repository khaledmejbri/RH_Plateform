package com.hr.referentiel.importation.parametrage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Une ligne par type d'import : porte la version du paramétrage (D-I23) et sert de verrou de sérialisation de toutes les
 * écritures d'un type ({@code SELECT … FOR UPDATE}). La version est incrémentée à la main, sous ce verrou, d'exactement 1
 * par écriture effective (pas de {@code @Version} : l'incrément ne doit pas dépendre d'un champ « sale »).
 */
@Entity
@Table(name = "rh_import_parametrage")
public class ImportParametrage {

	@Id
	@Column(name = "type_import", nullable = false, length = 20, updatable = false)
	private String typeImport;

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	@Column(name = "modifie_par_nom", length = 160)
	private String modifieParNom;

	protected ImportParametrage() {
	}

	public ImportParametrage(String typeImport) {
		this.typeImport = typeImport;
	}

	public String getTypeImport() {
		return typeImport;
	}

	public long getVersion() {
		return version;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public String getModifieParNom() {
		return modifieParNom;
	}

	/** Une écriture effective : version + 1, date et auteur. */
	public void marquerModifie(Instant quand, String auteurNom) {
		this.version++;
		this.modifieLe = quand;
		this.modifieParNom = auteurNom;
	}
}
