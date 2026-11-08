package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.dto.AnalyseMobile;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.dto.EvaluationDetailMobile;
import com.hr.evaluation.dto.ReponseMobile;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.SkillAnswer;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lectures mobiles d'une évaluation (ticket 2B, D-E15). Règle unique : le <b>collaborateur évalué</b> ne voit pas la
 * notation du responsable (réponse, note, commentaire, moyenne, écart, score consolidé, appréciation) tant que
 * l'évaluation n'est pas validée ; ces champs sont alors absents du JSON, pas à zéro. Le responsable voit les deux
 * séries. L'alerte d'écart n'est jamais exposée (D-E09). Le rôle se déduit de l'identité lue dans le JWT, jamais d'un
 * en-tête. L'appelant doit avoir passé le contrôle de participant ({@code EvaluationAccessService}) avant d'appeler.
 */
@Service
public class EvaluationMobileLectureService {

	public static final String EVALUATION_INTROUVABLE = "EVALUATION_INTROUVABLE";
	public static final String ROLE_COLLABORATEUR = "COLLABORATEUR";
	public static final String ROLE_RESPONSABLE = "RESPONSABLE";

	private final EvaluationRepository evaluationRepository;
	private final EvaluationAnswerRepository answerRepository;
	private final SkillAnswerRepository skillAnswerRepository;
	private final EvaluationQuestionRepository questionRepository;
	private final EvaluationScoringService scoringService;
	private final TemplateTechniqueEvaluation templateTechnique;
	private final LibellesProfilService libellesProfil;
	private final NomsCollaborateurs noms;

	public EvaluationMobileLectureService(
			EvaluationRepository evaluationRepository,
			EvaluationAnswerRepository answerRepository,
			SkillAnswerRepository skillAnswerRepository,
			EvaluationQuestionRepository questionRepository,
			EvaluationScoringService scoringService,
			TemplateTechniqueEvaluation templateTechnique,
			LibellesProfilService libellesProfil,
			NomsCollaborateurs noms) {
		this.evaluationRepository = evaluationRepository;
		this.answerRepository = answerRepository;
		this.skillAnswerRepository = skillAnswerRepository;
		this.questionRepository = questionRepository;
		this.scoringService = scoringService;
		this.templateTechnique = templateTechnique;
		this.libellesProfil = libellesProfil;
		this.noms = noms;
	}

	/** Vrai si la notation du responsable peut être montrée au collaborateur évalué (D-E15). */
	public static boolean notesResponsableVisibles(StatutEvaluationRh statut) {
		return statut == StatutEvaluationRh.VALIDEE || statut == StatutEvaluationRh.ARCHIVEE;
	}

	/** Le collaborateur évalué est masqué tant que l'évaluation n'est pas validée ; le responsable ne l'est jamais. */
	public static boolean masquerPour(IdentiteAppelant appelant, Evaluation evaluation) {
		return appelant.estCollaborateur(evaluation) && !appelant.estSuperieur(evaluation)
				&& !notesResponsableVisibles(evaluation.getStatut());
	}

	/** Existence de l'évaluation, avant le contrôle d'accès : 404 si inconnue. */
	@Transactional(readOnly = true)
	public void verifierExiste(UUID evaluationId) {
		if (!evaluationRepository.existsById(evaluationId)) {
			throw EvaluationMetierException.notFound(EVALUATION_INTROUVABLE, "Évaluation introuvable");
		}
	}

	// ------------------------------------------------------------------------------------------ B8 : réponses

	@Transactional(readOnly = true)
	public List<ReponseMobile> reponses(UUID evaluationId, IdentiteAppelant appelant) {
		Evaluation evaluation = charger(evaluationId);
		boolean masquer = masquerPour(appelant, evaluation);
		List<ReponseMobile> sortie = new ArrayList<>();
		for (EvaluationAnswer a : answerRepository.findWithQuestionByEvaluationId(evaluationId)) {
			Integer noteCollab = a.getNoteCollaborateur() != null ? a.getNoteCollaborateur() : a.getNoteAttribuee();
			sortie.add(new ReponseMobile(
					a.getId(), a.getQuestion().getId(), a.getQuestion().getLibelle(),
					a.getReponseCollaborateur(), noteCollab, a.getReponduParCollaborateurLe(),
					masquer ? null : a.getReponseManager(),
					masquer ? null : a.getCommentaireManager(),
					masquer ? null : a.getNoteManager(),
					masquer ? null : a.getReponduParManagerLe()));
		}
		return sortie;
	}

