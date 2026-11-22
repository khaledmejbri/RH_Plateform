package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.AvancementService.PlanAvancement;
import com.hr.workflow.moteur.EffetClient.ActeurEffet;
import com.hr.workflow.moteur.EffetClient.RequeteEffet;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.moteur.PreparationDecisionService.PlanExecutionDecision;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfAppelEffetRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Planificateur des relances, échéances et escalades (D-W17 à D-W20, CA-10.4 à CA-10.7, CA-13.4, D-W4-03).
 * Sous {@link VerrouPlanificateur} et {@code FOR UPDATE SKIP LOCKED}, lots de 100.
 * <strong>Aucun appel HTTP dans une transaction</strong> (§ 9.6.1).
 * Ordre strict des verrous en écriture : {@code wf_instance} puis {@code wf_tache}.
 */
@Component
public class EcheanceScheduler {

	private static final Logger log = LoggerFactory.getLogger(EcheanceScheduler.class);
	private static final int TAILLE_LOT = 100;
	private static final String MOTIF_EXPIRATION = "Délai de décision dépassé";
	private static final String MOTIF_COMPLEMENT_EXPIRE = "Complément non fourni dans le délai";
	private static final String MOTIF_APPROBATION_AUTO = "Approuvée automatiquement : délai dépassé";

	private final WfTacheRepository tacheRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final WfAppelEffetRepository appelEffetRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final ResolutionIntervenantsService resolutionService;
	private final PreparationDecisionService preparationService;
	private final ApplicationDecisionService applicationService;
	private final WorkflowReferentielClient referentielClient;
	private final EffetClient effetClient;
	private final OutboxService outboxService;
	private final VerrouPlanificateur verrouPlanificateur;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final Clock clock;

