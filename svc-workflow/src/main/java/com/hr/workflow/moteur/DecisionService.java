package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EvaluateurCondition;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.ResultatEtapeExecution;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.EtapeCouranteDto;
import com.hr.workflow.dto.WorkflowMoteurDtos.InstanceDecisionDto;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfBeneficiaire;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.AvancementService.EtapeActivee;
import com.hr.workflow.moteur.AvancementService.EtapeInformation;
import com.hr.workflow.moteur.AvancementService.EtapeSautee;
import com.hr.workflow.moteur.AvancementService.PlanAvancement;
import com.hr.workflow.moteur.AvancementService.TacheACreer;
import com.hr.workflow.moteur.EffetClient.ActeurEffet;
import com.hr.workflow.moteur.EffetClient.RequeteEffet;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.repository.WfAppelEffetRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
	private final WfEtapeExecutionRepository etapeExecutionRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final WfAppelEffetRepository appelEffetRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final ResolutionIntervenantsService resolutionService;
	private final WorkflowReferentielClient referentielClient;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final AvancementService avancementService;
	private final EffetClient effetClient;
	private final OutboxService outboxService;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final EvaluateurCondition evaluateur = new EvaluateurCondition();
	private final Clock clock;

	public DecisionService(
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfEtapeExecutionRepository etapeExecutionRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			WfAppelEffetRepository appelEffetRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			ResolutionIntervenantsService resolutionService,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			AvancementService avancementService,
			EffetClient effetClient,
			OutboxService outboxService,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.etapeExecutionRepository = etapeExecutionRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.appelEffetRepository = appelEffetRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.resolutionService = resolutionService;
		this.referentielClient = referentielClient;
		this.planificationEcheanceService = planificationEcheanceService;
		this.avancementService = avancementService;
		this.effetClient = effetClient;
		this.outboxService = outboxService;
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
			return ResultatDecision.ok(reconstruireReponse(ctx.reponseMemorisee()));
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
		ActeurEffet acteurEffet = new ActeurEffet(appelantCompteId, ctx.candidatCollaborateurId(), nomActeur, ctx.pourNom());

		// 3. HORS TRANSACTION : CONTROLER (si applicable) + calcul du plan + résolutions suivantes
		if ("APPROUVER".equals(action) && ctx.etapeDef() != null && ctx.etapeDef().controleAvantApprobationActif()) {
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

		Instant maintenant = clock.instant();
		LocalDate aujourdhui = LocalDate.now(clock);
		CalendrierSocieteResponse calendrier = null;
		PlanAvancement plan = null;
		Instant echeanceSuivante = null;
		List<IntervenantResolu> candidatsReactivation = null;
		Map<String, List<UUID>> comptesInfos = Map.of();

		boolean termineEtape = determinerSiTermineEtape(ctx, action);

		if ("RENVOYER".equals(action)) {
			calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(30));
			echeanceSuivante = planificationEcheanceService.calculerEcheanceComplement(maintenant, calendrier);
		} else if (ctx.typeTache() == TypeTache.COMPLEMENT) {
			// Réactivation de l'étape avec résolution fraîche des candidats (D-W10)
			calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));
			List<UUID> exclure = ctx.definition().separationDesTaches() ? ctx.approbateursPrecedents() : List.of();
			RegleAResoudre regle = new RegleAResoudre(
					ctx.etapeCode(), ctx.etapeDef().intervenants(), ctx.etapeDef().repliSiVide(),
					"VALIDATION".equals(ctx.etapeDef().type()));
			LotResolu lot = resolutionService.resoudre(
					aujourdhui, ctx.demandeurCollaborateurId(), ctx.beneficiaires(), exclure, List.of(regle));
			var res = lot.resultats().get(ctx.etapeCode());
			candidatsReactivation = res != null ? res.candidats() : List.of();
			echeanceSuivante = planificationEcheanceService.calculerEcheance(
					maintenant, ctx.etapeDef().delai(), calendrier, ctx.variables());
		} else if (termineEtape) {
			List<UUID> exclure = new ArrayList<>();
			if (ctx.definition().separationDesTaches()) {
				exclure.addAll(ctx.approbateursPrecedents());
				if ("APPROUVER".equals(action) && ctx.candidatCollaborateurId() != null) {
					exclure.add(ctx.candidatCollaborateurId());
				}
			}
			List<RegleAResoudre> regles = DemarrageService.collecterRegles(ctx.definition());
			LotResolu lot = resolutionService.resoudre(
					aujourdhui, ctx.demandeurCollaborateurId(), ctx.beneficiaires(), exclure, regles);
			String resultatEtape = "REFUSER".equals(action) ? "REFUSEE"
					: ("FAIT".equals(action) ? "FAITE" : "APPROUVEE");
			plan = avancementService.calculerApresEtape(
					ctx.definition(), ctx.etapeCode(), resultatEtape,
					ctx.variables(), typesVariablesPour(ctx.objetType()), lot.resultats());
			if (plan.etapeSuivante() != null && !plan.etapeSuivante().bloquee()) {
				calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));
				echeanceSuivante = planificationEcheanceService.calculerEcheance(
						maintenant, plan.etapeSuivante().etape().delai(), calendrier, ctx.variables());
			}
			if (!plan.etapesInformation().isEmpty()) {
				Map<String, List<UUID>> mapInfos = new HashMap<>();
				for (EtapeInformation info : plan.etapesInformation()) {
					mapInfos.put(info.code(), resolutionService.resoudreComptesPublics(
							aujourdhui, info.publicDestinataires(), ctx.demandeurCollaborateurId(),
							ctx.demandeurCompteId(), List.of()));
				}
				comptesInfos = mapInfos;
			}
		}

		// 4. Effet terminal (§ 9.6.4 étape 4)
		String effetTerminal = null;
		if (plan != null && plan.statutFinal() == StatutInstance.APPROUVEE) {
			effetTerminal = "APPROUVER";
		} else if (plan != null && plan.statutFinal() == StatutInstance.REFUSEE) {
			effetTerminal = "REFUSER";
		} else if ("RENVOYER".equals(action) && typeCatalogue.supporteComplement()) {
			effetTerminal = "METTRE_A_COMPLETER";
		}

		// 5. SANS EFFET TERMINAL : une transaction atomique
		final PlanAvancement finalPlan = plan;
		final Instant finalEcheance = echeanceSuivante;
		final List<IntervenantResolu> finalCandReactiv = candidatsReactivation;
		final Map<String, List<UUID>> finalComptesInfos = comptesInfos;

		if (effetTerminal == null) {
			DecisionResponse rep = txWrite.execute(status -> appliquerDecisionDansTransaction(
					ctx.instanceId(), tacheId, req, action, commentaire, appelantCompteId,
					ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(),
					typeCatalogue, finalPlan, finalEcheance, finalCandReactiv, finalComptesInfos, false));
			return ResultatDecision.ok(rep);
		}

		// 6. AVEC EFFET TERMINAL : transaction 1 (RESERVEE + wf_appel_effet RESERVE) -> appel APPLIQUER hors tx -> transaction 2
		final String finalEffetTerminal = effetTerminal;
		String cleEffet = "decision:" + tacheId + ":" + req.cleIdempotence();
		UUID appelEffetId = txWrite.execute(status -> reserverPourEffet(
				ctx.instanceId(), tacheId, req, cleEffet, finalEffetTerminal, ctx.serviceProprietaire(),
				appelantCompteId, ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(), commentaire));

		ResultatAppelEffet resEffet = effetClient.appeler(
				ctx.serviceProprietaire(),
				typeCatalogue.cheminEffet(),
				new RequeteEffet(cleEffet, "APPLIQUER", ctx.objetType(), ctx.objetId(), effetTerminal,
						acteurEffet, commentaire, ctx.instanceId(), ctx.libelleTache()));

		if (resEffet instanceof ResultatAppelEffet.Succes succes) {
			DecisionResponse rep = txWrite.execute(status -> {
				WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElseThrow();
				ae.setStatut(StatutAppelEffet.APPLIQUE);
				ae.setReponse(succes.corps());
				ae.setTentatives(ae.getTentatives() + 1);
				ae.setTermineLe(clock.instant());
				appelEffetRepository.save(ae);
				return appliquerDecisionDansTransaction(
						ctx.instanceId(), tacheId, req, action, commentaire, appelantCompteId,
						ctx.candidatCollaborateurId(), nomActeur, ctx.pourCollaborateurId(), ctx.pourNom(),
						typeCatalogue, finalPlan, finalEcheance, finalCandReactiv, finalComptesInfos, true);
			});
			return ResultatDecision.ok(rep);
		}

		if (resEffet instanceof ResultatAppelEffet.RejetMetier rejet) {
			txWrite.executeWithoutResult(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElseThrow();
				WfTache t = tacheRepository.findByIdForUpdate(tacheId).orElseThrow();
				t.setStatut(StatutTache.A_TRAITER);
				t.setCleIdempotence(null);
				tacheRepository.save(t);
				WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElseThrow();
				ae.setStatut(StatutAppelEffet.REJETE);
				ae.setReponse(rejet.corps());
				ae.setTentatives(ae.getTentatives() + 1);
				ae.setTermineLe(clock.instant());
				appelEffetRepository.save(ae);
				enregistrerHistorique(inst.getId(), t.getId(), "EFFET_REJETE", appelantCompteId, nomActeur,
						ctx.pourNom(), Map.of("effet", ae.getEffet(), "code", rejet.code()), clock.instant());
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
		t.setDecision(versDecisionTache(req.action().trim()));
		t.setCommentaire(commentaire);
		t.setDecideParCompteId(appelantCompteId);
		t.setDecideParCollaborateurId(appelantCollabId);
		t.setDecideParNom(appelantNom);
		t.setPourCollaborateurId(pourCollabId);
		t.setPourNom(pourNom);
		t.setCleIdempotence(req.cleIdempotence());
		tacheRepository.saveAndFlush(t);

		WfAppelEffet ae = new WfAppelEffet();
		ae.setCleIdempotence(cleEffet);
		ae.setInstanceId(instanceId);
		ae.setTacheId(tacheId);
		ae.setEffet(effet);
		ae.setServiceProprietaire(serviceProprietaire);
		ae.setStatut(StatutAppelEffet.RESERVE);
		ae.setTentatives(0);
		ae.setCreeLe(maintenant);
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
			PlanAvancement plan,
			Instant echeanceSuivante,
			List<IntervenantResolu> candidatsReactivation,
			Map<String, List<UUID>> comptesInfos,
			boolean depuisReservee) {
		// Ordre strict des verrous : wf_instance PUIS wf_tache (§ 9.6.7)
		WfInstance inst = instanceRepository.findByIdForUpdate(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		WfTache t = tacheRepository.findByIdForUpdate(tacheId)
				.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));

		if (!depuisReservee) {
			if (req.cleIdempotence().equals(t.getCleIdempotence()) && t.getReponseDecision() != null) {
				return reconstruireReponse(t.getReponseDecision());
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
		}

		Instant maintenant = clock.instant();
		DecisionTache decisionEnum = versDecisionTache(action);
		t.setStatut(StatutTache.TRAITEE);
		t.setDecision(decisionEnum);
		t.setCommentaire(commentaire);
		t.setDecideParCompteId(appelantCompteId);
		t.setDecideParCollaborateurId(appelantCollabId);
		t.setDecideParNom(appelantNom);
		t.setPourCollaborateurId(pourCollabId);
		t.setPourNom(pourNom);
		t.setDecideLe(maintenant);
		t.setCleIdempotence(req.cleIdempotence());
		tacheRepository.saveAndFlush(t);

		enregistrerHistorique(inst.getId(), t.getId(), "DECISION", appelantCompteId, appelantNom, pourNom,
				Map.of("action", action, "decision", decisionEnum.name(), "commentaire", commentaire), maintenant);

		UUID evId = UUID.randomUUID();
		EtapeCouranteDto etapeCouranteDto = null;

		if ("RENVOYER".equals(action)) {
			// D-W10 : incrémenter nb_renvois, annuler les éventuelles autres tâches ouvertes de l'étape, créer tâche COMPLEMENT
			WfEtapeExecution ee = t.getEtapeExecution();
			ee.setNbRenvois(ee.getNbRenvois() + 1);
			etapeExecutionRepository.save(ee);

			for (WfTache autre : tacheRepository.findByEtapeExecutionId(ee.getId())) {
				if (!autre.getId().equals(t.getId()) && autre.getStatut() == StatutTache.A_TRAITER) {
					autre.setStatut(StatutTache.ANNULEE);
					tacheRepository.save(autre);
				}
			}

			WfTache comp = new WfTache();
			comp.setInstance(inst);
			comp.setEtapeExecution(ee);
			comp.setType(TypeTache.COMPLEMENT);
			comp.setLibelle("Complément demandé : " + t.getLibelle());
			comp.setStatut(StatutTache.A_TRAITER);
			comp.setObligatoire(true);
			comp.setEcheanceLe(echeanceSuivante);
			comp = tacheRepository.saveAndFlush(comp);

			WfTacheCandidat candDemandeur = new WfTacheCandidat();
			candDemandeur.setId(new WfTacheCandidatId(comp.getId(), inst.getDemandeurCollaborateurId()));
			candDemandeur.setCompteId(inst.getDemandeurCompteId());
			candDemandeur.setNom(inst.getDemandeurNom());
			candDemandeur.setOrigine(OrigineCandidat.TITULAIRE);
			candDemandeur.setAjouteLe(maintenant);
			candidatRepository.save(candDemandeur);

			if (inst.getDemandeurCompteId() != null) {
				String texte = TextesNotificationWorkflow.renvoyee(typeCatalogue.libelle(), commentaire, inst.getObjetNumero());
				outboxService.publierNotification(evId, inst.getDemandeurCompteId().toString(), "WEBSOCKET",
						"WF_DECISION", "Complément demandé", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
			outboxService.publierEvenementWorkflow(evId, "TACHE_TRAITEE", inst, ee.getEtapeCode(), comp.getLibelle(), echeanceSuivante);
			etapeCouranteDto = new EtapeCouranteDto(ee.getEtapeCode(), comp.getLibelle(), echeanceSuivante, "Demandeur");

		} else if (t.getType() == TypeTache.COMPLEMENT) {
			// Réactivation de l'étape (D-W10)
			WfEtapeExecution ee = t.getEtapeExecution();
			WfTache nouvelle = new WfTache();
			nouvelle.setInstance(inst);
			nouvelle.setEtapeExecution(ee);
			nouvelle.setType(TypeTache.VALIDATION);
			nouvelle.setLibelle(ee.getEtapeCode());
			nouvelle.setStatut(StatutTache.A_TRAITER);
			nouvelle.setObligatoire(true);
			nouvelle.setEcheanceLe(echeanceSuivante);
			nouvelle = tacheRepository.saveAndFlush(nouvelle);

			if (candidatsReactivation != null) {
				for (IntervenantResolu cand : candidatsReactivation) {
					WfTacheCandidat c = new WfTacheCandidat();
					c.setId(new WfTacheCandidatId(nouvelle.getId(), cand.collaborateurId()));
					c.setCompteId(cand.compteId());
					c.setNom(cand.nom());
					c.setOrigine(OrigineCandidat.TITULAIRE);
					c.setAjouteLe(maintenant);
					candidatRepository.save(c);
					String texte = TextesNotificationWorkflow.tacheCreee(
							typeCatalogue.libelle(), inst.getDemandeurNom(), nouvelle.getLibelle(), echeanceSuivante, inst.getObjetNumero());
					outboxService.publierNotification(evId, cand.compteId().toString(), "WEBSOCKET",
							"WF_TACHE_CREEE", "Complément fourni", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}
			outboxService.publierEvenementWorkflow(evId, "TACHE_CREEE", inst, ee.getEtapeCode(), nouvelle.getLibelle(), echeanceSuivante);
			etapeCouranteDto = new EtapeCouranteDto(ee.getEtapeCode(), nouvelle.getLibelle(), echeanceSuivante, null);

		} else if (plan == null) {
			// Mode TOUS ou TACHES_PARALLELES : l'étape n'est pas encore terminée (d'autres tâches obligatoires restent ouvertes)
			WfEtapeExecution ee = t.getEtapeExecution();
			outboxService.publierEvenementWorkflow(evId, "TACHE_TRAITEE", inst, ee.getEtapeCode(), t.getLibelle(), t.getEcheanceLe());
			etapeCouranteDto = new EtapeCouranteDto(ee.getEtapeCode(), t.getLibelle(), t.getEcheanceLe(), null);

		} else {
			// L'étape est terminée : annuler les éventuelles autres tâches ouvertes de cette étape (ex. refus en mode TOUS)
			WfEtapeExecution ee = t.getEtapeExecution();
			for (WfTache autre : tacheRepository.findByEtapeExecutionId(ee.getId())) {
				if (!autre.getId().equals(t.getId()) && autre.getStatut() == StatutTache.A_TRAITER) {
					autre.setStatut(StatutTache.ANNULEE);
					tacheRepository.save(autre);
				}
			}
			ee.setStatut(StatutEtapeExecution.TERMINEE);
			ee.setResultat("REFUSER".equals(action) ? ResultatEtapeExecution.REFUSEE
					: ("FAIT".equals(action) ? ResultatEtapeExecution.FAITE : ResultatEtapeExecution.APPROUVEE));
			ee.setTermineeLe(maintenant);
			etapeExecutionRepository.save(ee);

			int prochainOrdre = ee.getOrdre() + 1;
			for (EtapeSautee saut : plan.etapesSautees()) {
				WfEtapeExecution eSaut = new WfEtapeExecution();
				eSaut.setInstance(inst);
				eSaut.setEtapeCode(saut.code());
				eSaut.setOrdre(prochainOrdre++);
				eSaut.setStatut(StatutEtapeExecution.SAUTEE);
				eSaut.setRaisonSaut(saut.raison());
				eSaut.setActiveLe(maintenant);
				eSaut.setTermineeLe(maintenant);
				etapeExecutionRepository.save(eSaut);
				enregistrerHistorique(inst.getId(), null, "ETAPE_SAUTEE", null, "Système", null,
						Map.of("etape_code", saut.code(), "raison", saut.raison().name()), maintenant);
			}

			for (EtapeInformation info : plan.etapesInformation()) {
				WfEtapeExecution eInfo = new WfEtapeExecution();
				eInfo.setInstance(inst);
				eInfo.setEtapeCode(info.code());
				eInfo.setOrdre(prochainOrdre++);
				eInfo.setStatut(StatutEtapeExecution.TERMINEE);
				eInfo.setActiveLe(maintenant);
				eInfo.setTermineeLe(maintenant);
				etapeExecutionRepository.save(eInfo);
				UUID evInfo = UUID.randomUUID();
				String texte = TextesNotificationWorkflow.information(
						info.libelle(), typeCatalogue.libelle(), inst.getDemandeurNom(), inst.getObjetNumero());
				for (UUID compteId : comptesInfos.getOrDefault(info.code(), List.of())) {
					outboxService.publierNotification(evInfo, compteId.toString(), info.canal(), "WF_INFORMATION",
							info.libelle(), texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}

			if (plan.estTerminal()) {
				inst.setStatut(plan.statutFinal());
				inst.setEtapeCouranteCode(null);
				inst.setTermineLe(maintenant);
				if (plan.statutFinal() == StatutInstance.REFUSEE) {
					inst.setResultatMotif(commentaire);
				}
				instanceRepository.save(inst);

				if (inst.getDemandeurCompteId() != null) {
					String texte = plan.statutFinal() == StatutInstance.APPROUVEE
							? TextesNotificationWorkflow.approuvee(typeCatalogue.libelle(), inst.getObjetNumero())
							: TextesNotificationWorkflow.refusee(typeCatalogue.libelle(), appelantNom, commentaire, inst.getObjetNumero());
					String sujet = plan.statutFinal() == StatutInstance.APPROUVEE ? "Demande approuvée" : "Demande refusée";
					outboxService.publierNotification(evId, inst.getDemandeurCompteId().toString(), "WEBSOCKET",
							"WF_FIN", sujet, texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
				outboxService.publierEvenementWorkflow(evId, "INSTANCE_TERMINEE", inst, null, null, null);
				enregistrerHistorique(inst.getId(), t.getId(), "INSTANCE_TERMINEE", appelantCompteId, appelantNom,
						pourNom, Map.of("statut", inst.getStatut().name()), maintenant);
			} else {
				EtapeActivee activee = plan.etapeSuivante();
				EtapeDefinition etapeDef = activee.etape();
				inst.setStatut(activee.bloquee() ? StatutInstance.BLOQUEE : StatutInstance.EN_COURS);
				inst.setEtapeCouranteCode(etapeDef.code());
				instanceRepository.save(inst);

				WfEtapeExecution eSuiv = new WfEtapeExecution();
				eSuiv.setInstance(inst);
				eSuiv.setEtapeCode(etapeDef.code());
				eSuiv.setOrdre(prochainOrdre);
				eSuiv.setStatut(StatutEtapeExecution.ACTIVE);
				eSuiv.setActiveLe(maintenant);
				eSuiv = etapeExecutionRepository.saveAndFlush(eSuiv);

				String canal = etapeDef.notifications() != null ? etapeDef.notifications().canal() : "WEBSOCKET";
				for (TacheACreer tc : activee.taches()) {
					WfTache nt = new WfTache();
					nt.setInstance(inst);
					nt.setEtapeExecution(eSuiv);
					nt.setCodeTache(tc.codeTache());
					nt.setType(tc.type());
					nt.setLibelle(tc.libelle());
					nt.setStatut(StatutTache.A_TRAITER);
					nt.setObligatoire(tc.obligatoire());
					nt.setEcheanceLe(echeanceSuivante);
					nt = tacheRepository.saveAndFlush(nt);

					for (IntervenantResolu cand : tc.candidats()) {
						WfTacheCandidat c = new WfTacheCandidat();
						c.setId(new WfTacheCandidatId(nt.getId(), cand.collaborateurId()));
						c.setCompteId(cand.compteId());
						c.setNom(cand.nom());
						c.setOrigine(OrigineCandidat.TITULAIRE);
						c.setAjouteLe(maintenant);
						candidatRepository.save(c);

						String texte = TextesNotificationWorkflow.tacheCreee(
								typeCatalogue.libelle(), inst.getDemandeurNom(), tc.libelle(), echeanceSuivante, inst.getObjetNumero());
						outboxService.publierNotification(evId, cand.compteId().toString(), canal, "WF_TACHE_CREEE",
								"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
					}
				}

				if (inst.getDemandeurCompteId() != null && "APPROUVER".equals(action)) {
					String texteDem = TextesNotificationWorkflow.etapeApprouvee(
							typeCatalogue.libelle(), appelantNom, t.getLibelle(), etapeDef.libelle(), inst.getObjetNumero());
					outboxService.publierNotification(evId, inst.getDemandeurCompteId().toString(), "WEBSOCKET",
							"WF_DECISION", "Étape validée", texteDem, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}

				if (activee.bloquee()) {
					String texteBloque = TextesNotificationWorkflow.bloquee(
							etapeDef.libelle(), typeCatalogue.libelle(), inst.getDemandeurNom(), inst.getObjetNumero());
					outboxService.publierNotification(evId, "RH", "WEBSOCKET", "WF_BLOQUEE",
							"Workflow bloqué", texteBloque, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
					outboxService.publierEvenementWorkflow(evId, "INSTANCE_BLOQUEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceSuivante);
				} else {
					outboxService.publierEvenementWorkflow(evId, "TACHE_CREEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceSuivante);
				}

				String intitule = etapeDef.intervenants() != null
						? CatalogueTypesObjet.libelleRegle(etapeDef.intervenants().regle(), etapeDef.intervenants().params())
						: etapeDef.libelle();
				etapeCouranteDto = new EtapeCouranteDto(etapeDef.code(), etapeDef.libelle(), echeanceSuivante, intitule);
			}
		}

		DecisionResponse reponse = new DecisionResponse(
				t.getId(), decisionEnum.name(), new InstanceDecisionDto(inst.getStatut(), etapeCouranteDto));
		t.setReponseDecision(versMap(reponse));
		tacheRepository.save(t);
		return reponse;
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

	private static DecisionTache versDecisionTache(String action) {
		return switch (action) {
			case "APPROUVER" -> DecisionTache.APPROUVEE;
			case "REFUSER" -> DecisionTache.REFUSEE;
			case "RENVOYER" -> DecisionTache.RENVOYEE;
			case "FAIT" -> DecisionTache.FAITE;
			default -> throw WorkflowMetierException.unprocessable("ACTION_NON_AUTORISEE", "Action inconnue : " + action);
		};
	}

	private Map<String, TypeVariable> typesVariablesPour(String objetType) {
		Map<String, TypeVariable> map = new HashMap<>();
		for (VariableCatalogue vc : catalogue.variablesPour(objetType).values()) {
			map.put(vc.nom(), vc.type());
		}
		return map;
	}

	private static Map<String, Object> versMap(DecisionResponse rep) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("tache_id", rep.tacheId().toString());
		m.put("decision", rep.decision());
		Map<String, Object> inst = new LinkedHashMap<>();
		inst.put("statut", rep.instance().statut().name());
		if (rep.instance().etapeCourante() != null) {
			Map<String, Object> ec = new LinkedHashMap<>();
			ec.put("code", rep.instance().etapeCourante().code());
			ec.put("libelle", rep.instance().etapeCourante().libelle());
			ec.put("echeance_le", rep.instance().etapeCourante().echeanceLe() != null
					? rep.instance().etapeCourante().echeanceLe().toString() : null);
			ec.put("intitule_intervenants", rep.instance().etapeCourante().intituleIntervenants());
			inst.put("etape_courante", ec);
		}
		m.put("instance", inst);
		return m;
	}

	@SuppressWarnings("unchecked")
	private static DecisionResponse reconstruireReponse(Map<String, Object> m) {
		UUID tacheId = UUID.fromString(String.valueOf(m.get("tache_id")));
		String decision = String.valueOf(m.get("decision"));
		Map<String, Object> instMap = (Map<String, Object>) m.get("instance");
		StatutInstance statut = StatutInstance.valueOf(String.valueOf(instMap.get("statut")));
		EtapeCouranteDto ec = null;
		Object ecObj = instMap.get("etape_courante");
		if (ecObj instanceof Map<?, ?> ecMap) {
			String ech = ecMap.get("echeance_le") != null ? String.valueOf(ecMap.get("echeance_le")) : null;
			ec = new EtapeCouranteDto(
					ecMap.get("code") != null ? String.valueOf(ecMap.get("code")) : null,
					ecMap.get("libelle") != null ? String.valueOf(ecMap.get("libelle")) : null,
					ech != null ? Instant.parse(ech) : null,
					ecMap.get("intitule_intervenants") != null ? String.valueOf(ecMap.get("intitule_intervenants")) : null);
		}
		return new DecisionResponse(tacheId, decision, new InstanceDecisionDto(statut, ec));
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
