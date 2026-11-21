package com.hr.workflow.repository;

import com.hr.workflow.entity.WfAppelEffet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfAppelEffetRepository extends JpaRepository<WfAppelEffet, UUID> {

	Optional<WfAppelEffet> findByCleIdempotence(String cleIdempotence);

	@Query(value = """
			SELECT * FROM wf_appel_effet
			WHERE (statut = 'INCERTAIN' AND prochaine_tentative_le <= :maintenant)
			   OR (statut = 'RESERVE' AND cree_le < :seuilReserve)
			ORDER BY COALESCE(prochaine_tentative_le, cree_le) ASC
			LIMIT :limite
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<WfAppelEffet> verrouillerLotIncertains(
			@Param("maintenant") Instant maintenant,
			@Param("seuilReserve") Instant seuilReserve,
			@Param("limite") int limite);
}
