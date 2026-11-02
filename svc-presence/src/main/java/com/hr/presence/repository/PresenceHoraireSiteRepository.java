package com.hr.presence.repository;

import com.hr.presence.entity.PresenceHoraireSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PresenceHoraireSiteRepository extends JpaRepository<PresenceHoraireSite, UUID> {

	List<PresenceHoraireSite> findBySiteIdOrderByJourSemaine(UUID siteId);

	/** Sites ayant au moins un jour d'horaire (D-P11.b : les autres gardent le comportement antérieur). */
	@Query("select distinct h.siteId from PresenceHoraireSite h")
	List<UUID> findSiteIdsAvecHoraire();
}
