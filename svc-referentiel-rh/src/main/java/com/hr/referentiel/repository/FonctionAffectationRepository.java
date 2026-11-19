package com.hr.referentiel.repository;

import com.hr.referentiel.entity.FonctionAffectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FonctionAffectationRepository extends JpaRepository<FonctionAffectation, UUID> {

	boolean existsByFonctionId(UUID fonctionId);

	@Query("""
			SELECT a FROM FonctionAffectation a
			JOIN FETCH a.collaborateur c
			LEFT JOIN FETCH a.unitePerimetre u
			WHERE a.fonction.id = :fonctionId
			ORDER BY a.du DESC, c.nom ASC, c.prenom ASC
			""")
	List<FonctionAffectation> findAllByFonctionIdAvecDetails(@Param("fonctionId") UUID fonctionId);

	@Query("""
			SELECT a FROM FonctionAffectation a
			JOIN FETCH a.fonction f
			JOIN FETCH a.collaborateur c
			LEFT JOIN FETCH a.unitePerimetre u
			WHERE f.code IN :codes
			  AND f.actif = true
			  AND a.du <= :date
			  AND (a.au IS NULL OR a.au >= :date)
			""")
	List<FonctionAffectation> findActivesParCodesEtDate(
			@Param("codes") Collection<String> codes,
			@Param("date") LocalDate date);
}
