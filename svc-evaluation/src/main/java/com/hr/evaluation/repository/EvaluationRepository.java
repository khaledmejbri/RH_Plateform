package com.hr.evaluation.repository;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    List<Evaluation> findByCampaignId(UUID campaignId);

    List<Evaluation> findByCollaborateurIdentifiantOrderByCreeLeDesc(UUID collaborateurIdentifiant);

    List<Evaluation> findBySuperieurIdentifiantOrderByCreeLeDesc(UUID superieurIdentifiant);

    Optional<Evaluation> findByCampaignIdAndCollaborateurIdentifiant(
            UUID campaignId, 
            UUID collaborateurIdentifiant);

    List<Evaluation> findByCampaignIdAndStatut(UUID campaignId, StatutEvaluationRh statut);

    List<Evaluation> findByCampaignIdAndProfilMetierIncompletTrue(UUID campaignId);

    List<Evaluation> findByProfilMetierIncompletTrue();
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign WHERE e.collaborateurIdentifiant = :collaborateurIdentifiant ORDER BY e.creeLe DESC")
    List<Evaluation> findByCollaborateurIdentifiantOrderByCreeLeDescWithCampaign(UUID collaborateurIdentifiant);
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign WHERE e.superieurIdentifiant = :superieurIdentifiant ORDER BY e.creeLe DESC")
    List<Evaluation> findBySuperieurIdentifiantOrderByCreeLeDescWithCampaign(UUID superieurIdentifiant);
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign c LEFT JOIN FETCH c.templateGeneral LEFT JOIN FETCH c.templateTechnique LEFT JOIN FETCH c.templateCompetence LEFT JOIN FETCH e.templateCompetenceAssigne WHERE e.id = :id")
    Optional<Evaluation> findByIdWithCampaign(UUID id);

    /**
     * Verrou d'écriture sur la ligne seule (pas de jointure : PostgreSQL refuse FOR UPDATE sur le côté nullable d'une
     * jointure externe). À faire suivre de {@link #findByIdWithCampaign} pour charger campagne et templates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evaluation e WHERE e.id = :id")
    Optional<Evaluation> findByIdForUpdate(UUID id);
}
