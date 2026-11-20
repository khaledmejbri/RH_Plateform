package com.hr.workflow.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "wf_beneficiaire")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WfBeneficiaire {

	@EmbeddedId
	private WfBeneficiaireId id;

	public static WfBeneficiaire of(UUID instanceId, UUID collaborateurId) {
		return new WfBeneficiaire(new WfBeneficiaireId(instanceId, collaborateurId));
	}
}
