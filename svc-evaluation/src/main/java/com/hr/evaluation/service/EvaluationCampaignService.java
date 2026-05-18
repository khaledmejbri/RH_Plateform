package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.entity.TechnicalTemplate;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.repository.TechnicalTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EvaluationCampaignService {

    private final EvaluationCampaignRepository campaignRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final TechnicalTemplateRepository technicalTemplateRepository;

    public EvaluationCampaignService(
            EvaluationCampaignRepository campaignRepository,
            EvaluationTemplateRepository templateRepository,
            TechnicalTemplateRepository technicalTemplateRepository) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.technicalTemplateRepository = technicalTemplateRepository;
    }

    @Transactional
    public EvaluationCampaign creerCampagne(
            String nom,
            String description,
            EvaluationCampaignType type,
            Integer annee,
            Integer moisDebut,
            Integer moisFin,
            UUID creePar) {

        // Validate that evaluations only happen in June (6) or December (12)
        if (!moisDebut.equals(6) && !moisDebut.equals(12)) {
            throw new IllegalArgumentException(
                    "Les campagnes d'évaluation ne peuvent commencer qu'en juin (6) ou décembre (12).");
        }

        if (!moisFin.equals(6) && !moisFin.equals(12)) {
            throw new IllegalArgumentException(
                    "Les campagnes d'évaluation ne peuvent se terminer qu'en juin (6) ou décembre (12).");
        }

        // Check if a campaign already exists for this type and year
        List<EvaluationCampaignStatus> activeStatuses = List.of(
                EvaluationCampaignStatus.PLANIFIEE,
                EvaluationCampaignStatus.ACTIVE);

        campaignRepository.findFirstByTypeAndAnneeAndStatutIn(type, annee, activeStatuses)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Une campagne " + type + " existe déjà pour l'année " + annee);
                });

        // Calculate start and end dates based on month
        ZonedDateTime startDate = ZonedDateTime.of(annee, moisDebut, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime endDate = ZonedDateTime.of(annee, moisFin, 
                moisFin == 6 ? 30 : 31, 23, 59, 59, 0, ZoneId.of("UTC"));

        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setNom(nom);
        campaign.setDescription(description);
        campaign.setType(type);
        campaign.setAnnee(annee);
        campaign.setMoisDebut(moisDebut);
        campaign.setMoisFin(moisFin);
        campaign.setDateDebut(startDate.toInstant());
        campaign.setDateFin(endDate.toInstant());
        campaign.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        campaign.setCreePar(creePar);

        return campaignRepository.save(campaign);
    }

    @Transactional
    public EvaluationCampaign activerCampagne(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);
        
        if (campaign.getStatut() != EvaluationCampaignStatus.PLANIFIEE) {
            throw new IllegalStateException(
                    "Seule une campagne planifiée peut être activée. Statut actuel: " + campaign.getStatut());
        }

        // Verify current date is within campaign window
        Instant now = Instant.now();
        if (now.isBefore(campaign.getDateDebut()) || now.isAfter(campaign.getDateFin())) {
            throw new IllegalStateException(
                    "La campagne ne peut être activée que pendant sa période valide (" +
                    campaign.getDateDebut() + " à " + campaign.getDateFin() + ")");
        }

        campaign.setStatut(EvaluationCampaignStatus.ACTIVE);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public EvaluationCampaign terminerCampagne(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);
        
        if (campaign.getStatut() != EvaluationCampaignStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Seule une campagne active peut être terminée. Statut actuel: " + campaign.getStatut());
        }

        campaign.setStatut(EvaluationCampaignStatus.TERMINEE);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public EvaluationCampaign assignerTemplates(
            UUID campaignId,
            UUID templateGeneralId,
            UUID templateTechniqueId) {

        EvaluationCampaign campaign = chargerCampagne(campaignId);

        if (campaign.getStatut() != EvaluationCampaignStatus.PLANIFIEE) {
            throw new IllegalStateException(
                    "Les templates ne peuvent être assignés qu'à une campagne planifiée");
        }

        // Load and validate general template
        if (templateGeneralId != null) {
            EvaluationTemplate templateGeneral = templateRepository.findById(templateGeneralId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Template général introuvable: " + templateGeneralId));
            
            if (!templateGeneral.isActif()) {
                throw new IllegalStateException("Le template général doit être actif");
            }
            
            campaign.setTemplateGeneral(templateGeneral);
        }

        // Load and validate technical template
        if (templateTechniqueId != null) {
            TechnicalTemplate templateTechnique = technicalTemplateRepository.findById(templateTechniqueId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Template technique introuvable: " + templateTechniqueId));
            
            if (!templateTechnique.isActif()) {
                throw new IllegalStateException("Le template technique doit être actif");
            }
            
            campaign.setTemplateTechnique(templateTechnique);
        }

        return campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public EvaluationCampaign obtenirCampagneActive(EvaluationCampaignType type, Integer annee) {
        return campaignRepository.findFirstByTypeAndAnneeAndStatutIn(
                type, 
                annee, 
                List.of(EvaluationCampaignStatus.ACTIVE))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EvaluationCampaign> listerCampagnes(EvaluationCampaignStatus statut) {
        if (statut != null) {
            return campaignRepository.findByStatutOrderByDateDebutDesc(statut);
        }
        return campaignRepository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean estPeriodeEvaluationActive() {
        Instant now = Instant.now();
        List<EvaluationCampaign> activeCampaigns = campaignRepository
                .findByDateDebutBeforeAndDateFinAfterAndStatut(
                        now, 
                        now, 
                        EvaluationCampaignStatus.ACTIVE);
        return activeCampaigns.isEmpty();
    }

    private EvaluationCampaign chargerCampagne(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne d'évaluation introuvable: " + id));
    }
}
