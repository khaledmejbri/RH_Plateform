package com.hr.presence.repository;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresencePointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresencePointageRepository extends JpaRepository<PresencePointage, UUID>,
		JpaSpecificationExecutor<PresencePointage> {

	Optional<PresencePointage> findByCollaborateurIdAndIdempotencyKey(String collaborateurId, String idempotencyKey);

	Page<PresencePointage> findByCollaborateurIdOrderByServerTsDesc(String collaborateurId, Pageable pageable);

	/**
	 * Recherche admin : filtres optionnels construits dynamiquement (SCRUM-11).
	 * Une requête JPQL {@code (:p is null or ...)} échouait sous PostgreSQL quand tous les filtres étaient NULL
	 * (« could not determine data type of parameter »).
	 */
	static Specification<PresencePointage> filtreAdmin(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (statut != null) predicats.add(cb.equal(root.get("statut"), statut));
			if (type != null) predicats.add(cb.equal(root.get("type"), type));
			if (from != null) predicats.add(cb.greaterThanOrEqualTo(root.get("serverTs"), from));
			if (to != null) predicats.add(cb.lessThan(root.get("serverTs"), to));
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}
}
