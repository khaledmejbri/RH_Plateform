package com.hr.referentiel.repository;

import com.hr.referentiel.entity.UniteOrganisation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UniteOrganisationRepository extends JpaRepository<UniteOrganisation, UUID> {

	Optional<UniteOrganisation> findByCodeIgnoreCase(String code);

	@EntityGraph(attributePaths = "parent")
	List<UniteOrganisation> findByActifTrueOrderByCodeAsc();

	@EntityGraph(attributePaths = "parent")
	@Override
	Optional<UniteOrganisation> findById(UUID id);
}
