package com.hr.referentiel.repository;

import com.hr.referentiel.entity.Collaborateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CollaborateurRepository extends JpaRepository<Collaborateur, UUID> {

	boolean existsByMatriculeIgnoreCase(String matricule);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	@Query("select c from Collaborateur c where c.id = :id")
	Optional<Collaborateur> findDetailById(@Param("id") UUID id);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	@Query("select c from Collaborateur c where lower(c.matricule) = lower(:matricule)")
	Optional<Collaborateur> findDetailByMatricule(@Param("matricule") String matricule);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	Page<Collaborateur> findAll(Pageable pageable);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	Page<Collaborateur> findByStatutIgnoreCase(String statut, Pageable pageable);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	Page<Collaborateur> findByUniteId(UUID uniteId, Pageable pageable);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	Page<Collaborateur> findByStatutIgnoreCaseAndUniteId(String statut, UUID uniteId, Pageable pageable);

	Optional<Collaborateur> findByCompteUtilisateurId(UUID compteUtilisateurId);
}
