package com.hr.presence.repository;

import com.hr.presence.entity.PresenceParametreJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PresenceParametreJournalRepository extends JpaRepository<PresenceParametreJournal, UUID> {

	Page<PresenceParametreJournal> findByReferentielOrderByHorodatageDesc(String referentiel, Pageable pageable);
}
