package com.hr.evaluation.service;

import com.hr.evaluation.client.PersonneResumee;
import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.dto.AlerteEcartOuvertesParNiveau;
import com.hr.evaluation.dto.CampagneDetail;
import com.hr.evaluation.dto.EvaluationAnalyticsResponse;
import com.hr.evaluation.dto.EvaluationDetail;
import com.hr.evaluation.dto.EvaluationLigne;
import com.hr.evaluation.dto.PageEvaluations;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.entity.SkillAnswer;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lectures back-office de la campagne et de l'évaluation (ticket 2B, D-N01) : noms, matricules et libellés résolus
 * côté serveur (D-L01.a), codage couleur, niveau d'alerte, détail des deux séries de notes. Lecture seule : aucune
 * écriture, aucun effet sur les alertes. Les noms viennent du résolveur par lot (un appel par page), repli sur
 * l'instantané local, jamais un identifiant.
 */
@Service
public class EvaluationDetailService {

	public static final String CAMPAGNE_INTROUVABLE = "CAMPAGNE_INTROUVABLE";
	public static final String EVALUATION_INTROUVABLE = "EVALUATION_INTROUVABLE";
	public static final String PARAMETRE_INVALIDE = "PARAMETRE_INVALIDE";

	public static final String FAMILLE_ABSENTE = "FAMILLE_ABSENTE";
	public static final String NIVEAU_ABSENT = "NIVEAU_ABSENT";
	public static final String LES_DEUX = "LES_DEUX";

	public static final String EVT_CREEE = "CREEE";
	public static final String EVT_VALIDEE_COLLABORATEUR = "VALIDEE_COLLABORATEUR";
	public static final String EVT_VALIDEE_RESPONSABLE = "VALIDEE_RESPONSABLE";

	private final EvaluationRepository evaluationRepository;
	private final EvaluationCampaignRepository campagneRepository;
	private final EvaluationAnswerRepository answerRepository;
	private final SkillAnswerRepository skillAnswerRepository;
	private final EvaluationQuestionRepository questionRepository;
	private final AlerteEcartRepository alerteRepository;
	private final EvaluationAnalyseAdminService analyseAdminService;
	private final EvaluationCampaignService campagneService;
	private final AlerteEcartTraitementService alerteService;
	private final EvaluationScoringService scoringService;
	private final TemplateTechniqueEvaluation templateTechnique;
	private final LibellesProfilService libellesProfil;
	private final NomsCollaborateurs noms;
	private final int tailleMax;

	public EvaluationDetailService(
			EvaluationRepository evaluationRepository,
			EvaluationCampaignRepository campagneRepository,
			EvaluationAnswerRepository answerRepository,
			SkillAnswerRepository skillAnswerRepository,
			EvaluationQuestionRepository questionRepository,
			AlerteEcartRepository alerteRepository,
			EvaluationAnalyseAdminService analyseAdminService,
			EvaluationCampaignService campagneService,
			AlerteEcartTraitementService alerteService,
			EvaluationScoringService scoringService,
			TemplateTechniqueEvaluation templateTechnique,
			LibellesProfilService libellesProfil,
			NomsCollaborateurs noms,
			@Value("${evaluation.liste.taille-max}") int tailleMax) {
		this.evaluationRepository = evaluationRepository;
		this.campagneRepository = campagneRepository;
		this.answerRepository = answerRepository;
		this.skillAnswerRepository = skillAnswerRepository;
		this.questionRepository = questionRepository;
		this.alerteRepository = alerteRepository;
		this.analyseAdminService = analyseAdminService;
		this.campagneService = campagneService;
		this.alerteService = alerteService;
		this.scoringService = scoringService;
		this.templateTechnique = templateTechnique;
		this.libellesProfil = libellesProfil;
		this.noms = noms;
		this.tailleMax = tailleMax;
	}

	// ------------------------------------------------------------------------------------------- campagne (B1, B2)

