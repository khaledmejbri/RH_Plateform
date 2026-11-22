package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.moteur.AvancementService.EtapeInformation;
import com.hr.workflow.moteur.AvancementService.PlanAvancement;
import com.hr.workflow.moteur.DelegationResolutionService.CandidatSupplementaire;
import com.hr.workflow.moteur.PlanificationEcheanceService.PlanificationTache;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calcul et résolution hors transaction pour l'exécution d'une décision (§ 9.6.4, D-W10, D-W16, D-W27).
 * Partagé entre {@link DecisionService} et {@link EffetRepriseScheduler}.
 * <strong>Aucun appel HTTP dans une transaction.</strong>
 */
@Service
public class PreparationDecisionService {

	private final CatalogueTypesObjet catalogue;
	private final ResolutionIntervenantsService resolutionService;
	private final WorkflowReferentielClient referentielClient;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final DelegationResolutionService delegationResolutionService;
	private final AvancementService avancementService;
	private final Clock clock;

	public PreparationDecisionService(
			CatalogueTypesObjet catalogue,
			ResolutionIntervenantsService resolutionService,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			AvancementService avancementService,
			Clock clock) {
		this(catalogue, resolutionService, referentielClient, planificationEcheanceService,
				null, avancementService, clock);
	}

	@Autowired
	public PreparationDecisionService(
			CatalogueTypesObjet catalogue,
			ResolutionIntervenantsService resolutionService,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			DelegationResolutionService delegationResolutionService,
			AvancementService avancementService,
			Clock clock) {
		this.catalogue = catalogue;
		this.resolutionService = resolutionService;
		this.referentielClient = referentielClient;
		this.planificationEcheanceService = planificationEcheanceService;
		this.delegationResolutionService = delegationResolutionService;
		this.avancementService = avancementService;
		this.clock = clock;
	}

	public record PlanExecutionDecision(
			PlanAvancement plan,
			PlanificationTache planificationSuivante,
			PlanAvancement planReactivation,
			Map<String, List<UUID>> comptesInfos,
			List<UUID> comptesNotifActivation,
			List<UUID> comptesNotifFin,
			List<UUID> comptesBloquee,
			List<List<CandidatSupplementaire>> deleguesParTache
	) {
		public Instant echeanceSuivante() {
			return planificationSuivante != null ? planificationSuivante.echeanceLe() : null;
		}

		public static PlanExecutionDecision vide() {
			return new PlanExecutionDecision(null, PlanificationTache.VIDE, null,
					Map.of(), List.of(), List.of(), List.of(), List.of());
		}
	}

	public PlanExecutionDecision preparer(
			String action,
			TypeTache typeTache,
			String etapeCode,
			EtapeDefinition etapeDef,
			DefinitionWorkflow definition,
			String objetType,
			Map<String, Object> variables,
			UUID demandeurCollaborateurId,
			UUID demandeurCompteId,
			List<UUID> beneficiaires,
			List<UUID> approbateursPrecedents,
			UUID candidatCollaborateurId,
			boolean termineEtape,
			List<IntervenantResolu> candidatsEtapeTerminee) {
		Instant maintenant = clock.instant();
		LocalDate aujourdhui = LocalDate.now(clock);
		Map<String, Object> vars = variables != null ? variables : Map.of();
		Map<String, TypeVariable> typesVars = typesVariablesPour(objetType);

		if ("RENVOYER".equals(action)) {
			CalendrierSocieteResponse calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(30));
			Instant echeanceComplement = planificationEcheanceService.calculerEcheanceComplement(maintenant, calendrier);
			return new PlanExecutionDecision(null, new PlanificationTache(echeanceComplement, null, null),
					null, Map.of(), List.of(), List.of(), List.of(), List.of());
		}

		if (typeTache == TypeTache.COMPLEMENT) {
			List<UUID> exclure = definition.separationDesTaches() ? approbateursPrecedents : List.of();
			List<RegleAResoudre> regles = DemarrageService.collecterRegles(definition);
			LotResolu lot = resolutionService.resoudre(aujourdhui, demandeurCollaborateurId, beneficiaires, exclure, regles);
			PlanAvancement planReactiv = avancementService.calculerReactivation(
					definition, etapeCode, vars, typesVars, lot.resultats());
			return completerPlan(planReactiv, null, objetType, demandeurCollaborateurId, demandeurCompteId,
					beneficiaires, maintenant, aujourdhui, vars, List.of(), true);
		}

		if (!termineEtape) {
			return PlanExecutionDecision.vide();
		}

		List<UUID> exclure = new ArrayList<>();
		if (definition.separationDesTaches()) {
			if (approbateursPrecedents != null) {
				exclure.addAll(approbateursPrecedents);
			}
			if ("APPROUVER".equals(action) && candidatCollaborateurId != null) {
				exclure.add(candidatCollaborateurId);
			}
		}
		List<RegleAResoudre> regles = DemarrageService.collecterRegles(definition);
		LotResolu lot = resolutionService.resoudre(aujourdhui, demandeurCollaborateurId, beneficiaires, exclure, regles);
		String resultatEtape = "REFUSER".equals(action) ? "REFUSEE"
				: ("FAIT".equals(action) ? "FAITE" : "APPROUVEE");
		PlanAvancement plan = avancementService.calculerApresEtape(
				definition, etapeCode, resultatEtape, vars, typesVars, lot.resultats());

