package com.hr.referentiel.importation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ImportAnalyseRepository extends JpaRepository<ImportAnalyse, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from ImportAnalyse a where a.id = :id")
	Optional<ImportAnalyse> findByIdForUpdate(@Param("id") UUID id);

	@Query("select a from ImportAnalyse a where a.statut = 'CONFIRMEE' and (:type is null or a.typeImport = :type) "
			+ "order by a.confirmeLe desc")
	Page<ImportAnalyse> historique(@Param("type") String type, Pageable pageable);
}
