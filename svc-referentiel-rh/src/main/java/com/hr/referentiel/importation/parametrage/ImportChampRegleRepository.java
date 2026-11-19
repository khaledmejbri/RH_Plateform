package com.hr.referentiel.importation.parametrage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportChampRegleRepository extends JpaRepository<ImportChampRegle, UUID> {

	List<ImportChampRegle> findByTypeImportOrderByOrdreAsc(String typeImport);
}
