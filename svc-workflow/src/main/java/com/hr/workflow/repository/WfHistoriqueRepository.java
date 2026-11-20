package com.hr.workflow.repository;

import com.hr.workflow.entity.WfHistorique;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Journal d'exécution append-only (invariant I5) :
 * aucune méthode de mise à jour ni de suppression n'est exposée.
 */
public interface WfHistoriqueRepository extends Repository<WfHistorique, UUID> {

	WfHistorique save(WfHistorique entity);

	<S extends WfHistorique> List<S> saveAll(Iterable<S> entities);

	List<WfHistorique> findByInstanceIdOrderByHorodatageAsc(UUID instanceId);
}
