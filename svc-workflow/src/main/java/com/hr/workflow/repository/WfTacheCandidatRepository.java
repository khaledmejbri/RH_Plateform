package com.hr.workflow.repository;

import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfTacheCandidatRepository extends JpaRepository<WfTacheCandidat, WfTacheCandidatId> {

	List<WfTacheCandidat> findByIdTacheId(UUID tacheId);

	List<WfTacheCandidat> findByIdTacheIdIn(Collection<UUID> tacheIds);

	List<WfTacheCandidat> findByIdTacheIdAndRetireLeIsNull(UUID tacheId);

	Optional<WfTacheCandidat> findByIdTacheIdAndCompteIdAndRetireLeIsNull(UUID tacheId, UUID compteId);

	boolean existsByIdTacheIdAndCompteId(UUID tacheId, UUID compteId);

	@Query("""
			SELECT COUNT(c) > 0 FROM WfTacheCandidat c
			JOIN WfTache t ON t.id = c.id.tacheId
			WHERE t.instance.id = :instanceId
			  AND c.compteId = :compteId
			""")
	boolean existsCandidatPourInstanceEtCompte(@Param("instanceId") UUID instanceId, @Param("compteId") UUID compteId);

	@Query("""
			SELECT c FROM WfTacheCandidat c
			JOIN WfTache t ON t.id = c.id.tacheId
			WHERE c.delegationId = :delegationId
			  AND c.retireLe IS NULL
			  AND t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			""")
	List<WfTacheCandidat> findActifsParDelegationSurTachesOuvertes(@Param("delegationId") UUID delegationId);

	@Query("""
			SELECT c FROM WfTacheCandidat c
			JOIN WfTache t ON t.id = c.id.tacheId
			WHERE c.origine = com.hr.workflow.domain.OrigineCandidat.DELEGUE
			  AND c.retireLe IS NULL
			  AND t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND (
			    c.delegationId IS NULL
			    OR EXISTS (
			      SELECT 1 FROM WfDelegation d
			      WHERE d.id = c.delegationId
			        AND (d.annuleeLe IS NOT NULL OR d.au < :aujourdhui OR d.du > :aujourdhui)
			    )
			  )
			""")
	List<WfTacheCandidat> findCandidatsDeleguesExpiresSurTachesOuvertes(@Param("aujourdhui") LocalDate aujourdhui);

	@Query("""
			SELECT c FROM WfTacheCandidat c
			JOIN WfTache t ON t.id = c.id.tacheId
			WHERE c.origine = com.hr.workflow.domain.OrigineCandidat.DELEGUE_AUTO
			  AND c.retireLe IS NULL
			  AND t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			""")
	List<WfTacheCandidat> findCandidatsDeleguesAutoSurTachesOuvertes();
}
