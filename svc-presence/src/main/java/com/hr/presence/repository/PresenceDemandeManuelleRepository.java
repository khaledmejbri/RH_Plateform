package com.hr.presence.repository;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceDemandeManuelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PresenceDemandeManuelleRepository extends JpaRepository<PresenceDemandeManuelle, UUID> {

	boolean existsByPointageRejeteId(UUID pointageRejeteId);

	List<PresenceDemandeManuelle> findByCollaborateurIdOrderByCreeLeDesc(String collaborateurId);

	List<PresenceDemandeManuelle> findByValideurIdAndStatutOrderByCreeLeAsc(String valideurId, StatutDemandeManuelle statut);

	List<PresenceDemandeManuelle> findAllByOrderByCreeLeDesc();

	List<PresenceDemandeManuelle> findByStatutOrderByCreeLeDesc(StatutDemandeManuelle statut);

	/** Pointages manuels validés d'un collaborateur sur une période (alerte RH, D-P04). */
	@Query("select count(d) from PresenceDemandeManuelle d where d.collaborateurId = :collab "
			+ "and d.statut = com.hr.presence.domain.StatutDemandeManuelle.VALIDEE "
			+ "and d.heureDeclaree >= :du and d.heureDeclaree < :au")
	long compterValideesSurPeriode(@Param("collab") String collaborateurId,
			@Param("du") Instant du, @Param("au") Instant au);
}
