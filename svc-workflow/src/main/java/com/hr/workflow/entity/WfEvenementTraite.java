package com.hr.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_evenement_traite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WfEvenementTraite {

	@Id
	@Column(name = "evenement_id", nullable = false)
	private UUID evenementId;

	@Column(name = "traite_le", nullable = false)
	private Instant traiteLe;
}
