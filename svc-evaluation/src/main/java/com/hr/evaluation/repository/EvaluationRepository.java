package com.hr.evaluation.repository;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
