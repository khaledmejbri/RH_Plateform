package com.hr.referentiel.importation.parametrage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ImportChampJournalRepository extends JpaRepository<ImportChampJournal, UUID> {

	Page<ImportChampJournal> findByTypeImportOrderByHorodatageDescIdAsc(String typeImport, Pageable pageable);

	/** Codes déjà cités au journal du type : un code de champ personnalisé n'est jamais réattribué (P2-2). */
	@Query("select distinct j.champCode from ImportChampJournal j where j.typeImport = :type and j.champCode is not null")
	List<String> codesCites(@Param("type") String type);
}