	@Transactional(readOnly = true)
	public CampagneDetail campagne(UUID campagneId) {
		EvaluationCampaign c = chargerCampagne(campagneId);
		long total = evaluationRepository.countByCampaignId(campagneId);
		long validees = evaluationRepository.countByCampaignIdAndStatut(campagneId, StatutEvaluationRh.VALIDEE);
		AlerteEcartOuvertesParNiveau alertes = alerteService.ouvertesParNiveau(campagneId);
		// Comme la liste des campagnes : le template de compétences masque l'ancien template technique
		CampagneDetail.ModeleRef technique = c.getTemplateCompetence() != null
				? new CampagneDetail.ModeleRef(c.getTemplateCompetence().getId(), c.getTemplateCompetence().getNom())
				: c.getTemplateTechnique() != null
						? new CampagneDetail.ModeleRef(c.getTemplateTechnique().getId(), c.getTemplateTechnique().getNom())
						: null;
		CampagneDetail.ModeleRef general = c.getTemplateGeneral() != null
				? new CampagneDetail.ModeleRef(c.getTemplateGeneral().getId(), c.getTemplateGeneral().getNom())
				: null;
		return new CampagneDetail(c.getId(), c.getNom(), c.getDescription(), c.getType().name(), c.getStatut().name(),
				c.getAnnee(), c.getMoisDebut(), c.getMoisFin(), c.getJourDebut(), c.getDateDebut(), c.getDateFin(),
				c.getDureeJours(), general, technique,
				CampagneDetail.Compteurs.de(total, validees, campagneService.compterEvaluationsSansTemplateTechnique(c)),
				alertes, c.getCreeLe());
	}

	@Transactional(readOnly = true)
	public PageEvaluations evaluationsDeCampagne(UUID campagneId, StatutEvaluationRh statut, int page, int taille) {
		if (page < 0) {
			throw EvaluationMetierException.badRequest(PARAMETRE_INVALIDE, "Le paramètre page doit être positif ou nul.");
		}
		if (taille < 1 || taille > tailleMax) {
			throw EvaluationMetierException.badRequest(PARAMETRE_INVALIDE,
					"Le paramètre taille doit être compris entre 1 et " + tailleMax + ".");
		}
		chargerCampagne(campagneId);
		PageRequest demande = PageRequest.of(page, taille,
				Sort.by("collaborateurNom").ascending().and(Sort.by("id")));
		Page<Evaluation> lue = statut == null
				? evaluationRepository.findByCampaignId(campagneId, demande)
				: evaluationRepository.findByCampaignIdAndStatut(campagneId, statut, demande);
		return new PageEvaluations(lignes(lue.getContent()), lue.getTotalElements(), page, taille);
	}

	// ----------------------------------------------------------------------------------------- listes (B4, B5)

	/** Filtres cumulables ; chaque filtre nul est ignoré. Collaborateur et supérieur : fiche ou compte. */
	@Transactional(readOnly = true)
	public List<EvaluationLigne> lister(UUID campagneId, UUID collaborateurId, UUID superieurId,
			Boolean profilIncomplet) {
		Specification<Evaluation> filtre = (racine, requete, cb) -> {
			if (requete != null && requete.getResultType() != Long.class && requete.getResultType() != long.class) {
				racine.fetch("campaign", jakarta.persistence.criteria.JoinType.LEFT);
			}
			List<Predicate> conditions = new ArrayList<>();
			if (campagneId != null) {
				conditions.add(cb.equal(racine.get("campaign").get("id"), campagneId));
			}
			if (collaborateurId != null) {
				conditions.add(cb.or(cb.equal(racine.get("collaborateurIdentifiant"), collaborateurId),
						cb.equal(racine.get("collaborateurCompteId"), collaborateurId)));
			}
			if (superieurId != null) {
				conditions.add(cb.or(cb.equal(racine.get("superieurIdentifiant"), superieurId),
						cb.equal(racine.get("superieurCompteId"), superieurId)));
			}
			if (Boolean.TRUE.equals(profilIncomplet)) {
				conditions.add(cb.isTrue(racine.get("profilMetierIncomplet")));
			}
			return cb.and(conditions.toArray(new Predicate[0]));
		};
		return lignes(evaluationRepository.findAll(filtre, Sort.by(Sort.Direction.DESC, "creeLe")));
	}

	// ------------------------------------------------------------------------------------------------ détail (B3)

	@Transactional(readOnly = true)
	public EvaluationDetail detail(UUID evaluationId) {
		Evaluation e = evaluationRepository.findByIdWithCampaign(evaluationId)
				.orElseThrow(() -> EvaluationMetierException.notFound(EVALUATION_INTROUVABLE, "Évaluation introuvable"));
		EvaluationLigne entete = lignes(List.of(e)).get(0);

		List<EvaluationQuestion> techniques = templateTechnique.questions(e);
		Set<UUID> idsTechniques = new HashSet<>();
		techniques.forEach(q -> idsTechniques.add(q.getId()));
		List<EvaluationAnswer> reponses = answerRepository.findWithQuestionByEvaluationId(evaluationId);
		List<SkillAnswer> competences = skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId);
		Map<UUID, EvaluationAnswer> parQuestion = new HashMap<>();
		reponses.forEach(r -> parQuestion.put(r.getQuestion().getId(), r));

