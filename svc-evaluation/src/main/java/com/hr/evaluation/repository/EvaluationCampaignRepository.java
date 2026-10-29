package com.hr.evaluation.repository;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.entity.EvaluationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /** Unicité par période (SCRUM-41). */
    List<EvaluationCampaign> findByTypeAndAnneeAndStatutIn(
            EvaluationCampaignType type, Integer annee, List<EvaluationCampaignStatus> statuts);

    /**
     * Lancement automatique : campagnes planifiées dont la date de début est atteinte (SCRUM-42).
     * Template général chargé d'emblée : la tâche planifiée le lit hors transaction.
     */
    @Query("SELECT c FROM EvaluationCampaign c LEFT JOIN FETCH c.templateGeneral "
            + "WHERE c.statut = :statut AND c.dateDebut <= :date")
    List<EvaluationCampaign> findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus statut, Instant date);

    /** Clôture automatique : campagnes actives dont la date de fin est passée (SCRUM-42). */
    List<EvaluationCampaign> findByStatutAndDateFinBefore(EvaluationCampaignStatus statut, Instant date);
    
    @Query("SELECT c FROM EvaluationCampaign c LEFT JOIN FETCH c.templateGeneral LEFT JOIN FETCH c.templateTechnique LEFT JOIN FETCH c.templateCompetence WHERE c.statut = :statut ORDER BY c.dateDebut DESC")
    List<EvaluationCampaign> findByStatutOrderByDateDebutDescWithTemplates(EvaluationCampaignStatus statut);
    
    @Query("SELECT c FROM EvaluationCampaign c LEFT JOIN FETCH c.templateGeneral LEFT JOIN FETCH c.templateTechnique LEFT JOIN FETCH c.templateCompetence ORDER BY c.dateDebut DESC")
    List<EvaluationCampaign> findAllWithTemplates();
}
