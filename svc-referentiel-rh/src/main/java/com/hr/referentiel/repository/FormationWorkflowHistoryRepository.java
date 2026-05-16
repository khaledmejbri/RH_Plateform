package com.hr.referentiel.repository;

import com.hr.referentiel.entity.FormationWorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormationWorkflowHistoryRepository extends JpaRepository<FormationWorkflowHistory, UUID> {
	List<FormationWorkflowHistory> findByDemandeFormationIdOrderByDateActionAsc(UUID demandeFormationId);
}