		// Questions du modèle, répondues ou non, puis les réponses restantes (question désactivée depuis)
		Map<UUID, EvaluationDetail.Question> generales = new LinkedHashMap<>();
		Map<UUID, EvaluationDetail.Question> techs = new LinkedHashMap<>();
		if (e.getCampaign() != null && e.getCampaign().getTemplateGeneral() != null) {
			for (EvaluationQuestion q : questionRepository
					.findByTemplateIdAndActifTrueOrderByOrdreAsc(e.getCampaign().getTemplateGeneral().getId())) {
				if (!idsTechniques.contains(q.getId())) {
					generales.put(q.getId(), question(q, parQuestion.get(q.getId()), false));
				}
			}
		}
		for (EvaluationQuestion q : techniques) {
			techs.put(q.getId(), question(q, parQuestion.get(q.getId()), true));
		}
		for (EvaluationAnswer r : reponses) {
			UUID qid = r.getQuestion().getId();
			boolean technique = idsTechniques.contains(qid);
			Map<UUID, EvaluationDetail.Question> cible = technique ? techs : generales;
			cible.computeIfAbsent(qid, k -> question(r.getQuestion(), r, technique));
		}
		List<EvaluationDetail.Question> listeTechniques = new ArrayList<>(techs.values());
		competences.forEach(c -> listeTechniques.add(competenceHistorique(c)));

