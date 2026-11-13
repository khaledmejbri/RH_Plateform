package com.hr.referentiel.repository;

import com.hr.referentiel.entity.ParametresConges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ReglagesCongesRepository extends JpaRepository<ParametresConges, Short> {

	/** Ligne unique : créée par l'amorçage, ou ici si une écriture arrive avant lui. */
	@Modifying
	@Query(value = "insert into rh_parametres (id, jours_acquis_par_mois, debut_acquisitions_le, version, modifie_le) "
			+ "values (1, :jours, :debut, 0, current_timestamp) on conflict do nothing", nativeQuery = true)
	void creerSiAbsente(@Param("jours") BigDecimal jours, @Param("debut") LocalDate debut);
}
