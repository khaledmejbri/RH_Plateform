package com.hr.referentiel.repository;

import com.hr.referentiel.entity.UniteOrganisation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UniteOrganisationRepository extends JpaRepository<UniteOrganisation, UUID> {

	Optional<UniteOrganisation> findByCodeIgnoreCase(String code);

	@EntityGraph(attributePaths = "parent")
	List<UniteOrganisation> findByActifTrueOrderByCodeAsc();

	@EntityGraph(attributePaths = {"parent", "manager"})
	@Query("select u from UniteOrganisation u where u.actif = true order by u.code asc")
	List<UniteOrganisation> findByActifTrueWithParentAndManager();

	@EntityGraph(attributePaths = {"parent", "manager"})
	@Query("select u from UniteOrganisation u order by u.code asc")
	List<UniteOrganisation> findAllWithParentAndManager();

	@EntityGraph(attributePaths = {"parent", "manager"})
	List<UniteOrganisation> findByParentId(UUID parentId);

	/** Nœuds dont la personne est le manager (fiche de hiérarchie, D-N01). */
	@EntityGraph(attributePaths = {"parent", "manager"})
	List<UniteOrganisation> findByManagerId(UUID managerId);

	@EntityGraph(attributePaths = {"parent", "manager"})
	@Override
	Optional<UniteOrganisation> findById(UUID id);
}
