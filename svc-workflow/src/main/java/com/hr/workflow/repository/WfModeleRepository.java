package com.hr.workflow.repository;

import com.hr.workflow.entity.WfModele;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfModeleRepository extends JpaRepository<WfModele, UUID> {

	Optional<WfModele> findByCode(String code);

	boolean existsByCode(String code);

	@Query("""
			SELECT m FROM WfModele m
			WHERE m.actif = true
			  AND m.objetType = :objetType
			  AND ((:sousType IS NULL AND m.sousType IS NULL) OR m.sousType = :sousType)
			""")
	Optional<WfModele> findActifParObjetTypeEtSousType(
			@Param("objetType") String objetType,
			@Param("sousType") String sousType);

	List<WfModele> findByObjetTypeAndActifTrue(String objetType);

	@Query("""
			SELECT m FROM WfModele m
			WHERE (:objetType IS NULL OR m.objetType = :objetType)
			  AND (:actif IS NULL OR m.actif = :actif)
			ORDER BY m.modifieLe DESC
			""")
	Page<WfModele> rechercher(
			@Param("objetType") String objetType,
			@Param("actif") Boolean actif,
			Pageable pageable);
}
