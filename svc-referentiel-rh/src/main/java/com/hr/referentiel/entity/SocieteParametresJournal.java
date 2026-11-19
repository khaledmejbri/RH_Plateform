package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal d'audit des modifications de {@link SocieteParametres} (conservation 5 ans, D-SOC13).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(
		name = "rh_societe_parametres_journal",
		indexes = {
				@Index(name = "idx_soc_param_journal_societe_date", columnList = "societe_id, horodatage")
		})
public class SocieteParametresJournal {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@Column(name = "auteur_compte_id")
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", nullable = false, length = 150)
	private String auteurNom;

	@Column(name = "avant_json", columnDefinition = "text")
	private String avantJson;

	@Column(name = "apres_json", nullable = false, columnDefinition = "text")
	private String apresJson;

	@Column(name = "horodatage", nullable = false)
	private Instant horodatage = Instant.now();

	protected SocieteParametresJournal() {
	}

	public SocieteParametresJournal(
			UUID auteurCompteId,
			String auteurNom,
			String avantJson,
			String apresJson,
			Instant horodatage) {
		this.auteurCompteId = auteurCompteId;
		this.auteurNom = auteurNom;
		this.avantJson = avantJson;
		this.apresJson = apresJson;
		this.horodatage = horodatage;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public UUID getAuteurCompteId() {
		return auteurCompteId;
	}

	public String getAuteurNom() {
		return auteurNom;
	}

	public String getAvantJson() {
		return avantJson;
	}

	public String getApresJson() {
		return apresJson;
	}

	public Instant getHorodatage() {
		return horodatage;
	}
}
