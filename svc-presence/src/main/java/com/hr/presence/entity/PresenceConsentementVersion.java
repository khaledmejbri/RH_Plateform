package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Version publiée du texte de consentement (D-P07.h). Jamais modifiée ni supprimée : c'est la preuve du texte
 * accepté. La plus récemment publiée est la version courante.
 */
@Entity
@Immutable
@Table(name = "presence_consentement_version")
public class PresenceConsentementVersion {

	@Id
	@Column(length = 32, nullable = false, updatable = false)
	private String version;

	@Column(nullable = false, updatable = false, length = 20000)
	private String texte;

	@Column(name = "publie_par_compte_id", length = 64, updatable = false)
	private String publieParCompteId;

	@Column(name = "publie_le", nullable = false, updatable = false)
	private Instant publieLe;

	protected PresenceConsentementVersion() {
	}

	public PresenceConsentementVersion(String version, String texte, String publieParCompteId, Instant publieLe) {
		this.version = version;
		this.texte = texte;
		this.publieParCompteId = publieParCompteId;
		this.publieLe = publieLe;
	}

	public String getVersion() { return version; }
	public String getTexte() { return texte; }
	public String getPublieParCompteId() { return publieParCompteId; }
	public Instant getPublieLe() { return publieLe; }
}
