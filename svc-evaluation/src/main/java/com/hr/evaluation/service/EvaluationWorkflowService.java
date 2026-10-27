package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.NiveauSenioriteCodes;
import com.hr.evaluation.domain.SkillLevel;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.*;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EvaluationWorkflowService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationCampaignRepository campaignRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final TechnicalQuestionRepository technicalQuestionRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationScoringService scoringService;
    private final EvaluationEventPublisher eventPublisher;

    public EvaluationWorkflowService(
            EvaluationRepository evaluationRepository,
            EvaluationCampaignRepository campaignRepository,
            EvaluationAnswerRepository answerRepository,
            SkillAnswerRepository skillAnswerRepository,
            EvaluationQuestionRepository questionRepository,
            TechnicalQuestionRepository technicalQuestionRepository,
            EvaluationTemplateRepository templateRepository,
            EvaluationScoringService scoringService,
            EvaluationEventPublisher eventPublisher) {
        this.evaluationRepository = evaluationRepository;
        this.campaignRepository = campaignRepository;
        this.answerRepository = answerRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.questionRepository = questionRepository;
        this.technicalQuestionRepository = technicalQuestionRepository;
        this.templateRepository = templateRepository;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Evaluation creerEvaluationPourCollaborateur(
            UUID campaignId,
            UUID collaborateurId,
            UUID superieurId) {
        return creerEvaluationPourCollaborateur(campaignId, collaborateurId, superieurId, null, null);
    }

    /**
     * Legacy — matching via role_metier déprécié ; préférer {@link #creerEvaluationDepuisSnapshot}.
     */
    @Transactional
    public Evaluation creerEvaluationPourCollaborateur(
            UUID campaignId,
            UUID collaborateurId,
            UUID superieurId,
            String niveauSeniorite,
            String roleMetier) {

        EvaluationCampaign campaign = chargerCampaign(campaignId);
        return creerEvaluationDepuisSnapshot(
                campaign, collaborateurId, superieurId, roleMetier, niveauSeniorite);
    }

    /**
     * Création avec snapshot figé famille × niveau (E4). Ne lit plus les params client.
     */
    @Transactional
    public Evaluation creerEvaluationDepuisSnapshot(
            EvaluationCampaign campaign,
            UUID collaborateurId,
            UUID superieurId,
            String familleMetierCode,
            String niveauSeniorite) {

        if (campaign.getStatut() != EvaluationCampaignStatus.ACTIVE) {
            throw new IllegalStateException("La campagne n'est pas active");
        }

        evaluationRepository.findByCampaignIdAndCollaborateurIdentifiant(campaign.getId(), collaborateurId)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Une évaluation existe déjà pour ce collaborateur dans cette campagne");
                });

        String famille = trimToNull(familleMetierCode);
        String niveau = NiveauSenioriteCodes.normalizeOrNull(niveauSeniorite);
        boolean profilIncomplet = famille == null;

        Evaluation evaluation = new Evaluation();
        evaluation.setCampaign(campaign);
        evaluation.setCollaborateurIdentifiant(collaborateurId);
        evaluation.setSuperieurIdentifiant(superieurId);
        evaluation.setEtapeActuelle(EvaluationStep.EVALUATION_GENERALE);
        evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);
        evaluation.setFamilleMetierCode(famille);
        evaluation.setNiveauSeniorite(niveau);
        evaluation.setProfilMetierIncomplet(profilIncomplet);
        // Legacy lecture : ne plus écrire role_metier comme clé matching
        evaluation.setRoleMetier(null);
        evaluation.setTemplateCompetenceAssigne(
                resoudreTemplateCompetence(famille, niveau, campaign));

        return evaluationRepository.save(evaluation);
    }

    /**
     * Ensure mobile : ne crée plus depuis params client (E4-R11).
     * Retourne l'évaluation existante sur campagne ACTIVE si présente ; sinon null.
     */
    @Transactional(readOnly = true)
    public Evaluation assurerEvaluationPourCollaborateur(
            UUID collaborateurId,
            UUID superieurId,
            String niveauSenioriteIgnore,
            String roleMetierIgnore) {

        List<EvaluationCampaign> actives = campaignRepository
                .findByStatutOrderByDateDebutDescWithTemplates(EvaluationCampaignStatus.ACTIVE);
        if (actives.isEmpty()) {
            throw new IllegalStateException(
                    "Aucune campagne ACTIVE. Créez et activez une campagne côté admin (avec template général).");
        }

        EvaluationCampaign campaign = actives.get(0);
        return evaluationRepository
                .findByCampaignIdAndCollaborateurIdentifiant(campaign.getId(), collaborateurId)
                .orElse(null);
    }

    /**
     * Résolution TECHNICAL (E4-R05) :
     * 1) famille+niveau 2) famille seule 3) template compétence campagne 4) null
     * {@code profil_acces} ignoré.
     */
    public EvaluationTemplate resoudreTemplateCompetence(
            String familleMetierCode,
            String niveauSeniorite,
            EvaluationCampaign campaign) {

        String famille = trimToNull(familleMetierCode);
        String niveau = NiveauSenioriteCodes.normalizeOrNull(niveauSeniorite);

        if (famille != null && niveau != null) {
            List<EvaluationTemplate> exact = templateRepository.findPublishedByTypeFamilleAndNiveau(
                    TemplateType.TECHNICAL, TemplateStatus.PUBLISHED, famille, niveau);
            if (!exact.isEmpty()) {
                return exact.get(0);
            }
        }
        if (famille != null) {
            List<EvaluationTemplate> familleSeule = templateRepository.findPublishedByTypeAndFamilleSansNiveau(
                    TemplateType.TECHNICAL, TemplateStatus.PUBLISHED, famille);
            if (!familleSeule.isEmpty()) {
                return familleSeule.get(0);
            }
            // Fallback : templates famille avec n'importe quel niveau si aucun « famille seule »
            List<EvaluationTemplate> familleAny = templateRepository.findPublishedByTypeAndFamille(
                    TemplateType.TECHNICAL, TemplateStatus.PUBLISHED, famille);
            // Prefer those with null niveau already handled above; here skip if we want strict order
            // Spec step 2 is famille seule only — do not pick random niveau
        }
        if (campaign != null && campaign.getTemplateCompetence() != null) {
            return campaign.getTemplateCompetence();
        }
        return null;
    }

    /** @deprecated matching legacy role+niveau — délégué à famille. */
    @Deprecated
    public EvaluationTemplate resoudreTemplateCompetenceLegacy(
            String niveauSeniorite,
            String roleMetier,
            EvaluationCampaign campaign) {
        return resoudreTemplateCompetence(roleMetier, niveauSeniorite, campaign);
    }

    @Transactional
    public void repondreQuestionCollaborateur(
            UUID evaluationId,
            UUID questionId,
            String reponse,
            Integer note) {

        Evaluation evaluation = chargerEvaluation(evaluationId);
        verifyCollaboratorCanAnswer(evaluation);

        EvaluationQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question introuvable: " + questionId));

        EvaluationAnswer answer = answerRepository
                .findByEvaluationIdAndQuestionId(evaluationId, questionId)
                .orElseGet(() -> {
                    EvaluationAnswer newAnswer = new EvaluationAnswer();
                    newAnswer.setEvaluation(evaluation);
                    newAnswer.setQuestion(question);
                    return newAnswer;
                });

        answer.setReponseCollaborateur(reponse);
        answer.setNoteAttribuee(note);
        answer.setNoteCollaborateur(note);
        answer.setReponduParCollaborateurLe(Instant.now());

        answerRepository.save(answer);
        recalculerScore(evaluation);
    }

    @Transactional
    public void repondreQuestionManager(
            UUID evaluationId,
            UUID questionId,
            String reponseManager,
            String commentaireManager,
            Integer note) {

        Evaluation evaluation = chargerEvaluation(evaluationId);
        verifyManagerCanAnswer(evaluation);

        EvaluationQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question introuvable: " + questionId));

        EvaluationAnswer answer = answerRepository
                .findByEvaluationIdAndQuestionId(evaluationId, questionId)
                .orElseThrow(() -> new IllegalStateException("Le collaborateur doit d'abord répondre à cette question"));

        answer.setReponseManager(reponseManager);
        answer.setCommentaireManager(commentaireManager);
        answer.setNoteManager(note);
        answer.setReponduParManagerLe(Instant.now());

        answerRepository.save(answer);
        recalculerScore(evaluation);
    }

    @Transactional
    public void evaluerCompetenceTechnique(
            UUID evaluationId,
            UUID questionTechniqueId,
            SkillLevel niveauAutoEvaluation,
            String commentaireCollaborateur) {

        Evaluation evaluation = chargerEvaluation(evaluationId);
        verifyCollaboratorCanAnswer(evaluation);

        TechnicalQuestion question = technicalQuestionRepository.findById(questionTechniqueId)
                .orElseThrow(() -> new IllegalArgumentException("Question technique introuvable: " + questionTechniqueId));

        SkillAnswer answer = skillAnswerRepository
                .findByEvaluationIdAndQuestionTechniqueId(evaluationId, questionTechniqueId)
                .orElseGet(() -> {
                    SkillAnswer newAnswer = new SkillAnswer();
                    newAnswer.setEvaluation(evaluation);
                    newAnswer.setQuestionTechnique(question);
                    return newAnswer;
                });

        answer.setNiveauAutoEvaluation(niveauAutoEvaluation);
        answer.setCommentaireCollaborateur(commentaireCollaborateur);
        answer.setEvalueParCollaborateurLe(Instant.now());

        skillAnswerRepository.save(answer);
        recalculerScore(evaluation);
    }

    @Transactional
    public void evaluerCompetenceTechniqueManager(
            UUID evaluationId,
            UUID questionTechniqueId,
            SkillLevel niveauManager,
            String commentaireManager) {

        Evaluation evaluation = chargerEvaluation(evaluationId);
        verifyManagerCanAnswer(evaluation);

        SkillAnswer answer = skillAnswerRepository
                .findByEvaluationIdAndQuestionTechniqueId(evaluationId, questionTechniqueId)
                .orElseThrow(() -> new IllegalStateException("Le collaborateur doit d'abord s'auto-évaluer"));

        answer.setNiveauManager(niveauManager);
        answer.setCommentaireManager(commentaireManager);
        answer.setEvalueParManagerLe(Instant.now());

        skillAnswerRepository.save(answer);
        recalculerScore(evaluation);
    }

    @Transactional
    public void passerAEtapeTechnique(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);
        EvaluationCampaign campaign = evaluation.getCampaign();

        EvaluationTemplate templateGeneral = campaign.getTemplateGeneral();
        if (templateGeneral == null) {
            throw new IllegalStateException("La campagne n'a pas de template général configuré");
        }

        List<EvaluationQuestion> questions = questionRepository
                .findByTemplateIdAndActifTrueOrderByOrdreAsc(templateGeneral.getId());

        long unansweredCount = questions.stream()
                .filter(EvaluationQuestion::isObligatoire)
                .filter(q -> answerRepository.findByEvaluationIdAndQuestionId(evaluationId, q.getId()).isEmpty())
                .count();

        if (unansweredCount > 0) {
            throw new IllegalStateException(unansweredCount + " questions obligatoires sans réponse");
        }

        // Snapshot immuable — ne pas recalculer depuis fiche ; garder template déjà assigné
        evaluation.setEtapeActuelle(EvaluationStep.EVALUATION_TECHNIQUE);
        evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation validerParCollaborateur(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);
        boolean dejaValidee = evaluation.getValidationCollaborateurLe() != null;
        evaluation.setValidationCollaborateurLe(Instant.now());
        recalculerScore(evaluation);
        actualiserStatut(evaluation);
        Evaluation saved = evaluationRepository.save(evaluation);
        if (!dejaValidee && saved.getValidationSuperieurLe() == null) {
            eventPublisher.publierValidationCollaborateur(saved);
        }
        return saved;
    }

    @Transactional
    public Evaluation validerParManager(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);
        evaluation.setValidationSuperieurLe(Instant.now());
        recalculerScore(evaluation);
        actualiserStatut(evaluation);
        return evaluationRepository.save(evaluation);
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listerEvaluationsCollaborateur(UUID collaborateurId) {
        return evaluationRepository.findByCollaborateurIdentifiantOrderByCreeLeDescWithCampaign(collaborateurId);
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listerEvaluationsManager(UUID managerId) {
        return evaluationRepository.findBySuperieurIdentifiantOrderByCreeLeDescWithCampaign(managerId);
    }

    @Transactional(readOnly = true)
    public List<EvaluationAnswer> obtenirReponsesEvaluation(UUID evaluationId) {
        return answerRepository.findByEvaluationIdOrderByQuestionOrdreAsc(evaluationId);
    }

    @Transactional(readOnly = true)
    public List<SkillAnswer> obtenirReponsesTechniques(UUID evaluationId) {
        return skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId);
    }

    @Transactional(readOnly = true)
    public Evaluation obtenirEvaluation(UUID id) {
        return chargerEvaluation(id);
    }

    private void recalculerScore(Evaluation evaluation) {
        var analytics = scoringService.analyser(
                obtenirReponsesEvaluation(evaluation.getId()),
                obtenirReponsesTechniques(evaluation.getId()));
        evaluation.setScoreSur20(scoringService.toScoreSur20(analytics.finalScore()));
        evaluationRepository.save(evaluation);
    }

    private void actualiserStatut(Evaluation evaluation) {
        boolean collaborateur = evaluation.getValidationCollaborateurLe() != null;
        boolean superieur = evaluation.getValidationSuperieurLe() != null;

        if (collaborateur && superieur) {
            evaluation.setStatut(StatutEvaluationRh.VALIDEE);
        } else if (collaborateur) {
            evaluation.setStatut(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
        } else if (superieur) {
            evaluation.setStatut(StatutEvaluationRh.VALIDEE_SUPERIEUR);
        }
    }

    private void verifyCollaboratorCanAnswer(Evaluation evaluation) {
        // Ownership enforced at controller layer for mobile
    }

    private void verifyManagerCanAnswer(Evaluation evaluation) {
        // Ownership enforced at controller layer for mobile
    }

    private Evaluation chargerEvaluation(UUID id) {
        return evaluationRepository.findByIdWithCampaign(id)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable: " + id));
    }

    private EvaluationCampaign chargerCampaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagne introuvable: " + id));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
