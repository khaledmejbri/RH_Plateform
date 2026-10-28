package com.hr.presence.repository;

import com.hr.presence.entity.PresenceSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PresenceSiteRepository extends JpaRepository<PresenceSite, UUID> {

	boolean existsByCodeIgnoreCase(String code);

	Optional<PresenceSite> findByCodeIgnoreCase(String code);

	Optional<PresenceSite> findByBorneJetonHash(String borneJetonHash);
}