		EvaluationAnalyticsResponse analyse = scoringService.analyser(reponses, competences, idsTechniques);
		EvaluationDetail.Scores scores = null;
		if (analyse.aNotesCollaborateur() || analyse.aNotesResponsable()) {
			scores = new EvaluationDetail.Scores(
					analyse.aNotesCollaborateur() ? analyse.selfAverage() : null,
					analyse.aNotesResponsable() ? analyse.managerAverage() : null,
					e.getScoreSur20(),
					e.getScoreSur20() != null ? scoringService.appreciation(e.getScoreSur20()).name() : null,
					e.getScoreSur20() != null ? scoringService.appreciation(e.getScoreSur20()).libelle() : null);
		}
		EvaluationDetail.AlerteRef alerte = entete.alerteId() == null ? null
				: new EvaluationDetail.AlerteRef(entete.alerteId(), entete.niveauAlerte(), entete.alerteStatut());
		return new EvaluationDetail(entete, new ArrayList<>(generales.values()), listeTechniques, scores, alerte,
				historique(e, entete));
	}

	private EvaluationDetail.Question question(EvaluationQuestion q, EvaluationAnswer a, boolean technique) {
		Integer noteCollab = a == null ? null
				: a.getNoteCollaborateur() != null ? a.getNoteCollaborateur() : a.getNoteAttribuee();
		Integer noteResp = a == null ? null : a.getNoteManager();
		Integer ecart = null;
		String niveau = null;
		if (technique && noteCollab != null && noteResp != null) {
			ecart = Math.abs(noteResp - noteCollab);
			niveau = EcartEtoiles.niveau(ecart).name();
		}
		String section = q.getSectionLibelle() != null && !q.getSectionLibelle().isBlank()
				? q.getSectionLibelle() : q.getSectionCode();
		return new EvaluationDetail.Question(q.getId(), q.getLibelle(), section, q.getTypeQuestion().name(),
				a == null ? null : a.getReponseCollaborateur(), noteCollab,
				a == null ? null : a.getReponseManager(), noteResp,
				a == null ? null : a.getCommentaireManager(), ecart, niveau);
	}

	/** Ancienne voie des compétences techniques (table skill_answer), encore lue pour les évaluations anciennes. */
	private EvaluationDetail.Question competenceHistorique(SkillAnswer c) {
		Integer noteCollab = c.getNiveauAutoEvaluation() != null ? c.getNiveauAutoEvaluation().score() : null;
		Integer noteResp = c.getNiveauManager() != null ? c.getNiveauManager().score() : null;
		Integer ecart = noteCollab != null && noteResp != null ? Math.abs(noteResp - noteCollab) : null;
		return new EvaluationDetail.Question(c.getQuestionTechnique().getId(), c.getQuestionTechnique().getCompetence(),
				"Compétences", "COMPETENCE", c.getCommentaireCollaborateur(), noteCollab,
				c.getNiveauManager() != null ? c.getNiveauManager().label() : null, noteResp,
				c.getCommentaireManager(), ecart, ecart == null ? null : EcartEtoiles.niveau(ecart).name());
	}

	private List<EvaluationDetail.Evenement> historique(Evaluation e, EvaluationLigne entete) {
		List<EvaluationDetail.Evenement> evenements = new ArrayList<>();
		evenements.add(new EvaluationDetail.Evenement(EVT_CREEE, e.getCreeLe(), null));
		if (e.getValidationCollaborateurLe() != null) {
			evenements.add(new EvaluationDetail.Evenement(EVT_VALIDEE_COLLABORATEUR, e.getValidationCollaborateurLe(),
					entete.collaborateurNom()));
		}
		if (e.getValidationSuperieurLe() != null) {
			// L'acteur est le RO qui a validé (compte) ; à défaut, le supérieur de l'évaluation
			Map<UUID, PersonneResumee> acteur = e.getValidationSuperieurPar() == null ? Map.of()
					: noms.resoudre(List.of(e.getValidationSuperieurPar()));
			evenements.add(new EvaluationDetail.Evenement(EVT_VALIDEE_RESPONSABLE, e.getValidationSuperieurLe(),
					NomsCollaborateurs.nom(acteur, e.getValidationSuperieurPar(), entete.superieurNom())));
		}
		evenements.sort(Comparator.comparing(EvaluationDetail.Evenement::le, Comparator.nullsLast(Comparator.naturalOrder())));
		return evenements;
	}

	// ------------------------------------------------------------------------------------------ construction ligne

	/** Une résolution de noms, une requête de notes et une d'alertes pour tout le lot. À appeler en transaction. */
	List<EvaluationLigne> lignes(List<Evaluation> evaluations) {
		if (evaluations.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = evaluations.stream().map(Evaluation::getId).toList();
		Set<UUID> personnesAResoudre = new HashSet<>();
		for (Evaluation e : evaluations) {
			personnesAResoudre.add(e.getCollaborateurIdentifiant());
			personnesAResoudre.add(e.getSuperieurIdentifiant());
		}
		Map<UUID, PersonneResumee> personnes = noms.resoudre(personnesAResoudre);
		Map<UUID, EvaluationAnalyseAdminService.NotesResponsable> notes = analyseAdminService.notesResponsable(ids);
		Map<UUID, AlerteEcart> alertes = new HashMap<>();
		alerteRepository.findByEvaluationIdIn(ids).forEach(a -> alertes.put(a.getEvaluationId(), a));

		List<EvaluationLigne> lignes = new ArrayList<>(evaluations.size());
		for (Evaluation e : evaluations) {
			var n = notes.get(e.getId());
			boolean aNote = n != null && n.aNote();
			AlerteEcart alerte = alertes.get(e.getId());
			String motif = e.isProfilMetierIncomplet() ? motif(e) : null;
			String campagneNom = e.getCampaign() != null ? e.getCampaign().getNom() : null;
			UUID campagneId = e.getCampaign() != null ? e.getCampaign().getId() : null;
			lignes.add(new EvaluationLigne(
					e.getId(), campagneId, campagneNom,
					e.getCollaborateurIdentifiant(),
					NomsCollaborateurs.nom(personnes, e.getCollaborateurIdentifiant(), e.getCollaborateurNom()),
					NomsCollaborateurs.matricule(personnes, e.getCollaborateurIdentifiant()),
					e.getCollaborateurPoste(),
					e.getSuperieurIdentifiant(),
					NomsCollaborateurs.nom(personnes, e.getSuperieurIdentifiant(), e.getSuperieurNom()),
					NomsCollaborateurs.matricule(personnes, e.getSuperieurIdentifiant()),
					e.getStatut().name(),
					e.getEtapeActuelle() != null ? e.getEtapeActuelle().name() : EvaluationStep.EVALUATION_GENERALE.name(),
					e.getScoreSur20(),
					aNote ? n.couleur().name() : null,
					aNote,
					alerte != null ? alerte.getNiveauCourant().name() : null,
					alerte != null ? alerte.getStatut().name() : null,
					alerte != null ? alerte.getId() : null,
					e.getFamilleMetierCode(), libellesProfil.famille(e.getFamilleMetierCode()),
					e.getNiveauSeniorite(), libellesProfil.niveau(e.getNiveauSeniorite()),
					e.isProfilMetierIncomplet(), motif,
					e.getValidationCollaborateurLe(), e.getValidationSuperieurLe(), e.getCreeLe(),
					campagneNom, e.getCollaborateurIdentifiant(), e.getSuperieurIdentifiant()));
		}
		return lignes;
	}

	static String motif(Evaluation e) {
		boolean famille = e.getFamilleMetierCode() == null || e.getFamilleMetierCode().isBlank();
		boolean niveau = e.getNiveauSeniorite() == null || e.getNiveauSeniorite().isBlank();
		if (famille && niveau) {
			return LES_DEUX;
		}
		if (famille) {
			return FAMILLE_ABSENTE;
		}
		return niveau ? NIVEAU_ABSENT : null;
	}

	private EvaluationCampaign chargerCampagne(UUID campagneId) {
		return campagneRepository.findById(campagneId)
				.orElseThrow(() -> EvaluationMetierException.notFound(CAMPAGNE_INTROUVABLE, "Campagne introuvable"));
	}
}
