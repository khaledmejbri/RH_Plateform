package com.hr.presence.entity;

import com.hr.presence.domain.ActionParametre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal append-only des changements de paramètres (D-R02.g, C-L-02) : colonnes non modifiables, aucun endpoint
 * d'écriture ni de suppression, aucune purge. Écrit dans la même transaction que le changement.
 */
@Entity
@Table(name = "presence_parametre_journal",
		indexes = @Index(name = "idx_presence_param_journal", columnList = "referentiel, horodatage"))
public class PresenceParametreJournal {

	@Id
	@Column(updatable = false)
	private UUID id = UUID.randomUUID();

	@Column(nullable = false, length = 40, updatable = false)
	private String referentiel;

	@Column(nullable = false, length = 40, updatable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private ActionParametre action;

	@Column(name = "avant_json", columnDefinition = "text", updatable = false)
	private String avantJson;

	@Column(name = "apres_json", columnDefinition = "text", updatable = false)
	private String apresJson;

	@Column(name = "auteur_compte_id", length = 64, updatable = false)
	private String auteurCompteId;

	@Column(name = "auteur_nom", length = 120, updatable = false)
	private String auteurNom;

	@Column(nullable = false, updatable = false)
	private Instant horodatage = Instant.now();

	protected PresenceParametreJournal() {
	}

	public PresenceParametreJournal(String referentiel, String code, ActionParametre action, String avantJson,
									String apresJson, String auteurCompteId, String auteurNom) {
		this.referentiel = referentiel;
		this.code = code;
		this.action = action;
		this.avantJson = avantJson;
		this.apresJson = apresJson;
		this.auteurCompteId = auteurCompteId;
		this.auteurNom = auteurNom;
	}

	public UUID getId() { return id; }
	public String getReferentiel() { return referentiel; }
	public String getCode() { return code; }
	public ActionParametre getAction() { return action; }
	public String getAvantJson() { return avantJson; }
	public String getApresJson() { return apresJson; }
	public String getAuteurCompteId() { return auteurCompteId; }
	public String getAuteurNom() { return auteurNom; }
	public Instant getHorodatage() { return horodatage; }
}
