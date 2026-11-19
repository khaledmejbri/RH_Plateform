package com.hr.referentiel.repository;

import com.hr.referentiel.entity.AcquisitionMois;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface AcquisitionMoisRepository extends JpaRepository<AcquisitionMois, String> {

	/** Fige N au premier passage du mois (D-S07.c) pour une société : un second appel ne change rien. */
	@org.springframework.transaction.annotation.Transactional
	@Modifying
	@Query(value = "insert into rh_acquisition_mois (societe_id, mois, jours_par_mois, premier_passage_le) "
			+ "values (:societeId, :mois, :jours, current_timestamp) on conflict do nothing", nativeQuery = true)
	void figerSiAbsent(@Param("societeId") UUID societeId, @Param("mois") String mois, @Param("jours") BigDecimal jours);
}
