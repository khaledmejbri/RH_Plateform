package com.hr.identiteacces.repository;

import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.entity.Societe;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocieteRepository extends JpaRepository<Societe, UUID> {

	@Query("select s from Societe s where lower(s.code) = lower(:code)")
	Optional<Societe> findByCodeIgnoreCase(@Param("code") String code);

	@Query("select (count(s) > 0) from Societe s where lower(s.code) = lower(:code)")
	boolean existsByCodeIgnoreCase(@Param("code") String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Societe s where s.id = :id")
	Optional<Societe> findByIdPourMiseAJour(@Param("id") UUID id);

	Page<Societe> findAllByOrderByCreeLeDesc(Pageable pageable);

	List<Societe> findByStatutOrderByCodeAsc(StatutSociete statut);

	List<Societe> findAllByOrderByCodeAsc();
}
