package com.hr.referentiel.repository;

import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeAdminWorkflowHistoryRepository extends JpaRepository<DemandeAdminWorkflowHistory, UUID> {
	/** D-N02 : l'un des acteurs (fiche ou compte) a-t-il pris l'une de ces décisions sur la demande ? */
	boolean existsByDemandeAdministrativeIdAndActionInAndActeurIdentifiantIn(
			UUID demandeId, java.util.Collection<com.hr.referentiel.domain.ActionWorkflowAdministratif> actions,
			java.util.Collection<UUID> acteurs);

	List<DemandeAdminWorkflowHistory> findByDemandeAdministrativeIdOrderByDateActionAsc(UUID demandeAdministrativeId);
}
