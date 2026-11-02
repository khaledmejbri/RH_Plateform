package com.hr.presence.repository;

import com.hr.presence.entity.PresenceHoraireSiteHistorique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PresenceHoraireSiteHistoriqueRepository extends JpaRepository<PresenceHoraireSiteHistorique, UUID> {

	Page<PresenceHoraireSiteHistorique> findBySiteIdOrderByModifieLeDesc(UUID siteId, Pageable pageable);

	Optional<PresenceHoraireSiteHistorique> findFirstBySiteIdOrderByModifieLeDesc(UUID siteId);
}
