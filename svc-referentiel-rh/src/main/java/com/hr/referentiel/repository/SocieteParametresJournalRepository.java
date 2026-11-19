package com.hr.referentiel.repository;

import com.hr.referentiel.entity.SocieteParametresJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SocieteParametresJournalRepository extends JpaRepository<SocieteParametresJournal, UUID> {

	Page<SocieteParametresJournal> findAllByOrderByHorodatageDesc(Pageable pageable);
}