	// ------------------------------------------------------------------------------------------ B7 : détail

	@Transactional(readOnly = true)
	public EvaluationDetailMobile detail(UUID evaluationId, IdentiteAppelant appelant) {
		Evaluation e = charger(evaluationId);
		boolean masquer = masquerPour(appelant, e);
		boolean collaborateur = appelant.estCollaborateur(e) && !appelant.estSuperieur(e);

		List<EvaluationQuestion> techniques = templateTechnique.questions(e);
		Set<UUID> idsTechniques = new HashSet<>();
		techniques.forEach(q -> idsTechniques.add(q.getId()));
		List<EvaluationAnswer> reponses = answerRepository.findWithQuestionByEvaluationId(evaluationId);
		Map<UUID, EvaluationAnswer> parQuestion = new HashMap<>();
		reponses.forEach(r -> parQuestion.put(r.getQuestion().getId(), r));

		Map<UUID, EvaluationDetailMobile.Question> generales = new LinkedHashMap<>();
		Map<UUID, EvaluationDetailMobile.Question> techs = new LinkedHashMap<>();
		if (e.getCampaign() != null && e.getCampaign().getTemplateGeneral() != null) {
			for (EvaluationQuestion q : questionRepository
					.findByTemplateIdAndActifTrueOrderByOrdreAsc(e.getCampaign().getTemplateGeneral().getId())) {
				if (!idsTechniques.contains(q.getId())) {
					generales.put(q.getId(), question(q, parQuestion.get(q.getId()), masquer));
				}
			}
		}
		for (EvaluationQuestion q : techniques) {
			techs.put(q.getId(), question(q, parQuestion.get(q.getId()), masquer));
		}
		for (EvaluationAnswer r : reponses) {
			Map<UUID, EvaluationDetailMobile.Question> cible = idsTechniques.contains(r.getQuestion().getId()) ? techs : generales;
			cible.computeIfAbsent(r.getQuestion().getId(), k -> question(r.getQuestion(), r, masquer));
		}

		AnalyseMobile analyse = analyse(e, appelant);
		EvaluationDetailMobile.Scores scores = null;
		Integer score = masquer ? null : e.getScoreSur20();
		if (!masquer && (analyse.aNotesCollaborateur() || Boolean.TRUE.equals(analyse.aNotesResponsable()))) {
			scores = new EvaluationDetailMobile.Scores(
					analyse.aNotesCollaborateur() ? analyse.selfAverage() : null,
					Boolean.TRUE.equals(analyse.aNotesResponsable()) ? analyse.managerAverage() : null,
					score != null ? scoringService.appreciation(score).name() : null,
					score != null ? scoringService.appreciation(score).libelle() : null);
		}
		Map<UUID, com.hr.evaluation.client.PersonneResumee> personnes =
				noms.resoudre(List.of(e.getCollaborateurIdentifiant(), e.getSuperieurIdentifiant()));
		return new EvaluationDetailMobile(
				e.getId(),
				e.getCampaign() != null ? e.getCampaign().getNom() : null,
				NomsCollaborateurs.nom(personnes, e.getCollaborateurIdentifiant(), e.getCollaborateurNom()),
				NomsCollaborateurs.matricule(personnes, e.getCollaborateurIdentifiant()),
				e.getCollaborateurPoste(),
				NomsCollaborateurs.nom(personnes, e.getSuperieurIdentifiant(), e.getSuperieurNom()),
				e.getStatut().name(),
				e.getEtapeActuelle() != null ? e.getEtapeActuelle().name() : null,
				libellesProfil.famille(e.getFamilleMetierCode()),
				libellesProfil.niveau(e.getNiveauSeniorite()),
				collaborateur ? ROLE_COLLABORATEUR : ROLE_RESPONSABLE,
				masquer ? messageAttente(e.getStatut()) : null,
				score, scores,
				new ArrayList<>(generales.values()), new ArrayList<>(techs.values()),
				e.getValidationCollaborateurLe(), e.getValidationSuperieurLe(), e.getCreeLe());
	}

