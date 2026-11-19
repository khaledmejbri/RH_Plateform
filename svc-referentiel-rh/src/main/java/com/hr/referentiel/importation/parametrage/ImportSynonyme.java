package com.hr.referentiel.importation.parametrage;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Synonyme d'en-tête d'un champ (D-I21). La forme normalisée vient de {@code ReconnaissanceEnTetes.normaliser}. */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_import_synonyme",
		uniqueConstraints = @UniqueConstraint(name = "uk_rh_import_synonyme_forme",
				columnNames = {"societe_id", "type_import", "champ_code", "forme_normalisee"}),
		indexes = @Index(name = "idx_rh_import_synonyme_forme", columnList = "societe_id, type_import, forme_normalisee"))
public class ImportSynonyme {

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

	@Column(name = "texte", nullable = false, length = 80)
	private String texte;

	@Column(name = "forme_normalisee", nullable = false, length = 80, updatable = false)
	private String formeNormalisee;

	@Convert(converter = OrigineSynonymeConverter.class)
	@Column(name = "origine", nullable = false, length = 8, updatable = false)
	private OrigineSynonyme origine;

	@Column(name = "actif", nullable = false)
	private boolean actif;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "cree_par_nom", length = 160, updatable = false)
	private String creeParNom;

	protected ImportSynonyme() {
	}

	public ImportSynonyme(String typeImport, String champCode, String texte, String formeNormalisee,
			OrigineSynonyme origine, boolean actif, Instant creeLe, String creeParNom) {
		this.id = UUID.randomUUID();
		this.typeImport = typeImport;
		this.champCode = champCode;
		this.texte = texte;
		this.formeNormalisee = formeNormalisee;
		this.origine = origine;
		this.actif = actif;
		this.creeLe = creeLe;
		this.creeParNom = creeParNom;
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

	public String getTexte() {
		return texte;
	}

	public String getFormeNormalisee() {
		return formeNormalisee;
	}

	public OrigineSynonyme getOrigine() {
		return origine;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public String getCreeParNom() {
		return creeParNom;
	}
}
