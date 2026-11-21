package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mémorisation idempotente des effets appliqués par {@code svc-workflow} (D-W5-07).
 * Les réponses 200 et 409 y sont conservées ; les 422 (erreurs métier corrigibles) ne le sont jamais.
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_workflow_effet", indexes = {
		@Index(name = "idx_wf_effet_societe_objet", columnList = "societe_id, objet_id")
})
public class RhWorkflowEffet {

	@Id
	@Column(name = "cle_idempotence", nullable = false, updatable = false, length = 120)
	private String cleIdempotence;

	@TenantId
	@Column(name = "societe_id", updatable = false)
	private UUID societeId;

	@Column(name = "objet_id", nullable = false, updatable = false)
	private UUID objetId;

	@Column(name = "effet", nullable = false, updatable = false, length = 20)
	private String effet;

	@Column(name = "code_resultat", nullable = false, updatable = false, length = 40)
	private String codeResultat;

	@Column(name = "statut_http", nullable = false, updatable = false)
	private int statutHttp;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reponse", nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> reponse = new HashMap<>();

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	public RhWorkflowEffet() {
	}

	public RhWorkflowEffet(
			String cleIdempotence,
			UUID objetId,
			String effet,
			String codeResultat,
			int statutHttp,
			Map<String, Object> reponse) {
		this.cleIdempotence = cleIdempotence;
		this.objetId = objetId;
		this.effet = effet;
		this.codeResultat = codeResultat;
		this.statutHttp = statutHttp;
		this.reponse = reponse != null ? new HashMap<>(reponse) : new HashMap<>();
		this.creeLe = Instant.now();
	}

	@PrePersist
	public void prePersist() {
		if (creeLe == null) {
			creeLe = Instant.now();
		}
	}

	public String getCleIdempotence() {
		return cleIdempotence;
	}

	public UUID getSocieteId() {
		return societeId;
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
