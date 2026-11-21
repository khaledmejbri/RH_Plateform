package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EvaluateurCondition;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionResponse;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.moteur.EffetClient.ActeurEffet;
import com.hr.workflow.moteur.EffetClient.RequeteEffet;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.moteur.PreparationDecisionService.PlanExecutionDecision;
import com.hr.workflow.repository.WfAppelEffetRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Exécution d'une décision humaine sur une tâche (§ 9.6.4, D-W08 à D-W11, D-W29, D-W30, I1, I3, I8).
 * Ordre strict des verrous : {@code wf_instance} puis {@code wf_tache}.
 * <strong>Aucun appel HTTP dans une transaction.</strong>
 */
@Service
public class DecisionService {

	private static final int MAX_RENVOIS_PAR_ETAPE = 3;

	private final WfInstanceRepository instanceRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final WfAppelEffetRepository appelEffetRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final PreparationDecisionService preparationService;
	private final ApplicationDecisionService applicationService;
	private final EffetClient effetClient;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final EvaluateurCondition evaluateur = new EvaluateurCondition();
	private final Clock clock;

	public DecisionService(
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			WfAppelEffetRepository appelEffetRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			PreparationDecisionService preparationService,
			ApplicationDecisionService applicationService,
			EffetClient effetClient,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.appelEffetRepository = appelEffetRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.preparationService = preparationService;
		this.applicationService = applicationService;
		this.effetClient = effetClient;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public record ResultatDecision(int httpStatus, DecisionResponse reponse, boolean decisionEnCours) {
		public static ResultatDecision ok(DecisionResponse rep) {
			return new ResultatDecision(200, rep, false);
		}

		public static ResultatDecision enCours() {
			return new ResultatDecision(202, null, true);
		}
	}

	private record LectureContexte(
			UUID instanceId,
			UUID etapeExecutionId,
			String etapeCode,
			int nbRenvois,
			TypeTache typeTache,
			StatutTache statutTache,
			Long versionTache,
			String libelleTache,
			String objetType,
			UUID objetId,
			String objetNumero,
			String serviceProprietaire,
			UUID demandeurCollaborateurId,
			UUID demandeurCompteId,
			String demandeurNom,
			StatutInstance statutInstance,
			Map<String, Object> variables,
			DefinitionWorkflow definition,
			EtapeDefinition etapeDef,
			List<UUID> beneficiaires,
			List<UUID> approbateursPrecedents,
			UUID candidatCollaborateurId,
			String candidatNom,
			UUID pourCollaborateurId,
			String pourNom,
			boolean estCandidatActif,
			boolean estAutreTacheOuverteDansEtape,
			Map<String, Object> reponseMemorisee
	) {
	}

	public ResultatDecision decider(
			UUID tacheId,
			DecisionRequest req,
			UUID appelantCompteId,
			String appelantNom,
			boolean estBackofficeLecture) {

		// 1. Lecture sans verrou + contrôle de visibilité (§ 9.10 -> 404) + idempotence
		LectureContexte ctx = txReadOnly.execute(status -> chargerContexte(
				tacheId, req.cleIdempotence(), appelantCompteId, estBackofficeLecture));

		if (ctx.reponseMemorisee() != null) {
			return ResultatDecision.ok(ApplicationDecisionService.reconstruireReponse(ctx.reponseMemorisee()));
		}

		// 2. Contrôles dans l'ordre exact du § 9.6.4
		if (!ctx.estCandidatActif()) {
			throw WorkflowMetierException.forbidden("PAS_INTERVENANT",
					"Vous n'êtes pas intervenant actif sur cette tâche.");
		}
		if (ctx.typeTache() == TypeTache.VALIDATION) {
			boolean auto = appelantCompteId.equals(ctx.demandeurCompteId())
					|| (ctx.candidatCollaborateurId() != null && ctx.candidatCollaborateurId().equals(ctx.demandeurCollaborateurId()))
					|| (ctx.candidatCollaborateurId() != null && ctx.beneficiaires().contains(ctx.candidatCollaborateurId()));
			if (auto) {
				throw WorkflowMetierException.forbidden("AUTO_DECISION_INTERDITE",
						"Vous ne pouvez pas décider sur votre propre demande.");
			}
		}
		if (ctx.statutTache() == StatutTache.RESERVEE) {
			throw WorkflowMetierException.conflict("DECISION_EN_COURS",
					"Une décision est déjà en cours d'enregistrement sur cette tâche.");
		}
		if (ctx.statutTache() != StatutTache.A_TRAITER) {
			throw WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE",
					"Cette tâche a déjà été traitée.");
		}

		String action = req.action().trim();
		verifierActionPermise(ctx.typeTache(), ctx.etapeDef(), action);

		String commentaire = req.commentaire() != null ? req.commentaire().trim() : "";
		// I3 : refus ou renvoi exige un motif 5..500
		if (("REFUSER".equals(action) || "RENVOYER".equals(action))
				&& (commentaire.length() < 5 || commentaire.length() > 500)) {
			throw WorkflowMetierException.unprocessable("MOTIF_OBLIGATOIRE",
					"Un motif de 5 à 500 caractères est obligatoire.");
		}
		// Commentaire conditionnel sur APPROUVER (§ 9.3.1)
		if ("APPROUVER".equals(action) && ctx.etapeDef() != null && ctx.etapeDef().commentaireObligatoireSi() != null) {
			Map<String, TypeVariable> typesVars = typesVariablesPour(ctx.objetType());
			if (evaluateur.evaluer(ctx.etapeDef().commentaireObligatoireSi(), ctx.variables(), typesVars)
					&& (commentaire.length() < 10 || commentaire.length() > 500)) {
				throw WorkflowMetierException.unprocessable("COMMENTAIRE_OBLIGATOIRE",
						"Un commentaire de 10 à 500 caractères est obligatoire pour cette approbation.");
			}
		}
		if ("RENVOYER".equals(action) && ctx.nbRenvois() >= MAX_RENVOIS_PAR_ETAPE) {
			throw WorkflowMetierException.unprocessable("RENVOIS_EPUISES",
					"Le nombre maximal de renvois pour complément (3) a été atteint pour cette étape.");
		}
		if (!ctx.versionTache().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
					"La tâche a été modifiée par un autre utilisateur.");
		}

