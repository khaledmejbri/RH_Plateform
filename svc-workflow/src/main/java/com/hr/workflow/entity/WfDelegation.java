package com.hr.workflow.entity;

import com.hr.workflow.domain.OrigineDelegation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Table créée en W3, exploitée en W4 (D-W3-04).
 */
@Entity
@Table(name = "wf_delegation")
@Getter
@Setter
@NoArgsConstructor
public class WfDelegation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "delegant_collaborateur_id", nullable = false)
	private UUID delegantCollaborateurId;

	@Column(name = "delegant_nom", length = 160)
	private String delegantNom;

	@Column(name = "delegue_collaborateur_id", nullable = false)
	private UUID delegueCollaborateurId;

	@Column(name = "delegue_compte_id")
	private UUID delegueCompteId;

	@Column(name = "delegue_nom", length = 160)
	private String delegueNom;

	@Column(name = "du", nullable = false)
	private LocalDate du;

	@Column(name = "au", nullable = false)
	private LocalDate au;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "objet_types", columnDefinition = "jsonb")
	private List<String> objetTypes;

	@Column(name = "motif", length = 300)
	private String motif;

	@Enumerated(EnumType.STRING)
	@Column(name = "origine", nullable = false, length = 12)
	private OrigineDelegation origine = OrigineDelegation.MANUELLE;

	@Column(name = "creee_par_compte_id")
	private UUID creeeParCompteId;

	@Column(name = "creee_le", nullable = false)
	private Instant creeeLe;

	@Column(name = "annulee_le")
	private Instant annuleeLe;

	@Column(name = "annulee_par_compte_id")
	private UUID annuleeParCompteId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}
