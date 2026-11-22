package com.hr.workflow.repository;

import com.hr.workflow.entity.WfBeneficiaire;
import com.hr.workflow.entity.WfBeneficiaireId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WfBeneficiaireRepository extends JpaRepository<WfBeneficiaire, WfBeneficiaireId> {

	List<WfBeneficiaire> findByIdInstanceId(UUID instanceId);

	List<WfBeneficiaire> findByIdInstanceIdIn(Collection<UUID> instanceIds);

	boolean existsByIdInstanceIdAndIdCollaborateurId(UUID instanceId, UUID collaborateurId);
}
