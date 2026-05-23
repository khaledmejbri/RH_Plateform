package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
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

        if (!moisDebut.equals(6) && !moisDebut.equals(12)) {
            throw new IllegalArgumentException(
                    "Les campagnes d'evaluation ne peuvent commencer qu'en juin (6) ou decembre (12).");
        }

        if (!moisFin.equals(6) && !moisFin.equals(12)) {
            throw new IllegalArgumentException(
                    "Les campagnes d'evaluation ne peuvent se terminer qu'en juin (6) ou decembre (12).");
        }

        List<EvaluationCampaignStatus> activeStatuses = List.of(
                EvaluationCampaignStatus.PLANIFIEE,
                EvaluationCampaignStatus.ACTIVE);

        campaignRepository.findFirstByTypeAndAnneeAndStatutIn(type, annee, activeStatuses)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Une campagne " + type + " existe deja pour l'annee " + annee);
                });

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
                    "Seule une campagne planifiee peut etre activee. Statut actuel: " + campaign.getStatut());
        }

        campaign.setStatut(EvaluationCampaignStatus.ACTIVE);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public EvaluationCampaign terminerCampagne(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);

        if (campaign.getStatut() != EvaluationCampaignStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Seule une campagne active peut etre terminee. Statut actuel: " + campaign.getStatut());
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
                    "Les templates ne peuvent etre assignes qu'a une campagne planifiee");
        }

        if (templateGeneralId != null) {
            EvaluationTemplate templateGeneral = templateRepository.findById(templateGeneralId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Template general introuvable: " + templateGeneralId));

            if (!templateGeneral.isActif() || templateGeneral.getType() != TemplateType.GENERIC) {
                throw new IllegalStateException("Le template general doit etre actif et de type GENERIC");
            }

            campaign.setTemplateGeneral(templateGeneral);
        }

        if (templateTechniqueId != null) {
            technicalTemplateRepository.findById(templateTechniqueId)
                    .ifPresentOrElse(templateTechnique -> {
                        if (!templateTechnique.isActif()) {
                            throw new IllegalStateException("Le template technique doit etre actif");
                        }
                        campaign.setTemplateTechnique(templateTechnique);
                        campaign.setTemplateCompetence(null);
                    }, () -> {
                        EvaluationTemplate templateCompetence = templateRepository.findById(templateTechniqueId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Template de competences introuvable: " + templateTechniqueId));
                        if (!templateCompetence.isActif() || templateCompetence.getType() != TemplateType.TECHNICAL) {
                            throw new IllegalStateException("Le template de competences doit etre actif et de type TECHNICAL");
                        }
                        campaign.setTemplateCompetence(templateCompetence);
                        campaign.setTemplateTechnique(null);
                    });
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
            return campaignRepository.findByStatutOrderByDateDebutDescWithTemplates(statut);
        }
        return campaignRepository.findAllWithTemplates();
    }

    @Transactional(readOnly = true)
    public boolean estPeriodeEvaluationActive() {
        Instant now = Instant.now();
        List<EvaluationCampaign> activeCampaigns = campaignRepository
                .findByDateDebutBeforeAndDateFinAfterAndStatut(
                        now,
                        now,
                        EvaluationCampaignStatus.ACTIVE);
        return !activeCampaigns.isEmpty();
    }

    private EvaluationCampaign chargerCampagne(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne d'evaluation introuvable: " + id));
    }
}
