package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import com.hr.referentiel.domain.BaseDecompteConge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paramètres de fonctionnement d'une société (§ 4.2, D-SOC04 à D-SOC13).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(
		name = "rh_societe_parametres",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_rh_societe_parametres_societe", columnNames = "societe_id")
		})
public class SocieteParametres {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "jours_travailles", nullable = false, columnDefinition = "jsonb")
	private List<String> joursTravailles = new ArrayList<>(List.of("LUN", "MAR", "MER", "JEU", "VEN"));

	@Column(name = "duree_hebdo_minutes", nullable = false)
	private int dureeHebdoMinutes = 2400;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "duree_jour_minutes", nullable = false, columnDefinition = "jsonb")
	private Map<String, Integer> dureeJourMinutes = new LinkedHashMap<>(Map.of(
			"LUN", 480, "MAR", 480, "MER", 480, "JEU", 480, "VEN", 480));

	@Column(name = "heure_debut_journee", nullable = false, length = 5)
	private String heureDebutJournee = "08:00";

	@Column(name = "heure_fin_journee", nullable = false, length = 5)
	private String heureFinJournee = "17:00";

	@Enumerated(EnumType.STRING)
	@Column(name = "base_decompte_conge", nullable = false, length = 30)
	private BaseDecompteConge baseDecompteConge = BaseDecompteConge.JOURS_TRAVAILLES;

	@Column(name = "duree_max_sortie_minutes", nullable = false)
	private int dureeMaxSortieMinutes = 120;

	@Column(name = "delegation_auto_conges", nullable = false)
	private boolean delegationAutoConges = true;

	@Column(name = "publication_quatre_yeux", nullable = false)
	private boolean publicationQuatreYeux = false;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe = Instant.now();

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe = Instant.now();

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	@Column(name = "modifie_par_nom", length = 150)
	private String modifieParNom;

	public UUID getId() {
		return id;
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public List<String> getJoursTravailles() {
		return joursTravailles;
	}

	public void setJoursTravailles(List<String> joursTravailles) {
		this.joursTravailles = new ArrayList<>(joursTravailles);
	}

	public int getDureeHebdoMinutes() {
		return dureeHebdoMinutes;
	}

	public void setDureeHebdoMinutes(int dureeHebdoMinutes) {
		this.dureeHebdoMinutes = dureeHebdoMinutes;
	}

	public Map<String, Integer> getDureeJourMinutes() {
		return dureeJourMinutes;
	}

	public void setDureeJourMinutes(Map<String, Integer> dureeJourMinutes) {
		this.dureeJourMinutes = new LinkedHashMap<>(dureeJourMinutes);
	}

	public String getHeureDebutJournee() {
		return heureDebutJournee;
	}

	public void setHeureDebutJournee(String heureDebutJournee) {
		this.heureDebutJournee = heureDebutJournee;
	}

	public String getHeureFinJournee() {
		return heureFinJournee;
	}

	public void setHeureFinJournee(String heureFinJournee) {
		this.heureFinJournee = heureFinJournee;
	}

	public BaseDecompteConge getBaseDecompteConge() {
		return baseDecompteConge;
	}

	public void setBaseDecompteConge(BaseDecompteConge baseDecompteConge) {
		this.baseDecompteConge = baseDecompteConge;
	}

	public int getDureeMaxSortieMinutes() {
		return dureeMaxSortieMinutes;
	}

	public void setDureeMaxSortieMinutes(int dureeMaxSortieMinutes) {
		this.dureeMaxSortieMinutes = dureeMaxSortieMinutes;
	}

	public boolean isDelegationAutoConges() {
		return delegationAutoConges;
	}

	public void setDelegationAutoConges(boolean delegationAutoConges) {
		this.delegationAutoConges = delegationAutoConges;
	}

	public boolean isPublicationQuatreYeux() {
		return publicationQuatreYeux;
	}

	public void setPublicationQuatreYeux(boolean publicationQuatreYeux) {
		this.publicationQuatreYeux = publicationQuatreYeux;
	}

	public Long getVersion() {
		return version;
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

	public UUID getModifieParCompteId() {
		return modifieParCompteId;
	}

	public void setModifieParCompteId(UUID modifieParCompteId) {
		this.modifieParCompteId = modifieParCompteId;
	}

	public String getModifieParNom() {
		return modifieParNom;
	}

	public void setModifieParNom(String modifieParNom) {
		this.modifieParNom = modifieParNom;
	}
}
