package com.hr.workflow.repository;

import com.hr.workflow.entity.WfInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WfInstanceRepository extends JpaRepository<WfInstance, UUID> {

	Optional<WfInstance> findByCleDemarrage(String cleDemarrage);

	Optional<WfInstance> findByObjetTypeAndObjetId(String objetType, UUID objetId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT i FROM WfInstance i WHERE i.id = :id")
	Optional<WfInstance> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			SELECT i FROM WfInstance i
			JOIN FETCH i.modeleVersion v
			WHERE i.id = :id
			""")
	Optional<WfInstance> findByIdAvecVersion(@Param("id") UUID id);

	@Query("""
			SELECT i FROM WfInstance i
			JOIN FETCH i.modeleVersion v
			WHERE i.objetType = :objetType AND i.objetId = :objetId
			""")
	Optional<WfInstance> findByObjetTypeAndObjetIdAvecVersion(
			@Param("objetType") String objetType,
			@Param("objetId") UUID objetId);
}
