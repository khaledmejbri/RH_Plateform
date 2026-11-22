package com.hr.presence.repository;

import com.hr.presence.entity.PresenceWorkflowEffet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PresenceWorkflowEffetRepository extends JpaRepository<PresenceWorkflowEffet, String> {

	Optional<PresenceWorkflowEffet> findByCleIdempotence(String cleIdempotence);
}
