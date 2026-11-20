package com.hr.workflow.entity;

import com.hr.workflow.domain.ModeActivation;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_activation")
@Getter
@Setter
@NoArgsConstructor
public class WfActivation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "objet_type", nullable = false, length = 40, unique = true)
	private String objetType;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false, length = 10)
	private ModeActivation mode = ModeActivation.HERITE;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}
