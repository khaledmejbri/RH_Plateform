package com.hr.workflow.entity;

import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wf_tache")
@Getter
@Setter
@NoArgsConstructor
public class WfTache {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instance_id", nullable = false)
	private WfInstance instance;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "etape_execution_id", nullable = false)
	private WfEtapeExecution etapeExecution;

	@Column(name = "code_tache", length = 40)
	private String codeTache;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 12)
	private TypeTache type;

	@Column(name = "libelle", nullable = false, length = 120)
	private String libelle;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 10)
	private StatutTache statut = StatutTache.A_TRAITER;

	@Column(name = "obligatoire", nullable = false)
	private boolean obligatoire = true;

	@Column(name = "echeance_le")
	private Instant echeanceLe;

	@Column(name = "prochaine_relance_le")
	private Instant prochaineRelanceLe;

	@Column(name = "nb_relances", nullable = false)
	private int nbRelances = 0;

	@Column(name = "escalade_a_faire_le")
	private Instant escaladeAFaireLe;

	@Column(name = "escalade_le")
	private Instant escaladeLe;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", length = 12)
	private DecisionTache decision;

	@Column(name = "commentaire", length = 1000)
	private String commentaire;

	@Column(name = "decide_par_compte_id")
	private UUID decideParCompteId;

	@Column(name = "decide_par_collaborateur_id")
	private UUID decideParCollaborateurId;

	@Column(name = "decide_par_nom", length = 160)
	private String decideParNom;

	@Column(name = "pour_collaborateur_id")
	private UUID pourCollaborateurId;

	@Column(name = "pour_nom", length = 160)
	private String pourNom;

	@Column(name = "decide_le")
	private Instant decideLe;

	@Column(name = "cle_idempotence", length = 64)
	private String cleIdempotence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reponse_decision", columnDefinition = "jsonb")
	private Map<String, Object> reponseDecision;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}
