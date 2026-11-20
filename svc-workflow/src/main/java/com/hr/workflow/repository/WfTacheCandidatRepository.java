package com.hr.workflow.repository;

import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
