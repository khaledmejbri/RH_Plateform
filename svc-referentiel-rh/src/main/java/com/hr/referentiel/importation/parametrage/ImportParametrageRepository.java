package com.hr.referentiel.importation.parametrage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImportParametrageRepository extends JpaRepository<ImportParametrage, String> {

	/** Verrou de ligne : sérialise toutes les écritures d'un type d'import (ADR § 5). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ImportParametrage p where p.typeImport = :type")
	Optional<ImportParametrage> findByIdForUpdate(@Param("type") String type);

	/**
	 * Verrou partagé ({@code FOR SHARE}) pris par les écritures de valeurs : empêche une écriture de définition
	 * concurrente (qui prend le {@code FOR UPDATE}) de changer le type ou de supprimer un champ pendant l'écriture.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select p from ImportParametrage p where p.typeImport = :type")
	Optional<ImportParametrage> findByIdForShare(@Param("type") String type);
}
