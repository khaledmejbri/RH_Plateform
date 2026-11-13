package com.hr.referentiel.repository;

import com.hr.referentiel.entity.SoldeConge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SoldeCongeRepository extends JpaRepository<SoldeConge, UUID> {

	/** Crée la ligne du solde si elle manque (sans erreur en cas de concurrence). */
	@Modifying
	@Query(value = "insert into rh_solde_conge (collaborateur_id, acquis, version, modifie_le) "
			+ "values (:id, 0, 0, current_timestamp) on conflict do nothing", nativeQuery = true)
	void creerSiAbsent(@Param("id") UUID id);

	/** Verrou d'écriture sur la ligne du solde (ordre des verrous : demande puis solde). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SoldeConge s where s.collaborateurId = :id")
	Optional<SoldeConge> verrouiller(@Param("id") UUID id);

	/** Acquis seuls (projection : aucune entité gérée, donc aucune instance périmée dans le contexte). */
	@Query("select s.collaborateurId, s.acquis from SoldeConge s where s.collaborateurId in :ids")
	List<Object[]> acquisParIds(@Param("ids") Collection<UUID> ids);

	@Query("select s from SoldeConge s where s.collaborateurId in :ids")
	List<SoldeConge> findParIds(@Param("ids") Collection<UUID> ids);
}
