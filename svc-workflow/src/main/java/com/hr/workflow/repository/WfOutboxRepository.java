package com.hr.workflow.repository;

import com.hr.workflow.entity.WfOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WfOutboxRepository extends JpaRepository<WfOutbox, UUID> {

	@Query(value = """
			SELECT * FROM wf_outbox
			WHERE publie_le IS NULL
			  AND prochaine_tentative_le <= :maintenant
			ORDER BY cree_le ASC
			LIMIT :limite
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<WfOutbox> verrouillerLotAPublier(
			@Param("maintenant") Instant maintenant,
			@Param("limite") int limite);

	long countByPublieLeIsNull();
}