		TypeObjetCatalogue typeCatalogue = catalogue.findByObjetType(ctx.objetType())
				.orElseThrow(() -> WorkflowMetierException.badRequest("TYPE_OBJET_INCONNU",
						"Type d'objet inconnu : " + ctx.objetType()));

		String nomActeur = ctx.candidatNom() != null && !ctx.candidatNom().isBlank() ? ctx.candidatNom() : appelantNom;
		// 3..6. Exécution (avec rejeu unique hors transaction si l'étape devient finie sous verrou en mode TOUS / TACHES_PARALLELES)
		try {
			return executerDecision(tacheId, req, appelantCompteId, nomActeur, ctx, typeCatalogue, action, commentaire, false);
		} catch (EtapeDevenueFinieException ex) {
			LectureContexte ctxRejeu = txReadOnly.execute(status -> chargerContexte(
					tacheId, req.cleIdempotence(), appelantCompteId, estBackofficeLecture));
			if (ctxRejeu.reponseMemorisee() != null) {
				return ResultatDecision.ok(ApplicationDecisionService.reconstruireReponse(ctxRejeu.reponseMemorisee()));
			}
			return executerDecision(tacheId, req, appelantCompteId, nomActeur, ctxRejeu, typeCatalogue, action, commentaire, true);
		}
	}

	private static final class EtapeDevenueFinieException extends RuntimeException {
	}

	private ResultatDecision executerDecision(
			UUID tacheId,
			DecisionRequest req,
			UUID appelantCompteId,
			String nomActeur,
			LectureContexte ctx,
			TypeObjetCatalogue typeCatalogue,
			String action,
			String commentaire,
			boolean forcerTermineEtape) {
		ActeurEffet acteurEffet = new ActeurEffet(appelantCompteId, ctx.candidatCollaborateurId(), nomActeur, ctx.pourNom());

		// 3. HORS TRANSACTION : CONTROLER (si applicable)
		if (!forcerTermineEtape && "APPROUVER".equals(action) && ctx.etapeDef() != null && ctx.etapeDef().controleAvantApprobationActif()) {
			ResultatAppelEffet ctrl = effetClient.appeler(
					ctx.serviceProprietaire(),
					typeCatalogue.cheminEffet(),
					new RequeteEffet("controle:" + tacheId + ":" + req.cleIdempotence(), "CONTROLER",
							ctx.objetType(), ctx.objetId(), "CONTROLER", acteurEffet, commentaire,
							ctx.instanceId(), ctx.etapeDef().libelle()));
			if (ctrl instanceof ResultatAppelEffet.RejetMetier rejet) {
				throw new WorkflowMetierException(rejet.code(), rejet.message(), rejet.httpStatus());
			}
			if (ctrl instanceof ResultatAppelEffet.Incertain) {
				throw WorkflowMetierException.indisponible("SERVICE_INDISPONIBLE",
						"Le service propriétaire est momentanément indisponible pour le contrôle préalable.");
			}
		}

		List<IntervenantResolu> candidatsEtapeTerminee = (ctx.candidatCollaborateurId() != null)
				? List.of(new IntervenantResolu(ctx.candidatCollaborateurId(), appelantCompteId, nomActeur, null))
				: List.of();

		boolean termineEtape = forcerTermineEtape || determinerSiTermineEtape(ctx, action);
		PlanExecutionDecision exec = preparationService.preparer(
				action, ctx.typeTache(), ctx.etapeCode(), ctx.etapeDef(), ctx.definition(), ctx.objetType(),
				ctx.variables(), ctx.demandeurCollaborateurId(), ctx.demandeurCompteId(), ctx.beneficiaires(),
				ctx.approbateursPrecedents(), ctx.candidatCollaborateurId(), termineEtape, candidatsEtapeTerminee);

		// 4. Effet terminal (§ 9.6.4 étape 4)
		String effetTerminal = determinerEffetTerminal(exec, action, typeCatalogue);

		// 5. SANS EFFET TERMINAL : une transaction atomique
		if (effetTerminal == null) {
			DecisionResponse rep = txWrite.execute(status -> appliquerDecisionDansTransaction(
					ctx.instanceId(), tacheId, req, action, commentaire, appelantCompteId,
					ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(),
					typeCatalogue, ctx.etapeDef(), exec, false));
			return ResultatDecision.ok(rep);
		}

		// 6. AVEC EFFET TERMINAL : transaction 1 (RESERVEE + wf_appel_effet RESERVE) -> appel APPLIQUER hors tx -> transaction 2
		String cleEffet = "decision:" + tacheId + ":" + req.cleIdempotence();
		UUID appelEffetId = txWrite.execute(status -> reserverPourEffet(
				ctx.instanceId(), tacheId, req, cleEffet, effetTerminal, ctx.serviceProprietaire(),
				appelantCompteId, ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(), commentaire));

		ResultatAppelEffet resEffet = effetClient.appeler(
				ctx.serviceProprietaire(),
				typeCatalogue.cheminEffet(),
				new RequeteEffet(cleEffet, "APPLIQUER", ctx.objetType(), ctx.objetId(), effetTerminal,
						acteurEffet, commentaire, ctx.instanceId(), ctx.libelleTache()));

		if (resEffet instanceof ResultatAppelEffet.Succes succes) {
			record IssueSucces(DecisionResponse reponse, String codeConflit, String messageConflit) {}
			IssueSucces issue = txWrite.execute(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId())
						.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
				WfTache t = tacheRepository.findByIdForUpdate(tacheId)
						.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
				WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElseThrow();
				ae.setStatut(StatutAppelEffet.APPLIQUE);
				ae.setReponse(succes.corps());
				ae.setTentatives(ae.getTentatives() + 1);
				ae.setTermineLe(clock.instant());
				appelEffetRepository.save(ae);

				if (inst.getStatut().estTerminale()) {
					return new IssueSucces(null, "INSTANCE_TERMINEE", "Cette instance est déjà terminée.");
				}
				if (t.getStatut() != StatutTache.RESERVEE) {
					return new IssueSucces(null, "TACHE_DEJA_TRAITEE", "Cette tâche a déjà été traitée.");
				}
				DecisionResponse rep = applicationService.appliquerDansTransaction(
						inst, t, req.cleIdempotence(), action, commentaire, appelantCompteId,
						ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(),
						typeCatalogue, ctx.etapeDef(), exec);
				return new IssueSucces(rep, null, null);
			});
			if (issue != null && issue.codeConflit() != null) {
				throw WorkflowMetierException.conflict(issue.codeConflit(), issue.messageConflit());
			}
			return ResultatDecision.ok(issue.reponse());
		}

		if (resEffet instanceof ResultatAppelEffet.RejetMetier rejet) {
			txWrite.executeWithoutResult(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElseThrow();
				WfTache t = tacheRepository.findByIdForUpdate(tacheId).orElseThrow();
				WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElseThrow();
				ae.setStatut(StatutAppelEffet.REJETE);
				ae.setReponse(rejet.corps());
				ae.setTentatives(ae.getTentatives() + 1);
				ae.setTermineLe(clock.instant());
				appelEffetRepository.save(ae);

				if (!inst.getStatut().estTerminale() && t.getStatut() == StatutTache.RESERVEE) {
					t.setStatut(StatutTache.A_TRAITER);
					t.setCleIdempotence(null);
					tacheRepository.save(t);
					enregistrerHistorique(inst.getId(), t.getId(), "EFFET_REJETE", appelantCompteId, nomActeur,
							ctx.pourNom(), Map.of("effet", ae.getEffet(), "code", rejet.code()), clock.instant());
				}
			});
			throw new WorkflowMetierException(rejet.code(), rejet.message(), rejet.httpStatus());
		}

		// INCERTAIN (délai dépassé, 5xx ou réseau) -> 202 DECISION_EN_COURS, reprise D-W29
		ResultatAppelEffet.Incertain incertain = (ResultatAppelEffet.Incertain) resEffet;
		txWrite.executeWithoutResult(status -> {
			WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElseThrow();
			ae.setStatut(StatutAppelEffet.INCERTAIN);
			ae.setTentatives(1);
			ae.setProchaineTentativeLe(clock.instant().plus(1, ChronoUnit.MINUTES));
			ae.setReponse(Map.of("erreur", incertain.erreur()));
			appelEffetRepository.save(ae);
		});
		return ResultatDecision.enCours();
	}

	private static String determinerEffetTerminal(PlanExecutionDecision exec, String action, TypeObjetCatalogue typeCatalogue) {
		if (exec.plan() != null && exec.plan().statutFinal() == StatutInstance.APPROUVEE) {
			return "APPROUVER";
		}
		if (exec.plan() != null && exec.plan().statutFinal() == StatutInstance.REFUSEE) {
			return "REFUSER";
		}
		if ("RENVOYER".equals(action) && typeCatalogue.supporteComplement()) {
			return "METTRE_A_COMPLETER";
		}
		return null;
	}

	private UUID reserverPourEffet(
			UUID instanceId,
			UUID tacheId,
			DecisionRequest req,
			String cleEffet,
			String effet,
			String serviceProprietaire,
			UUID appelantCompteId,
			UUID appelantCollabId,
			String appelantNom,
			UUID pourCollabId,
			String pourNom,
			String commentaire) {
		// Ordre strict des verrous : wf_instance PUIS wf_tache (§ 9.6.7)
		WfInstance inst = instanceRepository.findByIdForUpdate(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		WfTache t = tacheRepository.findByIdForUpdate(tacheId)
				.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));

		if (inst.getStatut().estTerminale()) {
			throw WorkflowMetierException.conflict("INSTANCE_TERMINEE", "Cette instance est déjà terminée.");
		}
		if (t.getStatut() == StatutTache.RESERVEE) {
			throw WorkflowMetierException.conflict("DECISION_EN_COURS", "Décision déjà en cours sur cette tâche.");
		}
		if (t.getStatut() != StatutTache.A_TRAITER) {
			throw WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE", "Cette tâche a déjà été traitée.");
		}
		if (!t.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS", "La tâche a été modifiée entre-temps.");
		}

		Instant maintenant = clock.instant();
		t.setStatut(StatutTache.RESERVEE);
		t.setDecision(ApplicationDecisionService.versDecisionTache(req.action().trim()));
		t.setCommentaire(commentaire);
		t.setDecideParCompteId(appelantCompteId);
		t.setDecideParCollaborateurId(appelantCollabId);
		t.setDecideParNom(appelantNom);
		t.setPourCollaborateurId(pourCollabId);
		t.setPourNom(pourNom);
		t.setCleIdempotence(req.cleIdempotence());
		tacheRepository.saveAndFlush(t);

		WfAppelEffet ae = appelEffetRepository.findByCleIdempotence(cleEffet).orElse(null);
		if (ae != null) {
			if (ae.getStatut() != StatutAppelEffet.REJETE) {
				throw WorkflowMetierException.conflict("DECISION_EN_COURS",
						"Une décision avec cette clé est déjà en cours ou appliquée.");
			}
			ae.setInstanceId(instanceId);
			ae.setTacheId(tacheId);
			ae.setEffet(effet);
			ae.setServiceProprietaire(serviceProprietaire);
			ae.setStatut(StatutAppelEffet.RESERVE);
			ae.setTentatives(0);
			ae.setReponse(null);
			ae.setProchaineTentativeLe(null);
			ae.setTermineLe(null);
			ae.setCreeLe(maintenant);
		} else {
			ae = new WfAppelEffet();
			ae.setCleIdempotence(cleEffet);
			ae.setInstanceId(instanceId);
			ae.setTacheId(tacheId);
			ae.setEffet(effet);
			ae.setServiceProprietaire(serviceProprietaire);
			ae.setStatut(StatutAppelEffet.RESERVE);
			ae.setTentatives(0);
			ae.setCreeLe(maintenant);
		}
		ae = appelEffetRepository.saveAndFlush(ae);
		return ae.getId();
	}

	private DecisionResponse appliquerDecisionDansTransaction(
			UUID instanceId,
			UUID tacheId,
			DecisionRequest req,
			String action,
			String commentaire,
			UUID appelantCompteId,
			UUID appelantCollabId,
			String appelantNom,
			UUID pourCollabId,
			String pourNom,
			TypeObjetCatalogue typeCatalogue,
			EtapeDefinition etapeTermineeDef,
			PlanExecutionDecision exec,
			boolean depuisReservee) {
		// Ordre strict des verrous : wf_instance PUIS wf_tache (§ 9.6.7)
		WfInstance inst = instanceRepository.findByIdForUpdate(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		WfTache t = tacheRepository.findByIdForUpdate(tacheId)
				.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));

		if (!depuisReservee) {
			if (req.cleIdempotence().equals(t.getCleIdempotence()) && t.getReponseDecision() != null) {
				return ApplicationDecisionService.reconstruireReponse(t.getReponseDecision());
			}
			if (inst.getStatut().estTerminale()) {
				throw WorkflowMetierException.conflict("INSTANCE_TERMINEE", "Cette instance est déjà terminée.");
			}
			if (t.getStatut() == StatutTache.RESERVEE) {
				throw WorkflowMetierException.conflict("DECISION_EN_COURS", "Décision déjà en cours sur cette tâche.");
			}
			if (t.getStatut() != StatutTache.A_TRAITER) {
				throw WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE", "Cette tâche a déjà été traitée.");
			}
			if (!t.getVersion().equals(req.version())) {
				throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS", "La tâche a été modifiée entre-temps.");
			}
			// Mode TOUS / TACHES_PARALLELES : revérifier sous verrou s'il reste d'autres tâches obligatoires ouvertes
			if (exec.plan() == null && exec.planReactivation() == null
					&& !"RENVOYER".equals(action) && t.getType() != TypeTache.COMPLEMENT) {
				boolean autreObligatoireOuverte = tacheRepository.findByEtapeExecutionId(t.getEtapeExecution().getId()).stream()
						.anyMatch(autre -> !autre.getId().equals(t.getId())
								&& autre.isObligatoire()
								&& (autre.getStatut() == StatutTache.A_TRAITER || autre.getStatut() == StatutTache.RESERVEE));
				if (!autreObligatoireOuverte) {
					throw new EtapeDevenueFinieException();
				}
			}
		}

		return applicationService.appliquerDansTransaction(
				inst, t, req.cleIdempotence(), action, commentaire, appelantCompteId,
				appelantCollabId, appelantNom, pourCollabId, pourNom, typeCatalogue, etapeTermineeDef, exec);
	}

	private LectureContexte chargerContexte(UUID tacheId, String cleIdempotence, UUID appelantCompteId, boolean estBackofficeLecture) {
		WfTache t = tacheRepository.findByIdAvecDetails(tacheId)
				.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
		WfInstance inst = t.getInstance();
		WfEtapeExecution ee = t.getEtapeExecution();

		List<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
				.map(b -> b.getId().getCollaborateurId()).toList();
		boolean estCandidatQuelconque = candidatRepository.existsByIdTacheIdAndCompteId(tacheId, appelantCompteId);
		boolean estDemandeur = appelantCompteId.equals(inst.getDemandeurCompteId());

		// Visibilité § 9.10 : sinon 404 identique à introuvable (anti-IDOR)
		if (!estBackofficeLecture && !estCandidatQuelconque && !estDemandeur) {
			throw WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable.");
		}

		if (cleIdempotence != null && cleIdempotence.equals(t.getCleIdempotence()) && t.getReponseDecision() != null) {
			return new LectureContexte(inst.getId(), ee.getId(), ee.getEtapeCode(), ee.getNbRenvois(),
					t.getType(), t.getStatut(), t.getVersion(), t.getLibelle(), inst.getObjetType(),
					inst.getObjetId(), inst.getObjetNumero(), inst.getServiceProprietaire(),
					inst.getDemandeurCollaborateurId(), inst.getDemandeurCompteId(), inst.getDemandeurNom(),
					inst.getStatut(), inst.getVariables(), null, null, benefs, List.of(),
					null, null, null, null, true, false, t.getReponseDecision());
		}

		Optional<WfTacheCandidat> candActif = candidatRepository.findByIdTacheIdAndCompteIdAndRetireLeIsNull(tacheId, appelantCompteId);
		DefinitionWorkflow def = codec.deserialiser(inst.getModeleVersion().getDefinition());
		EtapeDefinition etapeDef = def.etapes().stream()
				.filter(e -> e.code().equals(ee.getEtapeCode()))
				.findFirst().orElse(null);

		List<WfTache> tachesEtape = tacheRepository.findByEtapeExecutionId(ee.getId());
		boolean autreObligatoireOuverte = tachesEtape.stream()
				.anyMatch(autre -> !autre.getId().equals(tacheId)
						&& autre.isObligatoire()
						&& (autre.getStatut() == StatutTache.A_TRAITER || autre.getStatut() == StatutTache.RESERVEE));

		List<UUID> approbateurs = tacheRepository.findApprobateursCollaborateurIds(inst.getId());

		return new LectureContexte(
				inst.getId(), ee.getId(), ee.getEtapeCode(), ee.getNbRenvois(),
				t.getType(), t.getStatut(), t.getVersion(), t.getLibelle(),
				inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero(), inst.getServiceProprietaire(),
				inst.getDemandeurCollaborateurId(), inst.getDemandeurCompteId(), inst.getDemandeurNom(),
				inst.getStatut(), inst.getVariables() != null ? inst.getVariables() : Map.of(),
				def, etapeDef, benefs, approbateurs,
				candActif.map(c -> c.getId().getCollaborateurId()).orElse(null),
				candActif.map(WfTacheCandidat::getNom).orElse(null),
				candActif.map(WfTacheCandidat::getPourCollaborateurId).orElse(null),
				null,
				candActif.isPresent(),
				autreObligatoireOuverte,
				null
		);
	}

	private static boolean determinerSiTermineEtape(LectureContexte ctx, String action) {
		if ("RENVOYER".equals(action) || ctx.typeTache() == TypeTache.COMPLEMENT) {
			return false;
		}
		if ("REFUSER".equals(action)) {
			// Un seul refus termine l'étape même en mode TOUS (D-W08)
			return true;
		}
		// En mode TOUS ou TACHES_PARALLELES, l'étape se termine quand il n'y a plus d'autre tâche obligatoire ouverte
		return !ctx.estAutreTacheOuverteDansEtape();
	}

	private static void verifierActionPermise(TypeTache typeTache, EtapeDefinition etapeDef, String action) {
		if (typeTache == TypeTache.TACHE || typeTache == TypeTache.COMPLEMENT) {
			if (!"FAIT".equals(action)) {
				throw WorkflowMetierException.unprocessable("ACTION_NON_AUTORISEE",
						"Seule l'action FAIT est autorisée sur cette tâche.");
			}
			return;
		}
		List<String> permises = (etapeDef != null && etapeDef.actions() != null)
				? etapeDef.actions()
				: List.of("APPROUVER", "REFUSER");
		if (!permises.contains(action)) {
			throw WorkflowMetierException.unprocessable("ACTION_NON_AUTORISEE",
					"L'action '" + action + "' n'est pas autorisée sur cette étape.");
		}
	}

	private Map<String, TypeVariable> typesVariablesPour(String objetType) {
		Map<String, TypeVariable> map = new HashMap<>();
		for (VariableCatalogue vc : catalogue.variablesPour(objetType).values()) {
			map.put(vc.nom(), vc.type());
		}
		return map;
	}

	private void enregistrerHistorique(
			UUID instanceId, UUID tacheId, String typeEvenement,
			UUID acteurCompteId, String acteurNom, String pourNom,
			Map<String, Object> details, Instant horodatage) {
		WfHistorique h = new WfHistorique();
		h.setInstanceId(instanceId);
		h.setTacheId(tacheId);
		h.setTypeEvenement(typeEvenement);
		h.setActeurCompteId(acteurCompteId);
		h.setActeurNom(acteurNom);
		h.setPourNom(pourNom);
		h.setDetails(details);
		h.setHorodatage(horodatage);
		historiqueRepository.save(h);
	}
}
