package com.hr.evaluation.repository;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.entity.EvaluationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationCampaignRepository extends JpaRepository<EvaluationCampaign, UUID> {

    List<EvaluationCampaign> findByStatutOrderByDateDebutDesc(EvaluationCampaignStatus statut);

    Optional<EvaluationCampaign> findFirstByTypeAndAnneeAndStatutIn(
            EvaluationCampaignType type, 
            Integer annee, 
            List<EvaluationCampaignStatus> statuts);

    List<EvaluationCampaign> findByDateDebutBeforeAndDateFinAfterAndStatut(
            Instant dateDebut, 
            Instant dateFin, 
            EvaluationCampaignStatus statut);

    List<EvaluationCampaign> findByAnneeOrderByDateDebutDesc(Integer annee);
}
