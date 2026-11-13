package com.hr.referentiel.repository;

import com.hr.referentiel.entity.MouvementSoldeConge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MouvementSoldeCongeRepository extends JpaRepository<MouvementSoldeConge, UUID> {

	Page<MouvementSoldeConge> findByCollaborateurIdOrderByCreeLeDescIdDesc(UUID collaborateurId, Pageable pageable);

	Page<MouvementSoldeConge> findByCollaborateurIdAndTypeOrderByCreeLeDescIdDesc(UUID collaborateurId, String type,
			Pageable pageable);

	List<MouvementSoldeConge> findByCollaborateurIdOrderByCreeLeDescIdDesc(UUID collaborateurId);

	boolean existsByCollaborateurIdAndMoisAcquisition(UUID collaborateurId, String moisAcquisition);

	@Query("select m from MouvementSoldeConge m where m.id in :ids")
	List<MouvementSoldeConge> findParIds(@Param("ids") Collection<UUID> ids);

	long countByCollaborateurIdAndMoisAcquisition(UUID collaborateurId, String moisAcquisition);
}