	private EvaluationDetailMobile.Question question(EvaluationQuestion q, EvaluationAnswer a, boolean masquer) {
		Integer noteCollab = a == null ? null
				: a.getNoteCollaborateur() != null ? a.getNoteCollaborateur() : a.getNoteAttribuee();
		String section = q.getSectionLibelle() != null && !q.getSectionLibelle().isBlank()
				? q.getSectionLibelle() : q.getSectionCode();
		boolean avecResponsable = a != null && !masquer;
		return new EvaluationDetailMobile.Question(q.getId(), q.getLibelle(), section, q.getTypeQuestion().name(),
				a == null ? null : a.getReponseCollaborateur(), noteCollab,
				avecResponsable ? a.getReponseManager() : null,
				avecResponsable ? a.getNoteManager() : null,
				avecResponsable ? a.getCommentaireManager() : null);
	}

	/** Message d'attente adapté au statut (D-E15), côté serveur pour que web et mobile disent la même chose. */
	static String messageAttente(StatutEvaluationRh statut) {
		return switch (statut) {
			case VALIDEE_COLLABORATEUR ->
					"Vous avez validé votre évaluation. Les notes de votre responsable seront visibles une fois qu'il l'aura validée.";
			case VALIDEE_SUPERIEUR ->
					"Votre responsable a terminé son évaluation. Validez la vôtre pour la finaliser et consulter son résultat.";
			default ->
					"Les notes de votre responsable seront visibles une fois votre évaluation validée par vous et par lui.";
		};
	}

	// ----------------------------------------------------------------------------------------- analytics mobile

	/**
	 * Analyse pour le mobile. Collaborateur avant validation : l'analyse est calculée sur ses seules notes (copies
	 * détachées sans la notation du responsable), et tout ce qui dépend du responsable est absent. Aucun niveau d'écart.
	 */
	@Transactional(readOnly = true)
	public AnalyseMobile analyse(UUID evaluationId, IdentiteAppelant appelant) {
		return analyse(charger(evaluationId), appelant);
	}

	private AnalyseMobile analyse(Evaluation evaluation, IdentiteAppelant appelant) {
		boolean masquer = masquerPour(appelant, evaluation);
		List<EvaluationAnswer> reponses = answerRepository.findWithQuestionByEvaluationId(evaluation.getId());
		List<SkillAnswer> competences =
				skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluation.getId());
		if (masquer) {
			reponses = reponses.stream().map(EvaluationMobileLectureService::sansResponsable).toList();
			competences = competences.stream().map(EvaluationMobileLectureService::sansResponsable).toList();
		}
		EvaluationAnalyticsResponse a = scoringService.analyser(reponses, competences);
		if (masquer) {
			return new AnalyseMobile(a.selfAverage(), null, null, null, null, null, a.strengths(),
					a.improvementAreas(), a.recommendations(), a.aNotesCollaborateur(), null);
		}
		List<AnalyseMobile.Ecart> ecarts = a.gaps().stream()
				.map(g -> new AnalyseMobile.Ecart(g.questionId(), g.label(), g.section(), g.selfScore(),
						g.managerScore(), g.gap()))
				.toList();
		return new AnalyseMobile(a.selfAverage(), a.managerAverage(), a.finalScore(), a.averageGap(),
				a.discrepancyPercentage(), ecarts, a.strengths(), a.improvementAreas(), a.recommendations(),
				a.aNotesCollaborateur(), a.aNotesResponsable());
	}

	/** Copie détachée (jamais persistée) ne portant que la saisie du collaborateur. */
	private static EvaluationAnswer sansResponsable(EvaluationAnswer source) {
		EvaluationAnswer copie = new EvaluationAnswer();
		copie.setQuestion(source.getQuestion());
		copie.setReponseCollaborateur(source.getReponseCollaborateur());
		copie.setNoteCollaborateur(source.getNoteCollaborateur());
		copie.setNoteAttribuee(source.getNoteAttribuee());
		return copie;
	}

	private static SkillAnswer sansResponsable(SkillAnswer source) {
		SkillAnswer copie = new SkillAnswer();
		copie.setQuestionTechnique(source.getQuestionTechnique());
		copie.setNiveauAutoEvaluation(source.getNiveauAutoEvaluation());
		return copie;
	}

	private Evaluation charger(UUID evaluationId) {
		return evaluationRepository.findByIdWithCampaign(evaluationId)
				.orElseThrow(() -> EvaluationMetierException.notFound(EVALUATION_INTROUVABLE, "Évaluation introuvable"));
	}
}
