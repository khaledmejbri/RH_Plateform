package com.hr.evaluation.service;

import com.hr.evaluation.client.ReferentielParametresClient;
import com.hr.evaluation.client.ReferentielParametresClient.Reference;
import com.hr.evaluation.domain.NiveauSenioriteCodes;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CreateTemplateRequest;
import com.hr.evaluation.dto.CreateQuestionRequest;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TemplateService {

    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final ReferentielParametresClient referentielParametres;
    private final ParametresEvaluation parametres;

    public TemplateService(EvaluationTemplateRepository templateRepository,
                          EvaluationQuestionRepository questionRepository,
                          ReferentielParametresClient referentielParametres,
                          ParametresEvaluation parametres) {
        this.templateRepository = templateRepository;
        this.questionRepository = questionRepository;
        this.referentielParametres = referentielParametres;
        this.parametres = parametres;
    }

    public EvaluationTemplate creerTemplate(CreateTemplateRequest request, UUID userId) {
        TemplateType type;
        try {
            type = TemplateType.valueOf(request.getType().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw EvaluationMetierException.unprocessable("TYPE_TEMPLATE_INVALIDE",
                    "Type de template invalide: " + request.getType());
        }

        EvaluationTemplate template = new EvaluationTemplate();
        template.setNom(request.getNom());
        template.setDescription(request.getDescription());
        template.setType(type);
        template.setCreePar(userId != null ? userId : request.getCreePar());

        if (type == TemplateType.TECHNICAL) {
            String famille = request.resolveFamilleMetierCode();
            template.setFamilleMetierCode(famille);
            // Sync legacy role pour lectures / migrations
            template.setRole(famille);
            String niveau = NiveauSenioriteCodes.normalizeOrNull(request.getNiveauSeniorite());
            if (request.getNiveauSeniorite() != null && !request.getNiveauSeniorite().isBlank() && niveau == null) {
                throw EvaluationMetierException.badRequest("NIVEAU_SENIORITE_INVALIDE",
                        "Niveau de séniorité invalide: " + request.getNiveauSeniorite());
            }
            validerCiblage(famille, niveau);
            template.setNiveauSeniorite(niveau);
            template.setDomaine(request.getDomaine());
        }

        template = templateRepository.save(template);

        final EvaluationTemplate savedTemplate = template;
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            List<EvaluationQuestion> questions = request.getQuestions().stream()
                .map(qDto -> convertToQuestion(qDto, savedTemplate))
                .collect(Collectors.toList());

            questionRepository.saveAll(questions);
            template.setQuestions(questions);
        }

        return template;
    }

    public EvaluationTemplate publierTemplate(UUID templateId, UUID userId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("Only draft templates can be published");
        }

        if (template.getType() == TemplateType.TECHNICAL) {
            String famille = template.resolveFamilleMetierCode();
            if (famille == null || famille.isBlank()) {
                throw EvaluationMetierException.unprocessable("TECHNICAL_PROFIL_INCOMPLET",
                        "Un template TECHNICAL publié exige famille_metier_code");
            }
        }

        long questionsActives = template.getQuestions() == null ? 0
                : template.getQuestions().stream().filter(EvaluationQuestion::isActif).count();
        if (questionsActives < 1) {
            // recharge si questions lazy non chargées
            EvaluationTemplate withQ = templateRepository.findByIdWithQuestions(templateId).orElse(template);
            questionsActives = withQ.getQuestions() == null ? 0
                    : withQ.getQuestions().stream().filter(EvaluationQuestion::isActif).count();
            template = withQ;
        }
        if (questionsActives < 1) {
            throw EvaluationMetierException.unprocessable("TEMPLATE_SANS_QUESTION",
                    "Publication impossible : au moins une question active est requise");
        }

        template.setStatut(TemplateStatus.PUBLISHED);
        template.setPublieLe(Instant.now());
        template.setPubliePar(userId);

        return templateRepository.save(template);
    }

    public void archiverTemplate(UUID templateId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        template.setStatut(TemplateStatus.ARCHIVED);
        template.setActif(false);
        templateRepository.save(template);
    }

    public EvaluationQuestion ajouterQuestion(UUID templateId, CreateQuestionRequest request) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("Can only add questions to draft templates");
        }

        EvaluationQuestion question = convertToQuestion(request, template);
        return questionRepository.save(question);
    }

    public void supprimerQuestion(UUID templateId, UUID questionId) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("Can only delete questions from draft templates");
        }

        questionRepository.deleteById(questionId);
    }

    public void reorderQuestions(UUID templateId, List<UUID> questionIdsInOrder) {
        EvaluationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        if (template.getStatut() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("Can only reorder questions in draft templates");
        }

        List<EvaluationQuestion> questions = questionRepository.findAllById(questionIdsInOrder);

        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setOrdre(i + 1);
        }

        questionRepository.saveAll(questions);
    }

    @Transactional(readOnly = true)
    public List<EvaluationTemplate> listerTemplates(String type, String statut) {
        return listerTemplates(type, statut, null, null);
    }

    @Transactional(readOnly = true)
    public List<EvaluationTemplate> listerTemplates(
            String type, String statut, String familleMetierCode, String niveauSeniorite) {
        List<EvaluationTemplate> base;
        if (type != null && statut != null) {
            base = templateRepository.findByTypeAndStatutWithQuestions(
                TemplateType.valueOf(type),
                TemplateStatus.valueOf(statut)
            );
        } else if (type != null) {
            base = templateRepository.findByTypeWithQuestions(TemplateType.valueOf(type));
        } else if (statut != null) {
            base = templateRepository.findByStatutWithQuestions(TemplateStatus.valueOf(statut));
        } else {
            base = templateRepository.findAllByActifTrueWithQuestions();
        }

        String famille = familleMetierCode != null && !familleMetierCode.isBlank()
                ? familleMetierCode.trim() : null;
        String niveau = NiveauSenioriteCodes.normalizeOrNull(niveauSeniorite);

        return base.stream()
                .filter(t -> famille == null
                        || (t.resolveFamilleMetierCode() != null
                        && t.resolveFamilleMetierCode().equalsIgnoreCase(famille)))
                .filter(t -> niveau == null
                        || (t.getNiveauSeniorite() != null
                        && t.getNiveauSeniorite().equalsIgnoreCase(niveau)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EvaluationTemplate getTemplateWithQuestions(UUID templateId) {
        return templateRepository.findByIdWithQuestions(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));
    }

    /**
     * ADR lot 3 § 6 : la famille et le niveau ciblés sont validés (existent, actifs) et marqués « déjà utilisés » par le
     * référentiel. Inconnu : 400 ; inactif : 422 {@code VALEUR_INACTIVE} ; référentiel muet : 503.
     */
    private void validerCiblage(String famille, String niveau) {
        List<Reference> references = new ArrayList<>();
        if (famille != null && !famille.isBlank()) {
            references.add(new Reference("FAMILLE_METIER", famille.trim()));
        }
        if (niveau != null) {
            references.add(new Reference("NIVEAU_SENIORITE", niveau));
        }
        if (references.isEmpty()) {
            return;
        }
        ReferentielParametresClient.Resultat resultat = referentielParametres.utiliser(references);
        if (!resultat.inconnus().isEmpty()) {
            String code = resultat.inconnus().get(0);
            boolean estNiveau = niveau != null && niveau.equals(code);
            throw EvaluationMetierException.badRequest(estNiveau ? "NIVEAU_SENIORITE_INVALIDE" : "FAMILLE_METIER_INVALIDE",
                    (estNiveau ? "Niveau de séniorité inconnu : " : "Famille métier inconnue : ") + code);
        }
        if (!resultat.inactifs().isEmpty()) {
            throw EvaluationMetierException.unprocessable("VALEUR_INACTIVE",
                    "La famille ou le niveau choisi est désactivé (" + String.join(", ", resultat.inactifs())
                            + ") : choisissez une valeur active.");
        }
    }

    private EvaluationQuestion convertToQuestion(CreateQuestionRequest dto, EvaluationTemplate template) {
        QuestionType type = QuestionType.valueOf(dto.getTypeQuestion());
        parametres.verifierTypeQuestionActif(type);
        EvaluationQuestion question = new EvaluationQuestion();
        question.setTemplate(template);
        question.setLibelle(dto.getLibelle());
        question.setDescription(dto.getDescription());
        question.setTypeQuestion(type);
        question.setOrdre(dto.getOrdre());
        question.setObligatoire(dto.getObligatoire() != null ? dto.getObligatoire() : false);
        question.setSectionCode(dto.getSectionCode());
        question.setSectionLibelle(dto.getSectionLibelle());
        question.setPoids(dto.getPoids() != null ? dto.getPoids() : java.math.BigDecimal.ONE);
        question.setLabelsEchelle(dto.getLabelsEchelle());

        if (dto.getOptionsReponses() != null && !dto.getOptionsReponses().isEmpty()) {
            question.setOptionsReponses(dto.getOptionsReponses());
        }

        question.setValeurMinimale(dto.getValeurMinimale());
        question.setValeurMaximale(dto.getValeurMaximale());
        question.setUniteMesure(dto.getUniteMesure());
        question.setPlaceholder(dto.getPlaceholder());
        question.setRegexPattern(dto.getRegexPattern());
        question.setMinLongueur(dto.getMinLongueur());
        question.setMaxLongueur(dto.getMaxLongueur());

        return question;
    }
}
