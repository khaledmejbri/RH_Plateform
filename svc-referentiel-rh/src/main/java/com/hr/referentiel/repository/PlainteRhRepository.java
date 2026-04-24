package com.hr.referentiel.repository;

import com.hr.referentiel.entity.PlainteRh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlainteRhRepository extends JpaRepository<PlainteRh, UUID> {

	List<PlainteRh> findByAuteurIdOrderByCreeLeDesc(UUID auteurId);

	@Query("select p from PlainteRh p join fetch p.auteur order by p.creeLe desc")
	List<PlainteRh> findAllPourRhOrderByCreeLeDesc();
}
