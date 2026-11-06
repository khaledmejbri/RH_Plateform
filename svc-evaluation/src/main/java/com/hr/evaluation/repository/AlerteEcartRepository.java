package com.hr.evaluation.repository;

import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlerteEcartRepository extends JpaRepository<AlerteEcart, UUID>, JpaSpecificationExecutor<AlerteEcart> {

    /** Verrou d'écriture : les actions RH et le recalcul sont sérialisés par alerte (ADR § 9.1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlerteEcart a where a.id = :id")
    Optional<AlerteEcart> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlerteEcart a where a.evaluation.id = :evaluationId")
    Optional<AlerteEcart> findByEvaluationIdForUpdate(UUID evaluationId);

    @Query("select a from AlerteEcart a where a.evaluation.id = :evaluationId")
    Optional<AlerteEcart> findByEvaluationId(UUID evaluationId);

    /** D-E12.c : alertes OUVERTE / EN_COURS par niveau, toutes campagnes. */
    @Query("select a.niveauCourant, count(a) from AlerteEcart a where a.statut in :statuts group by a.niveauCourant")
    List<Object[]> compterParNiveau(Collection<StatutAlerteEcart> statuts);

    /** D-E12.c : idem pour une campagne. */
    @Query("select a.niveauCourant, count(a) from AlerteEcart a where a.statut in :statuts "
            + "and a.evaluation.campaign.id = :campagneId group by a.niveauCourant")
    List<Object[]> compterParNiveauEtCampagne(Collection<StatutAlerteEcart> statuts, UUID campagneId);

    long countByStatutNot(StatutAlerteEcart statut);

    long countByStatutNotAndNiveauCourant(StatutAlerteEcart statut, NiveauEcart niveauCourant);
}
