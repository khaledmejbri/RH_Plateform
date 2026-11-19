package com.hr.referentiel.repository;

import com.hr.referentiel.entity.ParametresConges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ReglagesCongesRepository extends JpaRepository<ParametresConges, Short> {

	/** Ligne unique par société : créée par l'amorçage, ou ici si une écriture arrive avant lui. */
	@org.springframework.transaction.annotation.Transactional
	@Modifying
	@Query(value = "insert into rh_parametres (id, societe_id, jours_acquis_par_mois, debut_acquisitions_le, version, modifie_le) "
			+ "values ((select coalesce(max(p.id), 0) + 1 from rh_parametres p), :societeId, :jours, :debut, 0, current_timestamp) "
			+ "on conflict do nothing", nativeQuery = true)
	void creerSiAbsente(@Param("societeId") UUID societeId, @Param("jours") BigDecimal jours, @Param("debut") LocalDate debut);

	Optional<ParametresConges> findFirstByOrderByIdAsc();
}
