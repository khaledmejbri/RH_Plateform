package com.hr.workflow.repository;

import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.entity.WfModeleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfModeleVersionRepository extends JpaRepository<WfModeleVersion, UUID> {

	List<WfModeleVersion> findByModeleIdOrderByNumeroDesc(UUID modeleId);

	Optional<WfModeleVersion> findByModeleIdAndStatut(UUID modeleId, StatutVersion statut);

	Optional<WfModeleVersion> findTopByModeleIdOrderByNumeroDesc(UUID modeleId);

	boolean existsByModeleIdAndStatut(UUID modeleId, StatutVersion statut);

	boolean existsByModeleIdAndStatutIn(UUID modeleId, List<StatutVersion> statuts);

	@Query("""
			SELECT v FROM WfModeleVersion v
			JOIN FETCH v.modele m
			WHERE v.id = :id
			""")
	Optional<WfModeleVersion> findByIdAvecModele(@Param("id") UUID id);

	@Query("""
			SELECT COUNT(v) > 0 FROM WfModeleVersion v
			JOIN v.modele m
			WHERE m.objetType = :objetType
			  AND m.actif = true
			  AND v.statut = com.hr.workflow.domain.StatutVersion.PUBLIEE
			""")
	boolean existsVersionPublieePourObjetType(@Param("objetType") String objetType);

	@Query("""
			SELECT v FROM WfModeleVersion v
			JOIN FETCH v.modele m
			WHERE m.actif = true
			  AND v.statut = com.hr.workflow.domain.StatutVersion.PUBLIEE
			""")
	List<WfModeleVersion> findAllPublieesActives();
}
