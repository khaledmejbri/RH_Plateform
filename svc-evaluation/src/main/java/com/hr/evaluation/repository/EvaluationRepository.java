package com.hr.evaluation.repository;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
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
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign WHERE e.collaborateurIdentifiant = :collaborateurIdentifiant ORDER BY e.creeLe DESC")
    List<Evaluation> findByCollaborateurIdentifiantOrderByCreeLeDescWithCampaign(UUID collaborateurIdentifiant);
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign WHERE e.superieurIdentifiant = :superieurIdentifiant ORDER BY e.creeLe DESC")
    List<Evaluation> findBySuperieurIdentifiantOrderByCreeLeDescWithCampaign(UUID superieurIdentifiant);
    
    @Query("SELECT e FROM Evaluation e LEFT JOIN FETCH e.campaign c LEFT JOIN FETCH c.templateGeneral LEFT JOIN FETCH c.templateTechnique LEFT JOIN FETCH c.templateCompetence WHERE e.id = :id")
    Optional<Evaluation> findByIdWithCampaign(UUID id);
}
