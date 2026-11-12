package com.hr.evaluation.repository;

import com.hr.evaluation.entity.ParametreJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParametreJournalRepository extends JpaRepository<ParametreJournal, UUID> {

    Page<ParametreJournal> findByReferentielOrderByHorodatageDesc(String referentiel, Pageable pageable);
}
