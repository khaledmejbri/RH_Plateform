package com.hr.referentiel.repository;

import com.hr.referentiel.entity.Collaborateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface CollaborateurRepository extends JpaRepository<Collaborateur, UUID> {

	/** D-F06 : verrou d'écriture sur la fiche pour sérialiser deux demandes simultanées du même collaborateur. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Collaborateur c where c.id = :id")
	Optional<Collaborateur> findByIdForUpdate(@Param("id") UUID id);

	boolean existsByMatriculeIgnoreCase(String matricule);

	@EntityGraph(attributePaths = {"unite", "unite.manager", "superieur"})
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

	/** Back-office qui traite l'étape RRH (§ 4.2 BACKOFFICE_ECRITURE) : profils RH et ADMIN actifs. */
	@Query("select c from Collaborateur c where c.profilAcces in ('RH', 'ADMIN') and c.statut = 'ACTIF'")
	java.util.List<Collaborateur> findAllBackofficeEcritureActifs();

	@EntityGraph(attributePaths = {"unite", "superieur"})
	@Query("select c from Collaborateur c where c.statut = 'ACTIF'")
	java.util.List<Collaborateur> findAllActifsWithUnite();

	/** Tous les collaborateurs, quel que soit leur statut, avec leur unité (vue « Temps de travail » de svc-presence). */
	@EntityGraph(attributePaths = {"unite"})
	@Query("select c from Collaborateur c")
	java.util.List<Collaborateur> findAllWithUnite();

	@EntityGraph(attributePaths = {"unite"})
	java.util.List<Collaborateur> findAllByCompteUtilisateurIdIn(java.util.Collection<UUID> compteIds);

	/** D-S07.a : fiches ACTIVES existant avant la fin du 1er du mois, recrutées au plus tard le 1er (ou sans date). */
	@Query("select c.id from Collaborateur c where upper(c.statut) = 'ACTIF' and c.creeLe < :limite "
			+ "and (c.dateRecrutement is null or c.dateRecrutement <= :premierJour)")
	java.util.List<UUID> idsEligiblesAcquisition(@Param("limite") java.time.Instant limite,
			@Param("premierJour") java.time.LocalDate premierJour);

	/** Lecture groupée par matricule (import : jamais une requête par ligne). */
	@Query("select c from Collaborateur c where lower(c.matricule) in :matricules")
	java.util.List<Collaborateur> findParMatricules(@Param("matricules") java.util.Collection<String> matriculesMinuscules);
}
