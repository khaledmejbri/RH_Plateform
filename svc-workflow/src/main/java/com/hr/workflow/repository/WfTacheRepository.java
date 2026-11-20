package com.hr.workflow.repository;

import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.entity.WfTache;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfTacheRepository extends JpaRepository<WfTache, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM WfTache t WHERE t.id = :id")
	Optional<WfTache> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH i.modeleVersion v
			JOIN FETCH t.etapeExecution e
			WHERE t.id = :id
			""")
	Optional<WfTache> findByIdAvecDetails(@Param("id") UUID id);

	List<WfTache> findByInstanceId(UUID instanceId);

	List<WfTache> findByInstanceIdAndStatutIn(UUID instanceId, Collection<StatutTache> statuts);

	List<WfTache> findByEtapeExecutionId(UUID etapeExecutionId);

	Optional<WfTache> findByCleIdempotence(String cleIdempotence);

	@Query(value = """
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH t.etapeExecution e
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			ORDER BY t.echeanceLe ASC NULLS LAST, t.id ASC
			""",
			countQuery = """
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	Page<WfTache> findATraiterPourCompte(@Param("compteId") UUID compteId, Pageable pageable);

	@Query(value = """
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH t.etapeExecution e
			WHERE t.statut = com.hr.workflow.domain.StatutTache.TRAITEE
			  AND t.decideParCompteId = :compteId
			  AND t.decideLe >= :depuis
			ORDER BY t.decideLe DESC, t.id DESC
			""",
			countQuery = """
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.TRAITEE
			  AND t.decideParCompteId = :compteId
			  AND t.decideLe >= :depuis
			""")
	Page<WfTache> findTraiteesPourCompte(
			@Param("compteId") UUID compteId,
			@Param("depuis") Instant depuis,
			Pageable pageable);

	@Query("""
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	long countATraiterPourCompte(@Param("compteId") UUID compteId);

	@Query("""
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.echeanceLe IS NOT NULL
			  AND t.echeanceLe < :maintenant
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	long countEnRetardPourCompte(@Param("compteId") UUID compteId, @Param("maintenant") Instant maintenant);

	@Query("""
			SELECT DISTINCT t.decideParCollaborateurId FROM WfTache t
			WHERE t.instance.id = :instanceId
			  AND t.decision = com.hr.workflow.domain.DecisionTache.APPROUVEE
			  AND t.decideParCollaborateurId IS NOT NULL
			""")
	List<UUID> findApprobateursCollaborateurIds(@Param("instanceId") UUID instanceId);
}
