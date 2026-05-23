package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.SkillLevel;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.*;
import com.hr.evaluation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationWorkflowService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationCampaignRepository campaignRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final SkillAnswerRepository skillAnswerRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final TechnicalQuestionRepository technicalQuestionRepository;
    private final TechnicalTemplateService technicalTemplateService;

    public EvaluationWorkflowService(
            EvaluationRepository evaluationRepository,
            EvaluationCampaignRepository campaignRepository,
            EvaluationAnswerRepository answerRepository,
            SkillAnswerRepository skillAnswerRepository,
            EvaluationQuestionRepository questionRepository,
            TechnicalQuestionRepository technicalQuestionRepository,
            TechnicalTemplateService technicalTemplateService) {
        this.evaluationRepository = evaluationRepository;
        this.campaignRepository = campaignRepository;
        this.answerRepository = answerRepository;
        this.skillAnswerRepository = skillAnswerRepository;
        this.questionRepository = questionRepository;
        this.technicalQuestionRepository = technicalQuestionRepository;
        this.technicalTemplateService = technicalTemplateService;
    }

    @Transactional
    public Evaluation creerEvaluationPourCollaborateur(
            UUID campaignId,
            UUID collaborateurId,
            UUID superieurId) {

        EvaluationCampaign campaign = chargerCampaign(campaignId);

        // Verify campaign is active
        if (campaign.getStatut() != com.hr.evaluation.domain.EvaluationCampaignStatus.ACTIVE) {
            throw new IllegalStateException("La campagne n'est pas active");
        }

        // Check if evaluation already exists
        evaluationRepository.findByCampaignIdAndCollaborateurIdentifiant(campaignId, collaborateurId)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Une évaluation existe déjà pour ce collaborateur dans cette campagne");
                });

        Evaluation evaluation = new Evaluation();
        evaluation.setCampaign(campaign);
        evaluation.setCollaborateurIdentifiant(collaborateurId);
        evaluation.setSuperieurIdentifiant(superieurId);
        evaluation.setEtapeActuelle(EvaluationStep.EVALUATION_GENERALE);
        evaluation.setStatut(StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE);

        return evaluationRepository.save(evaluation);
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
    }

    @Transactional
    public void passerAEtapeTechnique(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);
        EvaluationCampaign campaign = evaluation.getCampaign();

        // Get the general template from the campaign
        EvaluationTemplate templateGeneral = campaign.getTemplateGeneral();
        if (templateGeneral == null) {
            throw new IllegalStateException("La campagne n'a pas de template général configuré");
        }

        // Verify all general questions are answered by collaborator
        List<EvaluationQuestion> questions = questionRepository
                .findByTemplateIdAndActifTrueOrderByOrdreAsc(templateGeneral.getId());

        long unansweredCount = questions.stream()
                .filter(q -> q.isObligatoire())
                .filter(q -> answerRepository.findByEvaluationIdAndQuestionId(evaluationId, q.getId()).isEmpty())
                .count();

        if (unansweredCount > 0) {
            throw new IllegalStateException(unansweredCount + " questions obligatoires sans réponse");
        }

        evaluation.setEtapeActuelle(EvaluationStep.EVALUATION_TECHNIQUE);
        evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation validerParCollaborateur(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);

        evaluation.setValidationCollaborateurLe(Instant.now());
        actualiserStatut(evaluation);

        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation validerParManager(UUID evaluationId) {
        Evaluation evaluation = chargerEvaluation(evaluationId);

        evaluation.setValidationSuperieurLe(Instant.now());
        actualiserStatut(evaluation);

        return evaluationRepository.save(evaluation);
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listerEvaluationsCollaborateur(UUID collaborateurId) {
        return evaluationRepository.findByCollaborateurIdentifiantOrderByCreeLeDesc(collaborateurId);
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listerEvaluationsManager(UUID managerId) {
        return evaluationRepository.findBySuperieurIdentifiantOrderByCreeLeDesc(managerId);
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
        // Add logic to verify current user is the collaborator
    }

    private void verifyManagerCanAnswer(Evaluation evaluation) {
        // Add logic to verify current user is the manager
    }

    private Evaluation chargerEvaluation(UUID id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable: " + id));
    }

    private EvaluationCampaign chargerCampaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagne introuvable: " + id));
    }
}