		return completerPlan(plan, etapeDef, objetType, demandeurCollaborateurId, demandeurCompteId,
				beneficiaires, maintenant, aujourdhui, vars, candidatsEtapeTerminee, false);
	}

	private PlanExecutionDecision completerPlan(
			PlanAvancement plan,
			EtapeDefinition etapeTermineeDef,
			String objetType,
			UUID demandeurCollaborateurId,
			UUID demandeurCompteId,
			List<UUID> beneficiaires,
			Instant maintenant,
			LocalDate aujourdhui,
			Map<String, Object> vars,
			List<IntervenantResolu> candidatsEtapeTerminee,
			boolean estReactivation) {
		PlanificationTache planifSuivante = PlanificationTache.VIDE;
		List<List<CandidatSupplementaire>> deleguesParTache = List.of();
		List<UUID> comptesNotifActivation = List.of();
		List<UUID> comptesBloquee = List.of();
		Map<String, List<UUID>> comptesInfos = Map.of();
		List<UUID> comptesNotifFin = List.of();

		if (plan.etapeSuivante() != null && !plan.etapeSuivante().bloquee()) {
			CalendrierSocieteResponse calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));
			EtapeDefinition etapeSuivDef = plan.etapeSuivante().etape();
			planifSuivante = planificationEcheanceService.calculerPlanification(
					maintenant, etapeSuivDef.delai(), etapeSuivDef.relances(), etapeSuivDef.aEcheance(),
					calendrier, vars);
			if (delegationResolutionService != null) {
				LocalDate aujourdhuiSociete = LocalDate.now(
						clock.withZone(PlanificationEcheanceService.parserFuseau(calendrier.fuseau())));
				deleguesParTache = delegationResolutionService.resoudreDeleguesPourTaches(
						aujourdhuiSociete, objetType, demandeurCollaborateurId, beneficiaires,
						plan.etapeSuivante().taches(), calendrier.delegationAutoConges());
			}
			if (etapeSuivDef.notifications() != null
					&& etapeSuivDef.notifications().aLActivation() != null
					&& !etapeSuivDef.notifications().aLActivation().isEmpty()) {
				List<IntervenantResolu> tousCandidats = new ArrayList<>();
				plan.etapeSuivante().taches().forEach(t -> tousCandidats.addAll(t.candidats()));
				for (List<CandidatSupplementaire> ds : deleguesParTache) {
					for (CandidatSupplementaire d : ds) {
						tousCandidats.add(new IntervenantResolu(d.collaborateurId(), d.compteId(), d.nom(), null));
					}
				}
				comptesNotifActivation = resolutionService.resoudreComptesPublics(
						aujourdhui, etapeSuivDef.notifications().aLActivation(),
						demandeurCollaborateurId, demandeurCompteId, tousCandidats);
			}
		} else if (plan.etapeSuivante() != null && plan.etapeSuivante().bloquee()) {
			comptesBloquee = resolutionService.resoudreComptesRoles(aujourdhui, List.of("RH", "ADMIN"));
		}

		if (!plan.etapesInformation().isEmpty()) {
			Map<String, List<UUID>> mapInfos = new HashMap<>();
			for (EtapeInformation info : plan.etapesInformation()) {
				mapInfos.put(info.code(), resolutionService.resoudreComptesPublics(
						aujourdhui, info.publicDestinataires(), demandeurCollaborateurId,
						demandeurCompteId, List.of()));
			}
			comptesInfos = mapInfos;
		}

		if (etapeTermineeDef != null
				&& etapeTermineeDef.notifications() != null
				&& etapeTermineeDef.notifications().aLaFin() != null
				&& !etapeTermineeDef.notifications().aLaFin().isEmpty()) {
			comptesNotifFin = resolutionService.resoudreComptesPublics(
					aujourdhui, etapeTermineeDef.notifications().aLaFin(),
					demandeurCollaborateurId, demandeurCompteId,
					candidatsEtapeTerminee != null ? candidatsEtapeTerminee : List.of());
		}

		return estReactivation
				? new PlanExecutionDecision(null, planifSuivante, plan, comptesInfos, comptesNotifActivation,
						comptesNotifFin, comptesBloquee, deleguesParTache)
				: new PlanExecutionDecision(plan, planifSuivante, null, comptesInfos, comptesNotifActivation,
						comptesNotifFin, comptesBloquee, deleguesParTache);
	}

	private Map<String, TypeVariable> typesVariablesPour(String objetType) {
		Map<String, TypeVariable> map = new HashMap<>();
		for (VariableCatalogue vc : catalogue.variablesPour(objetType).values()) {
			map.put(vc.nom(), vc.type());
		}
		return map;
	}
}
