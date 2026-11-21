package com.hr.workflow.entity;

import com.hr.workflow.domain.StatutVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_modele_version")
@Getter
@Setter
@NoArgsConstructor
public class WfModeleVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "modele_id", nullable = false)
	private WfModele modele;

	@Column(name = "numero", nullable = false)
	private int numero;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 10)
	private StatutVersion statut = StatutVersion.BROUILLON;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "definition", nullable = false, columnDefinition = "jsonb")
	private String definition;

	@Column(name = "format_definition", nullable = false)
	private int formatDefinition = 1;

	@Column(name = "empreinte", nullable = false, length = 64, columnDefinition = "char(64)")
	private String empreinte;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	@Column(name = "modifie_par_nom", length = 160)
	private String modifieParNom;

	@Column(name = "publie_le")
	private Instant publieLe;

	@Column(name = "publie_par_compte_id")
	private UUID publieParCompteId;

	@Column(name = "retire_le")
	private Instant retireLe;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}
