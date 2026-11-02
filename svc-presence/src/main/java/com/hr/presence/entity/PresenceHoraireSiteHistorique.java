package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Trace de chaque modification d'horaire (C-L-02) : auteur, date, état avant / après (JSON sérialisé par le service). */
@Entity
@Table(name = "presence_horaire_site_historique",
		indexes = @Index(name = "idx_presence_horaire_histo_site", columnList = "site_id,modifie_le"))
public class PresenceHoraireSiteHistorique {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "site_id", nullable = false)
	private UUID siteId;

	@Column(name = "modifie_par_compte_id", length = 64)
	private String modifieParCompteId;

	@Column(name = "modifie_par_nom", length = 255)
	private String modifieParNom;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Column(name = "avant_json", columnDefinition = "text")
	private String avantJson;

	@Column(name = "apres_json", columnDefinition = "text")
	private String apresJson;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getSiteId() {
		return siteId;
	}

	public void setSiteId(UUID siteId) {
		this.siteId = siteId;
	}

	public String getModifieParCompteId() {
		return modifieParCompteId;
	}

	public void setModifieParCompteId(String modifieParCompteId) {
		this.modifieParCompteId = modifieParCompteId;
	}

	public String getModifieParNom() {
		return modifieParNom;
	}

	public void setModifieParNom(String modifieParNom) {
		this.modifieParNom = modifieParNom;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public void setModifieLe(Instant modifieLe) {
		this.modifieLe = modifieLe;
	}

	public String getAvantJson() {
		return avantJson;
	}

	public void setAvantJson(String avantJson) {
		this.avantJson = avantJson;
	}

	public String getApresJson() {
		return apresJson;
	}

	public void setApresJson(String apresJson) {
		this.apresJson = apresJson;
	}
}
