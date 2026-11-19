package com.hr.referentiel.importation.parametrage;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne par type d'import : porte la version du paramétrage (D-I23) et sert de verrou de sérialisation de toutes les
 * écritures d'un type ({@code SELECT … FOR UPDATE}). La version est incrémentée à la main, sous ce verrou, d'exactement 1
 * par écriture effective (pas de {@code @Version} : l'incrément ne doit pas dépendre d'un champ « sale »).
 */
@Entity
@Immutable
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_import_parametrage", uniqueConstraints = @UniqueConstraint(
		name = "uk_rh_import_parametrage_societe_type", columnNames = {"societe_id", "type_import"}))
public class ImportParametrage {

	@Id
	@Column(name = "type_import", nullable = false, length = 20, updatable = false)
	private String typeImport;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

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
