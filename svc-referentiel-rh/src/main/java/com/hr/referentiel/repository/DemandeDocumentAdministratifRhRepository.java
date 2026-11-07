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

	/** D-L02 : recherche RH par numéro lisible (stocké en majuscules ; correspondance exacte). */
	List<DemandeDocumentAdministratifRh> findByNumero(String numero);

	List<DemandeDocumentAdministratifRh> findByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

	List<DemandeDocumentAdministratifRh> findByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh statut);

	List<DemandeDocumentAdministratifRh> findByStatutInOrderByCreeLeAsc(List<StatutDocumentAdministratifDemandeRh> statuts);

	long countByStatutAndCreeLeBefore(StatutDocumentAdministratifDemandeRh statut, java.time.Instant avant);

	long countByStatut(StatutDocumentAdministratifDemandeRh statut);

	boolean existsByStatut(StatutDocumentAdministratifDemandeRh statut);

	/** Trouve la plus ancienne demande en attente (FIFO). */
	Optional<DemandeDocumentAdministratifRh> findFirstByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh statut);

	/** D-F06.a : plus ancienne demande ouverte du collaborateur pour ce type. */
	Optional<DemandeDocumentAdministratifRh> findFirstByDemandeurIdAndTypeDocumentAndStatutInOrderByCreeLeAsc(
			UUID demandeurId, com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh typeDocument,
			List<StatutDocumentAdministratifDemandeRh> statuts);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DemandeDocumentAdministratifRh d where d.statut = :statut order by d.creeLe asc")
	List<DemandeDocumentAdministratifRh> findQueueForUpdate(@Param("statut") StatutDocumentAdministratifDemandeRh statut);
}
