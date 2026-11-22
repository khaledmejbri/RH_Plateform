package com.hr.referentiel.repository;

import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeFormationRhRepository extends JpaRepository<DemandeFormationRh, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DemandeFormationRh d where d.id = :id")
	Optional<DemandeFormationRh> findByIdForUpdate(@Param("id") UUID id);

	@Query("select d from DemandeFormationRh d join fetch d.demandeur where d.id = :id")
	Optional<DemandeFormationRh> findByIdAvecDemandeur(@Param("id") UUID id);

	@Query(value = "SELECT identifiant FROM rh_demande_formation "
			+ "WHERE societe_id = :societeId AND workflow_mode = 'MOTEUR' "
			+ "AND workflow_etat = 'A_DEMARRER' AND statut <> 'ANNULEE' "
			+ "ORDER BY cree_le ASC LIMIT :limite FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	List<UUID> findIdsADemarrerPourReprise(@Param("societeId") UUID societeId, @Param("limite") int limite);

	/** D-L02 : recherche RH par numéro lisible (stocké en majuscules ; correspondance exacte). */
	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	List<DemandeFormationRh> findByNumero(String numero);

	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	List<DemandeFormationRh> findByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	List<DemandeFormationRh> findByStatutOrderByCreeLeDesc(StatutDemandeFormationRh statut);

	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	List<DemandeFormationRh> findAllByOrderByCreeLeDesc();

	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	List<DemandeFormationRh> findByDateSouhaiteeFinAndStatut(LocalDate dateFin, StatutDemandeFormationRh statut);

	@EntityGraph(attributePaths = {"demandeur", "demandeur.unite", "uniteCible", "collaborateursCibles"})
	Optional<DemandeFormationRh> findDetailById(UUID id);
}
