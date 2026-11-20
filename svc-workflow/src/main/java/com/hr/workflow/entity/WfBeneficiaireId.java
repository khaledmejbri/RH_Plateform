package com.hr.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WfBeneficiaireId implements Serializable {

	@Column(name = "instance_id", nullable = false)
	private UUID instanceId;

	@Column(name = "collaborateur_id", nullable = false)
	private UUID collaborateurId;
}
