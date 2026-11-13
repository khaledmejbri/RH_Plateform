package com.hr.referentiel.importation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportLigneRepository extends JpaRepository<ImportLigne, UUID> {

	Page<ImportLigne> findByImportIdOrderByNumeroLigneAsc(UUID importId, Pageable pageable);
}
