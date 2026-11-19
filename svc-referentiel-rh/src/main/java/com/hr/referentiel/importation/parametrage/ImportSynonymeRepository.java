package com.hr.referentiel.importation.parametrage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportSynonymeRepository extends JpaRepository<ImportSynonyme, UUID> {

	List<ImportSynonyme> findByTypeImport(String typeImport);

	List<ImportSynonyme> findByTypeImportAndActifTrue(String typeImport);

	Optional<ImportSynonyme> findByIdAndTypeImport(UUID id, String typeImport);
}
