package com.hr.referentiel.repository;

import com.hr.referentiel.entity.AcquisitionMois;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface AcquisitionMoisRepository extends JpaRepository<AcquisitionMois, String> {

	/** Fige N au premier passage du mois (D-S07.c) : un second appel ne change rien. */
	@Modifying
	@Query(value = "insert into rh_acquisition_mois (mois, jours_par_mois, premier_passage_le) "
			+ "values (:mois, :jours, current_timestamp) on conflict do nothing", nativeQuery = true)
	void figerSiAbsent(@Param("mois") String mois, @Param("jours") BigDecimal jours);
}
