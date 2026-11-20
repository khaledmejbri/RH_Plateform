package com.hr.workflow.repository;

import com.hr.workflow.entity.WfEtapeExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfEtapeExecutionRepository extends JpaRepository<WfEtapeExecution, UUID> {

	List<WfEtapeExecution> findByInstanceIdOrderByOrdreAsc(UUID instanceId);

	Optional<WfEtapeExecution> findTopByInstanceIdOrderByOrdreDesc(UUID instanceId);
}
