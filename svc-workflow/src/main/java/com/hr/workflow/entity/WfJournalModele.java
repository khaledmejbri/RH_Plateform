package com.hr.workflow.entity;

import com.hr.workflow.domain.ActionJournalModele;
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
@Table(name = "wf_journal_modele")
@Getter
@Setter
@NoArgsConstructor
public class WfJournalModele {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "modele_id")
	private UUID modeleId;

	@Column(name = "version_id")
	private UUID versionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 24)
	private ActionJournalModele action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "avant", columnDefinition = "jsonb")
	private Map<String, Object> avant;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "apres", columnDefinition = "jsonb")
	private Map<String, Object> apres;

	@Column(name = "auteur_compte_id")
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", length = 160)
	private String auteurNom;

	@Column(name = "horodatage", nullable = false)
	private Instant horodatage;
}
