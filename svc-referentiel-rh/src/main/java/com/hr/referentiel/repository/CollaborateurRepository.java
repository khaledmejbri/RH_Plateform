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

	/** Tous les collaborateurs d'une unité sans pagination (pour le workflow RO). */
	@EntityGraph(attributePaths = {"unite", "superieur"})
	@Query("select c from Collaborateur c where c.unite.id = :uniteId")
	java.util.List<Collaborateur> findByUniteId(@Param("uniteId") UUID uniteId);

	@EntityGraph(attributePaths = {"unite", "superieur"})
	Page<Collaborateur> findByStatutIgnoreCaseAndUniteId(String statut, UUID uniteId, Pageable pageable);


	/**
	 * Trouve le Responsable Opérationnel (profil_acces=RO) d'une unité.
	 * Utilisé par le workflow de validation des demandes administratives (CDC §M01).
	 */
	@Query("select c from Collaborateur c join fetch c.unite where c.unite.id = :uniteId and c.profilAcces = 'RO' and c.statut = 'ACTIF'")
	Optional<Collaborateur> findRoByUniteId(@Param("uniteId") UUID uniteId);

	Optional<Collaborateur> findByCompteUtilisateurId(UUID compteUtilisateurId);

	/**
	 * Trouve le Chef de Département (profil_acces=RESPONSABLE) d'une unité parente (département).
	 * Utilisé pour notifier le chef après validation RO → RRH.
	 */
	@Query("select c from Collaborateur c join fetch c.unite " +
			"where c.unite.id = :uniteParentId and c.profilAcces = 'RESPONSABLE' and c.statut = 'ACTIF'")
	Optional<Collaborateur> findChefDepartementByUniteId(@Param("uniteParentId") UUID uniteParentId);

	/**
	 * Trouve tous les collaborateurs avec profil RH (profil_acces=RH).
	 * Utilisé pour notifier le RRH après validation RO.
	 */
	@Query("select c from Collaborateur c where c.profilAcces = 'RH' and c.statut = 'ACTIF'")
	java.util.List<Collaborateur> findAllRhActifs();
}
