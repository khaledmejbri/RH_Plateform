package com.hr.evaluation.service;

import com.hr.evaluation.client.CollaborateurEvaluationSnapshot;
import com.hr.evaluation.client.ReferentielCollaborateurClient;
import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CampaignActivationResult;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.repository.TechnicalTemplateRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EvaluationCampaignService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationCampaignService.class);

    private final EvaluationCampaignRepository campaignRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final TechnicalTemplateRepository technicalTemplateRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationWorkflowService workflowService;
    private final ReferentielCollaborateurClient referentielClient;
    private final EvaluationEventPublisher eventPublisher;
    private final EvaluationParametresService parametresService;

    public EvaluationCampaignService(
            EvaluationCampaignRepository campaignRepository,
            EvaluationTemplateRepository templateRepository,
            TechnicalTemplateRepository technicalTemplateRepository,
            EvaluationRepository evaluationRepository,
            EvaluationWorkflowService workflowService,
            ReferentielCollaborateurClient referentielClient,
            EvaluationEventPublisher eventPublisher,
            EvaluationParametresService parametresService) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.technicalTemplateRepository = technicalTemplateRepository;
        this.evaluationRepository = evaluationRepository;
        this.workflowService = workflowService;
        this.referentielClient = referentielClient;
        this.eventPublisher = eventPublisher;
        this.parametresService = parametresService;
    }

    /**
     * Création d'une campagne (D-E02, SCRUM-41) : mois de lancement libre, jour de commencement (vide → le 20),
     * date de fin = date de commencement + durée (paramètre société si non précisée).
     * Une seule campagne planifiée ou active du même type par année, semestre ou trimestre.
     */
    @Transactional
    public EvaluationCampaign creerCampagne(
            String nom,
            String description,
            EvaluationCampaignType type,
            Integer annee,
            Integer moisDebut,
            Integer jourDebut,
            Integer dureeJours,
            UUID creePar) {

        if (type == null) {
            throw EvaluationMetierException.unprocessable("TYPE_CAMPAGNE_INVALIDE",
                    "Type de campagne invalide");
        }
        int duree = dureeJours != null ? dureeJours : parametresService.dureeCampagneJours();
        CampagneCalendrier.Periode periode = CampagneCalendrier.calculer(annee, moisDebut, jourDebut, duree);

        int indice = CampagneCalendrier.indicePeriode(type, moisDebut);
        boolean dejaPrise = campaignRepository.findByTypeAndAnneeAndStatutIn(type, annee,
                        List.of(EvaluationCampaignStatus.PLANIFIEE, EvaluationCampaignStatus.ACTIVE)).stream()
                .anyMatch(c -> c.getMoisDebut() != null
                        && CampagneCalendrier.indicePeriode(type, c.getMoisDebut()) == indice);
        if (dejaPrise) {
            throw EvaluationMetierException.conflict("CAMPAGNE_DEJA_EXISTANTE",
                    "Une campagne " + type.name().toLowerCase(Locale.ROOT) + " existe déjà pour "
                            + CampagneCalendrier.libellePeriode(type, moisDebut, annee) + ".");
        }

        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setNom(nom);
        campaign.setDescription(description);
        campaign.setType(type);
        campaign.setAnnee(annee);
        campaign.setMoisDebut(moisDebut);
        campaign.setMoisFin(periode.dernierJour().getMonthValue());
        campaign.setJourDebut(periode.jourDebut());
        campaign.setDureeJours(duree);
        campaign.setDateDebut(periode.dateDebut());
        campaign.setDateFin(periode.dateFin());
        campaign.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        campaign.setCreePar(creePar);

        return campaignRepository.save(campaign);
    }

    /** Lancement automatique bloqué : les RH ont été prévenus (SCRUM-42). */
    @Transactional
    public void marquerAlerteLancement(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);
        campaign.setAlerteLancementEnvoyee(true);
        campaignRepository.save(campaign);
    }

    /** Une campagne ne peut être lancée qu'avec un template général publié et actif. */
    public static boolean templateGeneralPret(EvaluationCampaign campaign) {
        EvaluationTemplate generic = campaign.getTemplateGeneral();
        return generic != null
                && generic.getType() == TemplateType.GENERIC
                && generic.getStatut() == TemplateStatus.PUBLISHED
                && generic.isActif();
    }

    /**
     * Active la campagne et crée les évaluations pour tous les ACTIFS (E4) + notifs (E5).
     */
    @Transactional
    public CampaignActivationResult activerCampagne(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);

        if (campaign.getStatut() != EvaluationCampaignStatus.PLANIFIEE) {
            throw EvaluationMetierException.unprocessable("CAMPAGNE_STATUT_INVALIDE",
                    "Seule une campagne planifiee peut etre activee. Statut actuel: " + campaign.getStatut());
        }

        if (!templateGeneralPret(campaign)) {
            throw EvaluationMetierException.unprocessable("CAMPAGNE_GENERIC_MANQUANT",
                    "Un template GENERIC publié et actif doit être assigné avant activation");
        }

        campaign.setStatut(EvaluationCampaignStatus.ACTIVE);
        campaignRepository.save(campaign);

        List<CollaborateurEvaluationSnapshot> population = referentielClient.listerActifsPourEvaluation();
        int creees = 0;
        int ignoresExistantes = 0;
        int ignoresManager = 0;
        int profilsIncomplets = 0;

        for (CollaborateurEvaluationSnapshot collab : population) {
            if (evaluationRepository.findByCampaignIdAndCollaborateurIdentifiant(campaignId, collab.identifiant())
                    .isPresent()) {
                ignoresExistantes++;
                continue;
            }
            if (collab.superieurIdentifiant() == null) {
                ignoresManager++;
                log.warn("Activation campagne {}: collab {} ignoré (manager_manquant)",
                        campaignId, collab.identifiant());
                continue;
            }
            try {
                Evaluation evaluation = workflowService.creerEvaluationDepuisSnapshot(
                        campaign, collab.identifiant(), collab.superieurIdentifiant(),
                        collab.familleMetierCode(), collab.niveauSeniorite());
                appliquerSnapshotIdentite(evaluation, collab);
                if (evaluation.isProfilMetierIncomplet()) {
                    profilsIncomplets++;
                }
                creees++;
                eventPublisher.publierEvaluationCampagneOuverte(evaluation, campaign.getNom());
            } catch (IllegalStateException dup) {
                ignoresExistantes++;
            }
        }

        return new CampaignActivationResult(
                campaign.getId(),
                campaign.getStatut().name(),
                creees,
                ignoresExistantes,
                ignoresManager,
                profilsIncomplets);
    }

    private void appliquerSnapshotIdentite(Evaluation evaluation, CollaborateurEvaluationSnapshot collab) {
        evaluation.setCollaborateurCompteId(collab.compteUtilisateurId());
        evaluation.setCollaborateurNom(collab.nomComplet());
        evaluation.setCollaborateurPoste(collab.posteLibelle());
        evaluation.setSuperieurCompteId(collab.superieurCompteUtilisateurId());
        evaluation.setSuperieurNom(collab.superieurNomComplet());
        evaluationRepository.save(evaluation);
    }

    @Transactional
    public EvaluationCampaign terminerCampagne(UUID campaignId) {
        EvaluationCampaign campaign = chargerCampagne(campaignId);

        if (campaign.getStatut() != EvaluationCampaignStatus.ACTIVE) {
            throw EvaluationMetierException.unprocessable("CAMPAGNE_STATUT_INVALIDE",
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

        if (campaign.getStatut() == EvaluationCampaignStatus.ACTIVE) {
            throw EvaluationMetierException.conflict("CAMPAGNE_ACTIVE_TEMPLATES_FIGES",
                    "Impossible de modifier les templates d’une campagne active : les évaluations déjà créées conservent leur snapshot.");
        }
        if (campaign.getStatut() != EvaluationCampaignStatus.PLANIFIEE) {
            throw EvaluationMetierException.unprocessable("CAMPAGNE_STATUT_INVALIDE",
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
        var now = java.time.Instant.now();
        List<EvaluationCampaign> activeCampaigns = campaignRepository
                .findByDateDebutBeforeAndDateFinAfterAndStatut(
                        now,
                        now,
                        EvaluationCampaignStatus.ACTIVE);
        return !activeCampaigns.isEmpty();
    }

    public static EvaluationCampaignType parseTypeStrict(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw EvaluationMetierException.unprocessable("TYPE_CAMPAGNE_INVALIDE",
                    "Type de campagne manquant");
        }
        String normalized = typeStr.trim().toUpperCase(Locale.ROOT);
        try {
            return EvaluationCampaignType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw EvaluationMetierException.unprocessable("TYPE_CAMPAGNE_INVALIDE",
                    "Type de campagne invalide: " + typeStr);
        }
    }

    private EvaluationCampaign chargerCampagne(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne d'evaluation introuvable: " + id));
    }
}
