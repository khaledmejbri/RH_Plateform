package com.hr.workflow.entity;

import com.hr.workflow.domain.StatutAppelEffet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wf_appel_effet")
@Getter
@Setter
@NoArgsConstructor
public class WfAppelEffet {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "cle_idempotence", nullable = false, length = 120, unique = true)
	private String cleIdempotence;

	@Column(name = "instance_id", nullable = false)
	private UUID instanceId;

	@Column(name = "tache_id")
	private UUID tacheId;

	@Column(name = "effet", nullable = false, length = 20)
	private String effet;

	@Column(name = "service_proprietaire", nullable = false, length = 40)
	private String serviceProprietaire;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 10)
	private StatutAppelEffet statut = StatutAppelEffet.RESERVE;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reponse", columnDefinition = "jsonb")
	private Map<String, Object> reponse;

	@Column(name = "tentatives", nullable = false)
	private int tentatives = 0;

	@Column(name = "prochaine_tentative_le")
	private Instant prochaineTentativeLe;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Column(name = "termine_le")
	private Instant termineLe;
}
