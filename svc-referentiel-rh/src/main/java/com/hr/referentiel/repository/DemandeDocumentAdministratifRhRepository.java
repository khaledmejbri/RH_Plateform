package com.hr.referentiel.repository;

import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeDocumentAdministratifRhRepository extends JpaRepository<DemandeDocumentAdministratifRh, UUID> {

	List<DemandeDocumentAdministratifRh> findByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

	List<DemandeDocumentAdministratifRh> findByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh statut);

	List<DemandeDocumentAdministratifRh> findByStatutInOrderByCreeLeAsc(List<StatutDocumentAdministratifDemandeRh> statuts);

	long countByStatutAndCreeLeBefore(StatutDocumentAdministratifDemandeRh statut, java.time.Instant avant);

	boolean existsByStatut(StatutDocumentAdministratifDemandeRh statut);

	/** Trouve la plus ancienne demande en attente (FIFO). */
	Optional<DemandeDocumentAdministratifRh> findFirstByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh statut);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DemandeDocumentAdministratifRh d where d.statut = :statut order by d.creeLe asc")
	List<DemandeDocumentAdministratifRh> findQueueForUpdate(@Param("statut") StatutDocumentAdministratifDemandeRh statut);
}
