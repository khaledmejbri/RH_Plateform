package com.hr.workflow.repository;

import com.hr.workflow.entity.WfJournalModele;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WfJournalModeleRepository extends JpaRepository<WfJournalModele, UUID> {

	Page<WfJournalModele> findByModeleIdOrderByHorodatageDesc(UUID modeleId, Pageable pageable);
}
