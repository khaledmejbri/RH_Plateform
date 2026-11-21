package com.hr.workflow.entity;

import com.hr.workflow.domain.RaisonSautEtape;
import com.hr.workflow.domain.ResultatEtapeExecution;
import com.hr.workflow.domain.StatutEtapeExecution;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_etape_execution")
@Getter
@Setter
@NoArgsConstructor
public class WfEtapeExecution {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instance_id", nullable = false)
	private WfInstance instance;

	@Column(name = "etape_code", nullable = false, length = 40)
	private String etapeCode;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 10)
	private StatutEtapeExecution statut;

	@Enumerated(EnumType.STRING)
	@Column(name = "raison_saut", length = 30)
	private RaisonSautEtape raisonSaut;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultat", length = 12)
	private ResultatEtapeExecution resultat;

	@Column(name = "nb_renvois", nullable = false)
	private int nbRenvois = 0;

	@Column(name = "active_le", nullable = false)
	private Instant activeLe;

	@Column(name = "terminee_le")
	private Instant termineeLe;
}
