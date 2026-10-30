package com.hr.presence.repository;

import com.hr.presence.entity.PresenceConsentementVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Les versions publiées ne sont jamais modifiées ni supprimées : seules la lecture et l'ajout sont utilisés. */
public interface ConsentementVersionRepository extends JpaRepository<PresenceConsentementVersion, String> {

	/** Version courante = la plus récemment publiée. */
	Optional<PresenceConsentementVersion> findFirstByOrderByPublieLeDescVersionDesc();

	List<PresenceConsentementVersion> findAllByOrderByPublieLeDescVersionDesc();
}
