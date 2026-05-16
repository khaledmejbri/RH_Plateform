package com.hr.referentiel.repository;

import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeAdminWorkflowHistoryRepository extends JpaRepository<DemandeAdminWorkflowHistory, UUID> {
	List<DemandeAdminWorkflowHistory> findByDemandeAdministrativeIdOrderByDateActionAsc(UUID demandeAdministrativeId);
}
