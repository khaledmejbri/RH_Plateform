package com.hr.presence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mémorisation idempotente des effets appliqués par {@code svc-workflow} dans {@code svc-presence} (W6, mono-société).
 * Les réponses 200 et 409 y sont conservées ; les 422 (erreurs métier corrigibles) ne le sont jamais.
 */
@Entity
@Table(name = "presence_workflow_effet", indexes = {
		@Index(name = "idx_presence_wf_effet_objet", columnList = "objet_type,objet_id")
})
public class PresenceWorkflowEffet {

	@Id
	@Column(name = "cle_idempotence", nullable = false, updatable = false, length = 120)
	private String cleIdempotence;

	@Column(name = "objet_type", nullable = false, updatable = false, length = 32)
	private String objetType;

	@Column(name = "objet_id", nullable = false, updatable = false)
	private UUID objetId;

	@Column(name = "effet", nullable = false, updatable = false, length = 32)
	private String effet;

	@Column(name = "code_resultat", nullable = false, updatable = false, length = 64)
	private String codeResultat;

	@Column(name = "statut_http", nullable = false, updatable = false)
	private int statutHttp;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reponse", nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> reponse = new HashMap<>();

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	public PresenceWorkflowEffet() {
	}

	public PresenceWorkflowEffet(
			String cleIdempotence,
			String objetType,
			UUID objetId,
			String effet,
			String codeResultat,
			int statutHttp,
			Map<String, Object> reponse) {
		this.cleIdempotence = cleIdempotence;
		this.objetType = objetType;
		this.objetId = objetId;
		this.effet = effet;
		this.codeResultat = codeResultat;
		this.statutHttp = statutHttp;
		this.reponse = reponse != null ? new HashMap<>(reponse) : new HashMap<>();
		this.creeLe = Instant.now();
	}

	@PrePersist
	void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	public String getCleIdempotence() {
		return cleIdempotence;
	}

	public String getObjetType() {
		return objetType;
	}

	public UUID getObjetId() {
		return objetId;
	}

	public String getEffet() {
		return effet;
	}

	public String getCodeResultat() {
		return codeResultat;
	}

	public int getStatutHttp() {
		return statutHttp;
	}

	public Map<String, Object> getReponse() {
		return reponse;
	}

	public Instant getCreeLe() {
		return creeLe;
	}
}