	public EcheanceScheduler(
			WfTacheRepository tacheRepository,
			WfInstanceRepository instanceRepository,
			WfTacheCandidatRepository candidatRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			WfAppelEffetRepository appelEffetRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			PlanificationEcheanceService planificationEcheanceService,
			ResolutionIntervenantsService resolutionService,
			PreparationDecisionService preparationService,
			ApplicationDecisionService applicationService,
			WorkflowReferentielClient referentielClient,
			EffetClient effetClient,
			OutboxService outboxService,
			VerrouPlanificateur verrouPlanificateur,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.tacheRepository = tacheRepository;
		this.instanceRepository = instanceRepository;
		this.candidatRepository = candidatRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.appelEffetRepository = appelEffetRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.planificationEcheanceService = planificationEcheanceService;
		this.resolutionService = resolutionService;
		this.preparationService = preparationService;
		this.applicationService = applicationService;
		this.referentielClient = referentielClient;
		this.effetClient = effetClient;
		this.outboxService = outboxService;
		this.verrouPlanificateur = verrouPlanificateur;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(cron = "${workflow.echeances.cron:0 * * * * *}", zone = "Africa/Tunis")
	public void executer() {
		verrouPlanificateur.executerSiLibre("wf-echeances", this::traiterEcheances);
	}

	private record ContexteTacheDue(
			UUID tacheId,
			UUID instanceId,
			TypeTache typeTache,
			String libelleTache,
			String etapeCode,
			Instant dateActivation,
			Instant echeanceLe,
			Instant prochaineRelanceLe,
			Instant escaladeAFaireLe,
			Instant escaladeLe,
			Instant echeanceTraiteeLe,
			String objetType,
			UUID objetId,
			String objetNumero,
			String serviceProprietaire,
			UUID demandeurCollabId,
			UUID demandeurCompteId,
			String demandeurNom,
			Map<String, Object> variables,
			DefinitionWorkflow definition,
			EtapeDefinition etapeDef,
			TypeObjetCatalogue typeCatalogue,
			List<UUID> beneficiaires,
			List<UUID> approbateursPrecedents,
			boolean autreObligatoireOuverte
	) {
	}

	/** Visible pour les tests (y compris concurrence sans verrou consultatif). */
	public int traiterEcheances() {
		Instant maintenant = clock.instant();
		List<UUID> tacheIds = txWrite.execute(status -> tacheRepository.verrouillerLotEcheances(maintenant, TAILLE_LOT)
				.stream()
				.map(WfTache::getId)
				.toList());
		if (tacheIds == null || tacheIds.isEmpty()) {
			return 0;
		}

		int traites = 0;
		for (UUID tacheId : tacheIds) {
			try {
				if (traiterUneTache(tacheId, maintenant)) {
					traites++;
				}
			} catch (Exception ex) {
				log.error("Erreur lors du traitement de l'échéance de la tâche {} : {}", tacheId, ex.getMessage(), ex);
			}
		}
		return traites;
	}

	private boolean traiterUneTache(UUID tacheId, Instant maintenant) {
		ContexteTacheDue ctx = txReadOnly.execute(status -> chargerContexte(tacheId));
		if (ctx == null) {
			return false;
		}

		String actionEcheance = determinerActionEcheance(ctx);
		boolean relanceDue = ctx.prochaineRelanceLe() != null && !ctx.prochaineRelanceLe().isAfter(maintenant);
		boolean echeanceDue = ctx.echeanceLe() != null && !ctx.echeanceLe().isAfter(maintenant)
				&& ctx.echeanceTraiteeLe() == null;
		boolean escaladeDue = ctx.escaladeAFaireLe() != null && !ctx.escaladeAFaireLe().isAfter(maintenant)
				&& ctx.escaladeLe() == null;
		boolean finAutoImmediate = echeanceDue && ("COMPLEMENT_EXPIRER".equals(actionEcheance)
				|| "EXPIRER".equals(actionEcheance)
				|| "REFUSER_AUTO".equals(actionEcheance)
				|| "APPROUVER_AUTO".equals(actionEcheance));

		boolean modifie = false;

		// 1. Relance (si due et pas de fin automatique immédiate sur ce même cycle)
		if (relanceDue && !finAutoImmediate) {
			modifie |= executerRelance(ctx, maintenant);
		}

		// 2. Fin automatique à l'échéance (COMPLEMENT, EXPIRER, REFUSER_AUTO, APPROUVER_AUTO)
		if (finAutoImmediate) {
			if ("COMPLEMENT_EXPIRER".equals(actionEcheance) || "EXPIRER".equals(actionEcheance)) {
				return modifie | executerExpirationOuAnnulation(
						ctx, "COMPLEMENT_EXPIRER".equals(actionEcheance), maintenant);
			}
			return modifie | executerDecisionAuto(ctx, "APPROUVER_AUTO".equals(actionEcheance), maintenant);
		}

		// 3. Échéance atteinte pour RIEN ou ESCALADER
		if (echeanceDue) {
			if ("ESCALADER".equals(actionEcheance)) {
				ResultatMarquageEscalade res = marquerEcheanceEscalade(ctx, maintenant);
				modifie |= res.modifie();
				if (res.escaladePlanifiee() != null && !res.escaladePlanifiee().isAfter(maintenant) && ctx.escaladeLe() == null) {
					escaladeDue = true;
				}
			} else {
				modifie |= marquerEcheanceRien(ctx, actionEcheance, maintenant);
			}
		}

		// 4. Escalade due
		if (escaladeDue && "ESCALADER".equals(actionEcheance)) {
			modifie |= executerEscalade(ctx, maintenant);
		}

		return modifie;
	}

	private ContexteTacheDue chargerContexte(UUID tacheId) {
		WfTache t = tacheRepository.findByIdAvecDetails(tacheId).orElse(null);
		if (t == null || t.getStatut() != StatutTache.A_TRAITER) {
			return null;
		}
		WfInstance inst = t.getInstance();
		if (inst.getStatut().estTerminale()) {
			return null;
		}
		WfEtapeExecution ee = t.getEtapeExecution();
		DefinitionWorkflow def = codec.deserialiser(inst.getModeleVersion().getDefinition());
		EtapeDefinition etapeDef = def.etapes().stream()
				.filter(e -> e.code().equals(ee.getEtapeCode()))
				.findFirst().orElse(null);
		TypeObjetCatalogue tc = catalogue.findByObjetType(inst.getObjetType()).orElse(null);
		List<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
				.map(b -> b.getId().getCollaborateurId()).toList();
		List<UUID> approbateurs = tacheRepository.findApprobateursCollaborateurIds(inst.getId());
		boolean autreObligatoireOuverte = tacheRepository.findByEtapeExecutionId(ee.getId()).stream()
				.anyMatch(autre -> !autre.getId().equals(t.getId())
						&& autre.isObligatoire()
						&& (autre.getStatut() == StatutTache.A_TRAITER || autre.getStatut() == StatutTache.RESERVEE));

		return new ContexteTacheDue(
				t.getId(), inst.getId(), t.getType(), t.getLibelle(), ee.getEtapeCode(), ee.getActiveLe(),
				t.getEcheanceLe(), t.getProchaineRelanceLe(), t.getEscaladeAFaireLe(), t.getEscaladeLe(),
				t.getEcheanceTraiteeLe(), inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero(),
				inst.getServiceProprietaire(), inst.getDemandeurCollaborateurId(), inst.getDemandeurCompteId(),
				inst.getDemandeurNom(), inst.getVariables() != null ? inst.getVariables() : Map.of(),
				def, etapeDef, tc, benefs, approbateurs, autreObligatoireOuverte);
	}

	private static String determinerActionEcheance(ContexteTacheDue ctx) {
		if (ctx.typeTache() == TypeTache.COMPLEMENT) {
			return "COMPLEMENT_EXPIRER";
		}
		if (ctx.etapeDef() != null && ctx.etapeDef().aEcheance() != null && ctx.etapeDef().aEcheance().action() != null) {
			return ctx.etapeDef().aEcheance().action();
		}
		return "RIEN";
	}

	private boolean executerRelance(ContexteTacheDue ctx, Instant maintenant) {
		Boolean ok = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale()
					|| t.getStatut() != StatutTache.A_TRAITER
					|| t.getProchaineRelanceLe() == null
					|| t.getProchaineRelanceLe().isAfter(maintenant)) {
				return false;
			}

			int nouveauNb = t.getNbRelances() + 1;
			RelancesDefinition relDef = ctx.etapeDef() != null ? ctx.etapeDef().relances() : null;
			t.setNbRelances(nouveauNb);
			t.setProchaineRelanceLe(planificationEcheanceService.calculerProchaineRelanceApresEnvoi(
					maintenant, t.getEcheanceLe(), nouveauNb, relDef));
			tacheRepository.save(t);

			enregistrerHistorique(inst.getId(), t.getId(), "RELANCE",
					Map.of("nb_relances", nouveauNb), maintenant);

			String typeLibelle = ctx.typeCatalogue() != null ? ctx.typeCatalogue().libelle() : inst.getObjetType();
			String canal = (ctx.etapeDef() != null && ctx.etapeDef().notifications() != null
					&& ctx.etapeDef().notifications().canal() != null)
					? ctx.etapeDef().notifications().canal() : "WEBSOCKET";
			String texte = TextesNotificationWorkflow.relance(
					typeLibelle, inst.getDemandeurNom(), ctx.dateActivation(), inst.getObjetNumero());
			UUID evId = UUID.randomUUID();
			Set<UUID> comptes = new HashSet<>();
			for (WfTacheCandidat c : candidatRepository.findByIdTacheIdAndRetireLeIsNull(t.getId())) {
				if (c.getCompteId() != null && comptes.add(c.getCompteId())) {
					outboxService.publierNotification(
							evId, c.getCompteId().toString(), canal, "WF_RELANCE",
							"Rappel : demande à traiter", texte,
							inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}
			return true;
		});
		return Boolean.TRUE.equals(ok);
	}

