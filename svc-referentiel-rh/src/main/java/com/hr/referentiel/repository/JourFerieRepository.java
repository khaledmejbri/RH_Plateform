package com.hr.referentiel.repository;

import com.hr.referentiel.entity.JourFerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JourFerieRepository extends JpaRepository<JourFerie, UUID> {

	Optional<JourFerie> findByDateJour(LocalDate dateJour);

	List<JourFerie> findByDateJourBetweenOrderByDateJourAsc(LocalDate debut, LocalDate fin);

	List<JourFerie> findAllByOrderByDateJourAsc();

	List<JourFerie> findByActifTrueAndDateJourBetweenOrderByDateJourAsc(LocalDate debut, LocalDate fin);

	@Query("select j.dateJour from JourFerie j where j.actif = true")
	List<LocalDate> datesActives();

	@Query("select j.dateJour from JourFerie j where j.actif = true and j.dateJour between :debut and :fin")
	List<LocalDate> datesActivesEntre(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
