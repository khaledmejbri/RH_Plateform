package com.hr.workflow.repository;

import com.hr.workflow.entity.WfDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository de la table {@code wf_delegation} (D-W21, D-W4-05). */
public interface WfDelegationRepository extends JpaRepository<WfDelegation, UUID>, JpaSpecificationExecutor<WfDelegation> {

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext('wf-del-' || CAST(:delegantId AS text)))", nativeQuery = true)
	Object verrouillerDelegant(@Param("delegantId") UUID delegantId);

	@Query("""
			SELECT COUNT(d) > 0 FROM WfDelegation d
			WHERE d.delegantCollaborateurId = :delegantId
			  AND d.annuleeLe IS NULL
			  AND d.du <= :au
			  AND d.au >= :du
			""")
	boolean existsChevauchement(
			@Param("delegantId") UUID delegantId,
			@Param("du") LocalDate du,
			@Param("au") LocalDate au);

	@Query("""
			SELECT d FROM WfDelegation d
			WHERE d.delegantCollaborateurId IN :delegantIds
			  AND d.annuleeLe IS NULL
			  AND d.du <= :date
			  AND d.au >= :date
			ORDER BY d.creeeLe ASC
			""")
	List<WfDelegation> findActivesPourDelegantsALaDate(
			@Param("delegantIds") Collection<UUID> delegantIds,
			@Param("date") LocalDate date);

	@Query("""
			SELECT d FROM WfDelegation d
			WHERE d.annuleeLe IS NULL
			  AND d.du <= :date
			  AND d.au >= :date
			ORDER BY d.creeeLe ASC
			""")
	List<WfDelegation> findActivesALaDate(@Param("date") LocalDate date);
}
