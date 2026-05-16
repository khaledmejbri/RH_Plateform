package com.hr.referentiel.repository;

import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeFormationRhRepository extends JpaRepository<DemandeFormationRh, UUID> {

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
