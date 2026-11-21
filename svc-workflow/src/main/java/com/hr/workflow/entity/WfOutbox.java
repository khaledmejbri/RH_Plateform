package com.hr.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "wf_outbox")
@Getter
@Setter
@NoArgsConstructor
public class WfOutbox {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "sujet", nullable = false, length = 60)
	private String sujet;

	@Column(name = "cle", nullable = false, length = 80)
	private String cle;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "entetes", columnDefinition = "jsonb")
	private Map<String, String> entetes;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "charge", nullable = false, columnDefinition = "jsonb")
	private String charge;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Column(name = "publie_le")
	private Instant publieLe;

	@Column(name = "tentatives", nullable = false)
	private int tentatives = 0;

	@Column(name = "prochaine_tentative_le", nullable = false)
	private Instant prochaineTentativeLe;

	@Column(name = "derniere_erreur", length = 500)
	private String derniereErreur;
}
