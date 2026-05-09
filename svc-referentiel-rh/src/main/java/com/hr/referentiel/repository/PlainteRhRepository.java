package com.hr.referentiel.repository;

import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import com.hr.referentiel.entity.PlainteRh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlainteRhRepository extends JpaRepository<PlainteRh, UUID> {

	List<PlainteRh> findByAuteurIdOrderByCreeLeDesc(UUID auteurId);

	/** Compte les plaintes de cette année pour générer le numéro de ticket YYYY-NNNNNN. */
	@Query("select count(p) from PlainteRh p where year(p.creeLe) = :annee")
	long countByAnnee(@Param("annee") int annee);

	/** Liste toutes les plaintes pour le RH, filtrable par type et/ou statut. */
	@Query("""
			select p from PlainteRh p join fetch p.auteur
			where (:type is null or p.typePlainte = :type)
			  and (:statut is null or p.statut = :statut)
			order by p.creeLe desc
			""")
	List<PlainteRh> findAllPourRhFiltre(
			@Param("type") TypePlainteRh type,
			@Param("statut") StatutPlainteRh statut);

	@Query("select p from PlainteRh p join fetch p.auteur order by p.creeLe desc")
	List<PlainteRh> findAllPourRhOrderByCreeLeDesc();

	Optional<PlainteRh> findByNumeroTicket(String numeroTicket);
}
