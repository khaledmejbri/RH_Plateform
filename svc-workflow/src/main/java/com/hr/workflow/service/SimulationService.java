package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.ErreurDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EvaluateurCondition;
import com.hr.workflow.dto.AdminWorkflowDtos.CandidatSimule;
import com.hr.workflow.dto.AdminWorkflowDtos.EtapeSimulee;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulerVersionRequest;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.moteur.ResolutionIntervenantsService;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simulation sans écriture d'une version de modèle (D-W06, CA-06.7).
 * Aucun appel HTTP dans une transaction.
 */
@Service
public class SimulationService {

	private final WfModeleVersionRepository versionRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final DefinitionValidateur validateur;
	private final ResolutionIntervenantsService resolutionService;
	private final WorkflowReferentielClient referentielClient;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final TransactionTemplate txReadOnly;
	private final EvaluateurCondition evaluateur = new EvaluateurCondition();
	private final Clock clock;

	public SimulationService(
			WfModeleVersionRepository versionRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			DefinitionValidateur validateur,
			ResolutionIntervenantsService resolutionService,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.versionRepository = versionRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.validateur = validateur;
		this.resolutionService = resolutionService;
		this.referentielClient = referentielClient;
		this.planificationEcheanceService = planificationEcheanceService;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.clock = clock;
	}

	public SimulationResponse simuler(UUID versionId, SimulerVersionRequest req) {
		record VersionChargee(String objetType, DefinitionWorkflow def) {
		}
		VersionChargee chargee = txReadOnly.execute(status -> {
			WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
					.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
			DefinitionWorkflow def = codec.deserialiser(v.getDefinition());
			return new VersionChargee(v.getModele().getObjetType(), def);
		});

		List<ErreurDefinition> erreurs = validateur.valider(chargee.def(), chargee.objetType());
		if (!validateur.estValide(erreurs)) {
			throw WorkflowMetierException.definitionInvalide(erreurs);
		}

		// Préparation des règles à résoudre en un seul lot (hors transaction)
		List<RegleAResoudre> regles = new ArrayList<>();
		for (EtapeDefinition etape : chargee.def().etapes()) {
			if (("VALIDATION".equals(etape.type()) || "TACHE".equals(etape.type())) && etape.intervenants() != null) {
				regles.add(new RegleAResoudre(
						etape.code(), etape.intervenants(), etape.repliSiVide(), "VALIDATION".equals(etape.type())));
			} else if ("TACHES_PARALLELES".equals(etape.type()) && etape.taches() != null) {
				for (EtapeDefinition.TacheParalleleDefinition t : etape.taches()) {
					regles.add(new RegleAResoudre(
							etape.code() + ":" + t.code(), t.intervenants(), t.repliSiVide(), false));
				}
			}
		}

		Instant maintenant = clock.instant();
		LocalDate aujourdhui = LocalDate.now(clock);
		LotResolu lot = resolutionService.resoudre(
				aujourdhui, req.demandeurId(), req.beneficiaires(), List.of(), regles);
		CalendrierSocieteResponse calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));

		// Fusion des variables utilisateur + variables communes du contexte demandeur
		Map<String, Object> variables = new HashMap<>();
		if (req.variables() != null) {
			variables.putAll(req.variables());
		}
		ContexteDemandeur ctx = lot.contexteDemandeur();
		if (ctx != null) {
			variables.putIfAbsent("demandeur_profil_acces", ctx.profilAcces());
			variables.putIfAbsent("demandeur_type_noeud", ctx.typeNoeud());
			variables.putIfAbsent("demandeur_anciennete_mois", ctx.ancienneteMois());
			variables.putIfAbsent("demandeur_est_manager", ctx.estManager());
		}

		Map<String, TypeVariable> typesVars = new HashMap<>();
		for (VariableCatalogue vc : catalogue.variablesPour(chargee.objetType()).values()) {
			typesVars.put(vc.nom(), vc.type());
		}

		List<EtapeSimulee> etapesSimulees = new ArrayList<>();
		Instant curseurTemps = maintenant;

		for (EtapeDefinition etape : chargee.def().etapes()) {
			if (etape.condition() != null && !evaluateur.evaluer(etape.condition(), variables, typesVars)) {
				etapesSimulees.add(new EtapeSimulee(
						etape.code(), etape.libelle(), "SAUTEE", "Condition non remplie", List.of(), null));
				continue;
			}

			if ("INFORMATION".equals(etape.type())) {
				etapesSimulees.add(new EtapeSimulee(
						etape.code(), etape.libelle(), "ACTIVEE", "Notification d'information", List.of(), null));
				continue;
			}

			if ("TACHES_PARALLELES".equals(etape.type())) {
				List<CandidatSimule> cands = new ArrayList<>();
				if (etape.taches() != null) {
					for (EtapeDefinition.TacheParalleleDefinition t : etape.taches()) {
						ResultatRegleResolue r = lot.resultats().get(etape.code() + ":" + t.code());
						if (r != null && r.candidats() != null) {
							r.candidats().forEach(c -> cands.add(new CandidatSimule(c.nom(), c.intitule(), "TITULAIRE")));
						}
					}
				}
				Instant ech = planificationEcheanceService.calculerEcheance(curseurTemps, etape.delai(), calendrier, variables);
				etapesSimulees.add(new EtapeSimulee(etape.code(), etape.libelle(), "ACTIVEE", null, cands, ech));
				if (ech != null) {
					curseurTemps = ech;
				}
				continue;
			}

			ResultatRegleResolue res = lot.resultats().get(etape.code());
			if (res == null || res.passer()) {
				etapesSimulees.add(new EtapeSimulee(
						etape.code(), etape.libelle(), "SAUTEE", "Aucun intervenant", List.of(), null));
				continue;
			}
			if (res.bloquer()) {
				etapesSimulees.add(new EtapeSimulee(
						etape.code(), etape.libelle(), "ACTIVEE", "Bloquee : aucun intervenant", List.of(), null));
				continue;
			}
			// Mode TOUS : au-delà de 10 candidats, définition refusée à la simulation (D-W08)
			if ("VALIDATION".equals(etape.type())
					&& "TOUS".equals(etape.intervenants().modeEffectif())
					&& res.candidats().size() > 10) {
				throw WorkflowMetierException.badRequest("DEFINITION_INVALIDE",
						"L'étape '" + etape.code() + "' en mode TOUS résout plus de 10 candidats (" + res.candidats().size() + ").");
			}

			List<CandidatSimule> candidats = res.candidats().stream()
					.map(c -> new CandidatSimule(c.nom(), c.intitule(), "TITULAIRE"))
					.toList();
			Instant ech = planificationEcheanceService.calculerEcheance(curseurTemps, etape.delai(), calendrier, variables);
			etapesSimulees.add(new EtapeSimulee(etape.code(), etape.libelle(), "ACTIVEE", null, candidats, ech));
			if (ech != null) {
				curseurTemps = ech;
			}
		}

		return new SimulationResponse(etapesSimulees);
	}
}
