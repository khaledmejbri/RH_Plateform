package com.hr.referentiel.entity;

import com.hr.referentiel.domain.ActionJournalParametre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal des modifications de paramètres (D-R02.g, C-L-02) : ajout seul, jamais modifié ni supprimé, écrit dans la
 * même transaction que la modification. Conservation sans purge (au moins 5 ans).
 */
@Entity
@Immutable
@Table(name = "rh_parametre_journal", indexes = {
		@Index(name = "idx_rh_parametre_journal_ref", columnList = "referentiel, horodatage")
})
public class ParametreJournal {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "referentiel", nullable = false, length = 40, updatable = false)
	private String referentiel;

	@Column(name = "code", length = 40, updatable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 16, updatable = false)
	private ActionJournalParametre action;

	@Column(name = "avant_json", columnDefinition = "text", updatable = false)
	private String avantJson;

	@Column(name = "apres_json", columnDefinition = "text", updatable = false)
	private String apresJson;

	@Column(name = "auteur_compte_id", updatable = false)
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", nullable = false, length = 160, updatable = false)
	private String auteurNom;

	@Column(name = "horodatage", nullable = false, updatable = false)
	private Instant horodatage;

	public ParametreJournal() {
	}

	public ParametreJournal(String referentiel, String code, ActionJournalParametre action, String avantJson,
			String apresJson, UUID auteurCompteId, String auteurNom, Instant horodatage) {
		this.id = UUID.randomUUID();
		this.referentiel = referentiel;
		this.code = code;
		this.action = action;
		this.avantJson = avantJson;
		this.apresJson = apresJson;
		this.auteurCompteId = auteurCompteId;
		this.auteurNom = auteurNom;
		this.horodatage = horodatage;
	}

	public UUID getId() {
		return id;
	}

	public String getReferentiel() {
		return referentiel;
	}

	public String getCode() {
		return code;
	}

	public ActionJournalParametre getAction() {
		return action;
	}

	public String getAvantJson() {
		return avantJson;
	}

	public String getApresJson() {
		return apresJson;
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