	private boolean marquerEcheanceRien(ContexteTacheDue ctx, String actionEcheance, Instant maintenant) {
		Boolean ok = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale()
					|| t.getStatut() != StatutTache.A_TRAITER || t.getEcheanceTraiteeLe() != null) {
				return false;
			}
			t.setEcheanceTraiteeLe(maintenant);
			tacheRepository.save(t);
			enregistrerHistorique(inst.getId(), t.getId(), "ECHEANCE_ATTEINTE",
					Map.of("action", actionEcheance), maintenant);
			return true;
		});
		return Boolean.TRUE.equals(ok);
	}

	private record ResultatMarquageEscalade(boolean modifie, Instant escaladePlanifiee) {
	}

	private ResultatMarquageEscalade marquerEcheanceEscalade(ContexteTacheDue ctx, Instant maintenant) {
		Instant escaladeCalculee = ctx.escaladeAFaireLe();
		if (escaladeCalculee == null && ctx.etapeDef() != null) {
			LocalDate ref = LocalDate.now(clock);
			CalendrierSocieteResponse cal = referentielClient.calendrierSociete(ref, ref.plusDays(30));
			escaladeCalculee = planificationEcheanceService.calculerEscaladeDepuisEcheance(
					ctx.echeanceLe(), ctx.etapeDef().aEcheance(), cal);
		}
		final Instant escaladeFinale = escaladeCalculee;
		ResultatMarquageEscalade res = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale() || t.getStatut() != StatutTache.A_TRAITER) {
				return new ResultatMarquageEscalade(false, null);
			}
			boolean modifie = false;
			if (t.getEcheanceTraiteeLe() == null) {
				t.setEcheanceTraiteeLe(maintenant);
				enregistrerHistorique(inst.getId(), t.getId(), "ECHEANCE_ATTEINTE",
						Map.of("action", "ESCALADER"), maintenant);
				modifie = true;
			}
			if (t.getEscaladeAFaireLe() == null && escaladeFinale != null) {
				t.setEscaladeAFaireLe(escaladeFinale);
				modifie = true;
			}
			if (modifie) {
				tacheRepository.save(t);
			}
			return new ResultatMarquageEscalade(modifie, t.getEscaladeAFaireLe());
		});
		return res != null ? res : new ResultatMarquageEscalade(false, null);
	}

	private boolean executerEscalade(ContexteTacheDue ctx, Instant maintenant) {
		IntervenantsDefinition inter = (ctx.etapeDef() != null && ctx.etapeDef().aEcheance() != null
				&& ctx.etapeDef().aEcheance().intervenants() != null)
				? ctx.etapeDef().aEcheance().intervenants()
				: new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL");

		LocalDate ref = LocalDate.now(clock);
		CalendrierSocieteResponse cal = referentielClient.calendrierSociete(ref, ref);
		ZoneId fuseau = PlanificationEcheanceService.parserFuseau(cal != null ? cal.fuseau() : null);
		LocalDate aujourdhui = LocalDate.now(clock.withZone(fuseau));
		List<UUID> exclure = (ctx.definition() != null && ctx.definition().separationDesTaches()
				&& ctx.approbateursPrecedents() != null)
				? ctx.approbateursPrecedents() : List.of();

		// Résolution hors transaction + repli ROLE [RH, ADMIN] (D-W20) + invariant I1
		RegleAResoudre regle = new RegleAResoudre(
				"escalade",
				inter,
				new RepliSiVideDefinition("ROLE", List.of("RH", "ADMIN")),
				true);
		LotResolu lot = resolutionService.resoudre(
				aujourdhui, ctx.demandeurCollabId(), ctx.beneficiaires(), exclure, List.of(regle));
		ResultatRegleResolue res = lot != null && lot.resultats() != null ? lot.resultats().get("escalade") : null;
		List<IntervenantResolu> candidats = (res != null && !res.bloquer() && res.candidats() != null)
				? res.candidats() : List.of();

		Boolean ok = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale()
					|| t.getStatut() != StatutTache.A_TRAITER || t.getEscaladeLe() != null) {
				return false;
			}
			t.setEscaladeLe(maintenant);
			if (t.getEcheanceTraiteeLe() == null) {
				t.setEcheanceTraiteeLe(maintenant);
			}
			tacheRepository.save(t);

			Set<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
					.map(b -> b.getId().getCollaborateurId()).collect(Collectors.toSet());
			Map<UUID, WfTacheCandidat> existants = candidatRepository.findByIdTacheId(t.getId()).stream()
					.collect(Collectors.toMap(c -> c.getId().getCollaborateurId(), c -> c, (a, b) -> a));

			String typeLibelle = ctx.typeCatalogue() != null ? ctx.typeCatalogue().libelle() : inst.getObjetType();
			String canal = (ctx.etapeDef() != null && ctx.etapeDef().notifications() != null
					&& ctx.etapeDef().notifications().canal() != null)
					? ctx.etapeDef().notifications().canal() : "WEBSOCKET";
			UUID evId = UUID.randomUUID();
			List<String> nomsAjoutes = new ArrayList<>();

			for (IntervenantResolu cand : candidats) {
				if (cand.collaborateurId() == null || cand.compteId() == null) {
					continue;
				}
				// Invariant I1
				if (cand.collaborateurId().equals(inst.getDemandeurCollaborateurId())
						|| benefs.contains(cand.collaborateurId())) {
					continue;
				}
				WfTacheCandidat ex = existants.get(cand.collaborateurId());
				if (ex != null && ex.getRetireLe() == null) {
					// Titulaire ou candidat déjà actif conservé intact
					continue;
				}
				WfTacheCandidat cible = ex != null ? ex : new WfTacheCandidat();
				cible.setId(new WfTacheCandidatId(t.getId(), cand.collaborateurId()));
				cible.setCompteId(cand.compteId());
				cible.setNom(cand.nom());
				cible.setOrigine(OrigineCandidat.ESCALADE);
				cible.setPourCollaborateurId(null);
				cible.setPourNom(null);
				cible.setDelegationId(null);
				cible.setAjouteLe(maintenant);
				cible.setRetireLe(null);
				candidatRepository.save(cible);
				existants.put(cand.collaborateurId(), cible);
				nomsAjoutes.add(cand.nom());

				String texte = TextesNotificationWorkflow.escalade(
						typeLibelle, inst.getDemandeurNom(), t.getLibelle(), inst.getObjetNumero());
				outboxService.publierNotification(
						evId, cand.compteId().toString(), canal, "WF_ESCALADE",
						"Tâche escaladée", texte,
						inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}

			enregistrerHistorique(inst.getId(), t.getId(), "ESCALADEE",
					Map.of("nouveaux_candidats", nomsAjoutes), maintenant);
			outboxService.publierEvenementWorkflow(
					evId, "TACHE_ESCALADEE", inst, ctx.etapeCode(),
					ctx.etapeDef() != null ? ctx.etapeDef().libelle() : t.getLibelle(), t.getEcheanceLe());
			return true;
		});
		return Boolean.TRUE.equals(ok);
	}

	private boolean executerExpirationOuAnnulation(ContexteTacheDue ctx, boolean estComplement, Instant maintenant) {
		String effet = estComplement ? "ANNULER" : "EXPIRER";
		String motif = estComplement ? MOTIF_COMPLEMENT_EXPIRE : MOTIF_EXPIRATION;
		String cleEffet = "echeance:" + ctx.tacheId() + ":" + effet;

		boolean sansEffetChezProprietaire = ctx.typeCatalogue() == null || !ctx.typeCatalogue().supporteEffet(effet);
		if (sansEffetChezProprietaire) {
			Boolean ok = txWrite.execute(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
				WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
				if (inst == null || t == null || inst.getStatut().estTerminale()
						|| t.getStatut() != StatutTache.A_TRAITER || t.getEcheanceTraiteeLe() != null) {
					return false;
				}
				applicationService.finaliserExpirationOuAnnulationDansTx(
						inst, t, estComplement, motif, ctx.typeCatalogue(), maintenant);
				return true;
			});
			return Boolean.TRUE.equals(ok);
		}

		// Phase 1 (Tx courte) : réserver la tâche et la ligne wf_appel_effet
		UUID appelEffetId = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale()
					|| t.getStatut() != StatutTache.A_TRAITER || t.getEcheanceTraiteeLe() != null) {
				return null;
			}
			t.setStatut(StatutTache.RESERVEE);
			t.setCommentaire(motif);
			t.setDecideParCompteId(null);
			t.setDecideParCollaborateurId(null);
			t.setDecideParNom("Système");
			t.setCleIdempotence(cleEffet);
			tacheRepository.saveAndFlush(t);

			return creerOuReutiliserAppelEffet(inst.getId(), t.getId(), cleEffet, effet,
					inst.getServiceProprietaire(), maintenant);
		});
		if (appelEffetId == null) {
			return false;
		}

		// Phase 2 (HORS TRANSACTION) : appel APPLIQUER
		ResultatAppelEffet res = effetClient.appeler(
				ctx.serviceProprietaire(),
				ctx.typeCatalogue().cheminEffet(),
				new RequeteEffet(cleEffet, "APPLIQUER", ctx.objetType(), ctx.objetId(), effet,
						new ActeurEffet(null, null, "Système", null), motif, ctx.instanceId(), ctx.libelleTache()));
		List<UUID> comptesRhAdmin = (res instanceof ResultatAppelEffet.RejetMetier)
				? resoudreComptesRhAdminHorsTx() : List.of();

		// Phase 3 (Tx courte) : appliquer l'issue
		txWrite.executeWithoutResult(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElse(null);
			if (inst == null || t == null || ae == null) {
				return;
			}
			enregistrerIssueEffet(ae, inst, t, res, comptesRhAdmin, ctx.typeCatalogue(),
					ctx.etapeDef() != null ? ctx.etapeDef().libelle() : ctx.libelleTache(),
					maintenant, () -> applicationService.finaliserExpirationOuAnnulationDansTx(
							inst, t, estComplement, motif, ctx.typeCatalogue(), maintenant));
		});
		return true;
	}

	private boolean executerDecisionAuto(ContexteTacheDue ctx, boolean estApprobation, Instant maintenant) {
		// Garde défensive I9 : APPROUVER_AUTO interdit sur type à validation humaine obligatoire
		if (estApprobation && ctx.typeCatalogue() != null && ctx.typeCatalogue().validationHumaineObligatoire()) {
			log.error("Tentative d'APPROUVER_AUTO interdite (I9) sur {} (tâche {}) : passage en BLOQUEE.",
					ctx.objetType(), ctx.tacheId());
			List<UUID> comptesRhAdmin = resoudreComptesRhAdminHorsTx();
			Boolean ok = txWrite.execute(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
				WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
				if (inst == null || t == null || inst.getStatut().estTerminale()
						|| t.getStatut() != StatutTache.A_TRAITER) {
					return false;
				}
				applicationService.bloquerSurRejetSystemeDansTx(
						inst, t, "APPROUVER", "AUTO_APPROBATION_INTERDITE",
						"Approbation automatique interdite sur ce type d'objet (I9).",
						comptesRhAdmin, ctx.typeCatalogue(),
						ctx.etapeDef() != null ? ctx.etapeDef().libelle() : ctx.libelleTache(), maintenant);
				return true;
			});
			return Boolean.TRUE.equals(ok);
		}

		String action = estApprobation ? "APPROUVER" : "REFUSER";
		String motif = estApprobation ? MOTIF_APPROBATION_AUTO : MOTIF_EXPIRATION;
		String cleAction = "echeance:" + ctx.tacheId() + ":" + action;

		// Phase 1 (Tx courte) : réserver la tâche pour éviter tout doublon concurrent
		Boolean reserve = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale()
					|| t.getStatut() != StatutTache.A_TRAITER || t.getEcheanceTraiteeLe() != null) {
				return false;
			}
			t.setStatut(StatutTache.RESERVEE);
			t.setDecision(ApplicationDecisionService.versDecisionTache(action));
			t.setCommentaire(motif);
			t.setDecideParCompteId(null);
			t.setDecideParCollaborateurId(null);
			t.setDecideParNom("Système");
			t.setCleIdempotence(cleAction);
			tacheRepository.saveAndFlush(t);
			return true;
		});
		if (!Boolean.TRUE.equals(reserve)) {
			return false;
		}

		// Phase 2 (HORS TRANSACTION) : préparation du plan d'avancement
		boolean termineEtape = !estApprobation || !ctx.autreObligatoireOuverte();
		PlanExecutionDecision exec;
		try {
			exec = preparerDecisionHorsTx(ctx, action, termineEtape);
		} catch (Exception ex) {
			libererReservationSurErreurPreparation(ctx.instanceId(), ctx.tacheId());
			throw ex;
		}

		PlanAvancement plan = exec.plan();
		String effetTerminal = null;
		if (plan != null && plan.statutFinal() == StatutInstance.APPROUVEE) {
			effetTerminal = "APPROUVER";
		} else if (plan != null && plan.statutFinal() == StatutInstance.REFUSEE) {
			effetTerminal = "REFUSER";
		}

		// Phase 3a : sans effet terminal -> application atomique (avec rejeu unique si mode TOUS devenu fini)
		if (effetTerminal == null) {
			final PlanExecutionDecision execInitial = exec;
			boolean etapeDevenueFinie = Boolean.TRUE.equals(txWrite.execute(status -> {
				WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
				WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
				if (inst == null || t == null || inst.getStatut().estTerminale() || t.getStatut() != StatutTache.RESERVEE) {
					return false;
				}
				if (plan == null) {
					boolean autreOuverte = tacheRepository.findByEtapeExecutionId(t.getEtapeExecution().getId()).stream()
							.anyMatch(autre -> !autre.getId().equals(t.getId())
									&& autre.isObligatoire()
									&& (autre.getStatut() == StatutTache.A_TRAITER || autre.getStatut() == StatutTache.RESERVEE));
					if (!autreOuverte) {
						return true;
					}
				}
				t.setEcheanceTraiteeLe(maintenant);
				applicationService.appliquerDansTransaction(
						inst, t, cleAction, action, motif,
						null, null, "Système", null, null,
						ctx.typeCatalogue(), ctx.etapeDef(), execInitial);
				return false;
			}));
			if (!etapeDevenueFinie) {
				return true;
			}
			// Rejeu avec termineEtape = true
			PlanExecutionDecision execRejeu;
			try {
				execRejeu = preparerDecisionHorsTx(ctx, action, true);
			} catch (Exception ex) {
				libererReservationSurErreurPreparation(ctx.instanceId(), ctx.tacheId());
				throw ex;
			}
			PlanAvancement pRejeu = execRejeu.plan();
			if (pRejeu == null || !pRejeu.estTerminal()) {
				txWrite.executeWithoutResult(status -> {
					WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
					WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
					if (inst != null && t != null && !inst.getStatut().estTerminale() && t.getStatut() == StatutTache.RESERVEE) {
						t.setEcheanceTraiteeLe(maintenant);
						applicationService.appliquerDansTransaction(
								inst, t, cleAction, action, motif,
								null, null, "Système", null, null,
								ctx.typeCatalogue(), ctx.etapeDef(), execRejeu);
					}
				});
				return true;
			}
			exec = execRejeu;
			effetTerminal = pRejeu.statutFinal() == StatutInstance.APPROUVEE ? "APPROUVER" : "REFUSER";
		}

		// Phase 3b : avec effet terminal -> créer wf_appel_effet RESERVE, appeler APPLIQUER hors tx, puis appliquer l'issue
		final String effetFinal = effetTerminal;
		UUID appelEffetId = txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale() || t.getStatut() != StatutTache.RESERVEE) {
				return null;
			}
			return creerOuReutiliserAppelEffet(inst.getId(), t.getId(), cleAction, effetFinal,
					inst.getServiceProprietaire(), maintenant);
		});
		if (appelEffetId == null) {
			return false;
		}

		ResultatAppelEffet res = effetClient.appeler(
				ctx.serviceProprietaire(),
				ctx.typeCatalogue().cheminEffet(),
				new RequeteEffet(cleAction, "APPLIQUER", ctx.objetType(), ctx.objetId(), effetFinal,
						new ActeurEffet(null, null, "Système", null), motif, ctx.instanceId(), ctx.libelleTache()));
		List<UUID> comptesRhAdmin = (res instanceof ResultatAppelEffet.RejetMetier)
				? resoudreComptesRhAdminHorsTx() : List.of();

		final PlanExecutionDecision finalExec = exec;
		txWrite.executeWithoutResult(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(ctx.tacheId()).orElse(null);
			WfAppelEffet ae = appelEffetRepository.findById(appelEffetId).orElse(null);
			if (inst == null || t == null || ae == null) {
				return;
			}
			enregistrerIssueEffet(ae, inst, t, res, comptesRhAdmin, ctx.typeCatalogue(),
					ctx.etapeDef() != null ? ctx.etapeDef().libelle() : ctx.libelleTache(),
					maintenant, () -> {
						t.setEcheanceTraiteeLe(maintenant);
						applicationService.appliquerDansTransaction(
								inst, t, cleAction, action, motif,
								null, null, "Système", null, null,
								ctx.typeCatalogue(), ctx.etapeDef(), finalExec);
					});
		});
		return true;
	}

	private PlanExecutionDecision preparerDecisionHorsTx(ContexteTacheDue ctx, String action, boolean termineEtape) {
		return preparationService.preparer(
				action, ctx.typeTache(), ctx.etapeCode(), ctx.etapeDef(), ctx.definition(), ctx.objetType(),
				ctx.variables(), ctx.demandeurCollabId(), ctx.demandeurCompteId(), ctx.beneficiaires(),
				ctx.approbateursPrecedents(), null, termineEtape, List.of());
	}

	private void libererReservationSurErreurPreparation(UUID instanceId, UUID tacheId) {
		txWrite.executeWithoutResult(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(instanceId).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(tacheId).orElse(null);
			if (inst != null && t != null && !inst.getStatut().estTerminale() && t.getStatut() == StatutTache.RESERVEE) {
				t.setStatut(StatutTache.A_TRAITER);
				t.setDecision(null);
				t.setCommentaire(null);
				t.setDecideParNom(null);
				t.setCleIdempotence(null);
				tacheRepository.save(t);
			}
		});
	}

	private UUID creerOuReutiliserAppelEffet(
			UUID instanceId,
			UUID tacheId,
			String cleEffet,
			String effet,
			String serviceProprietaire,
			Instant maintenant) {
		WfAppelEffet ae = appelEffetRepository.findByCleIdempotence(cleEffet).orElse(null);
		if (ae != null) {
			if (ae.getStatut() != StatutAppelEffet.REJETE) {
				return null;
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

	private void enregistrerIssueEffet(
			WfAppelEffet ae,
			WfInstance inst,
			WfTache t,
			ResultatAppelEffet res,
			List<UUID> comptesRhAdmin,
			TypeObjetCatalogue typeCatalogue,
			String etapeLibelle,
			Instant maintenant,
			Runnable surSucces) {
		if (res instanceof ResultatAppelEffet.Succes succes) {
			ae.setStatut(StatutAppelEffet.APPLIQUE);
			ae.setReponse(succes.corps());
			ae.setTentatives(ae.getTentatives() + 1);
			ae.setTermineLe(maintenant);
			appelEffetRepository.save(ae);
			if (!inst.getStatut().estTerminale() && t.getStatut() == StatutTache.RESERVEE) {
				surSucces.run();
			}
			return;
		}
		if (res instanceof ResultatAppelEffet.RejetMetier rejet) {
			ae.setStatut(StatutAppelEffet.REJETE);
			ae.setReponse(rejet.corps());
			ae.setTentatives(ae.getTentatives() + 1);
			ae.setTermineLe(maintenant);
			appelEffetRepository.save(ae);
			if (!inst.getStatut().estTerminale() && t.getStatut() == StatutTache.RESERVEE) {
				applicationService.bloquerSurRejetSystemeDansTx(
						inst, t, ae.getEffet(), rejet.code(), rejet.message(),
						comptesRhAdmin, typeCatalogue, etapeLibelle, maintenant);
			}
			return;
		}
		ResultatAppelEffet.Incertain incertain = (ResultatAppelEffet.Incertain) res;
		ae.setStatut(StatutAppelEffet.INCERTAIN);
		ae.setTentatives(1);
		ae.setProchaineTentativeLe(maintenant.plus(1, ChronoUnit.MINUTES));
		ae.setReponse(Map.of("erreur", incertain.erreur()));
		appelEffetRepository.save(ae);
	}

	private List<UUID> resoudreComptesRhAdminHorsTx() {
		try {
			return resolutionService.resoudreComptesRoles(LocalDate.now(clock), List.of("RH", "ADMIN"));
		} catch (Exception ex) {
			log.warn("Impossible de résoudre les comptes RH/ADMIN lors d'un blocage automatique : {}", ex.getMessage());
			return List.of();
		}
	}

	private void enregistrerHistorique(
			UUID instanceId, UUID tacheId, String typeEvenement,
			Map<String, Object> details, Instant horodatage) {
		WfHistorique h = new WfHistorique();
		h.setInstanceId(instanceId);
		h.setTacheId(tacheId);
		h.setTypeEvenement(typeEvenement);
		h.setActeurNom("Système");
		h.setDetails(details);
		h.setHorodatage(horodatage);
		historiqueRepository.save(h);
	}
}
