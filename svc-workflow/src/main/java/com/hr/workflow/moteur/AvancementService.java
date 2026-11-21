package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.TacheParalleleDefinition;
import com.hr.workflow.definition.EvaluateurCondition;
import com.hr.workflow.domain.RaisonSautEtape;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calcul pur (sans écriture, sans appel HTTP) de la suite d'une instance (§ 9.6.1).
 * Gère conditions, sauts, {@code aller_a}, fin, mode {@code TOUS}, {@code TACHES_PARALLELES} et {@code INFORMATION}.
 */
@Service
public class AvancementService {

	private final EvaluateurCondition evaluateur = new EvaluateurCondition();

	public record EtapeSautee(String code, String libelle, RaisonSautEtape raison) {
	}

	public record EtapeInformation(String code, String libelle, List<String> publicDestinataires, String canal) {
	}

	public record TacheACreer(
			String codeTache,
			String libelle,
			TypeTache type,
			boolean obligatoire,
			List<IntervenantResolu> candidats
	) {
	}

	public record EtapeActivee(
			EtapeDefinition etape,
			boolean bloquee,
			List<TacheACreer> taches
	) {
	}

	public record PlanAvancement(
			List<EtapeSautee> etapesSautees,
			List<EtapeInformation> etapesInformation,
			EtapeActivee etapeSuivante,
			StatutInstance statutFinal
	) {
		public boolean estTerminal() {
			return statutFinal == StatutInstance.APPROUVEE || statutFinal == StatutInstance.REFUSEE;
		}
	}

	/** Calcule le plan d'avancement depuis le début de la définition (démarrage § 9.6.3). */
	public PlanAvancement calculerDepuisDebut(
			DefinitionWorkflow definition,
			Map<String, Object> variables,
			Map<String, TypeVariable> typesVariables,
			Map<String, ResultatRegleResolue> resolutions) {
		return avancerDepuisIndex(definition, 0, variables, typesVariables, resolutions, false);
	}

	/** Calcule le plan de réactivation de l'étape courante après fourniture d'un complément (D-W10). */
	public PlanAvancement calculerReactivation(
			DefinitionWorkflow definition,
			String etapeCouranteCode,
			Map<String, Object> variables,
			Map<String, TypeVariable> typesVariables,
			Map<String, ResultatRegleResolue> resolutions) {
		int idxCourant = trouverIndex(definition.etapes(), etapeCouranteCode);
		if (idxCourant < 0) {
			throw new IllegalArgumentException("Étape courante introuvable dans la définition : " + etapeCouranteCode);
		}
		return avancerDepuisIndex(definition, idxCourant, variables, typesVariables, resolutions, true);
	}

	/**
	 * Calcule le plan d'avancement après la complétion d'une étape (résultat {@code APPROUVEE}, {@code FAITE} ou {@code REFUSEE}).
	 */
	public PlanAvancement calculerApresEtape(
			DefinitionWorkflow definition,
			String etapeCouranteCode,
			String resultatEtape,
			Map<String, Object> variables,
			Map<String, TypeVariable> typesVariables,
			Map<String, ResultatRegleResolue> resolutions) {
		List<EtapeDefinition> etapes = definition.etapes();
		int idxCourant = -1;
		for (int i = 0; i < etapes.size(); i++) {
			if (etapes.get(i).code().equals(etapeCouranteCode)) {
				idxCourant = i;
				break;
			}
		}
		if (idxCourant < 0) {
			throw new IllegalArgumentException("Étape courante introuvable dans la définition : " + etapeCouranteCode);
		}

		EtapeDefinition courante = etapes.get(idxCourant);
		String allerA = null;
		if ("REFUSEE".equals(resultatEtape)) {
			allerA = (courante.siRefuse() != null && courante.siRefuse().allerA() != null)
					? courante.siRefuse().allerA()
					: "FIN_REFUS";
		} else {
			if (courante.siApprouve() != null && courante.siApprouve().allerA() != null) {
				allerA = courante.siApprouve().allerA();
			}
		}

		if ("FIN_REFUS".equals(allerA)) {
			return new PlanAvancement(List.of(), List.of(), null, StatutInstance.REFUSEE);
		}
		if ("FIN_APPROBATION".equals(allerA)) {
			return new PlanAvancement(List.of(), List.of(), null, StatutInstance.APPROUVEE);
		}

		int idxDepart;
		if (allerA != null) {
			idxDepart = trouverIndex(etapes, allerA);
			if (idxDepart <= idxCourant) {
				throw new IllegalStateException("Transition invalide vers " + allerA);
			}
		} else {
			idxDepart = idxCourant + 1;
		}

		return avancerDepuisIndex(definition, idxDepart, variables, typesVariables, resolutions, false);
	}

	private PlanAvancement avancerDepuisIndex(
			DefinitionWorkflow definition,
			int indexDepart,
			Map<String, Object> variables,
			Map<String, TypeVariable> typesVariables,
			Map<String, ResultatRegleResolue> resolutions,
			boolean ignorerConditionPremier) {
		List<EtapeDefinition> etapes = definition.etapes();
		List<EtapeSautee> sautees = new ArrayList<>();
		List<EtapeInformation> infos = new ArrayList<>();
		Map<String, ResultatRegleResolue> resMap = resolutions != null ? resolutions : Map.of();

		for (int i = indexDepart; i < etapes.size(); i++) {
			EtapeDefinition etape = etapes.get(i);

			// D-W12 : condition d'activation évaluée au moment de l'arrivée sur l'étape
			if (!(ignorerConditionPremier && i == indexDepart)
					&& etape.condition() != null
					&& !evaluateur.evaluer(etape.condition(), variables, typesVariables)) {
				sautees.add(new EtapeSautee(etape.code(), etape.libelle(), RaisonSautEtape.CONDITION));
				continue;
			}

			// D-W07 : INFORMATION envoie une notification et passe aussitôt à la suite
			if ("INFORMATION".equals(etape.type())) {
				String canal = (etape.notifications() != null && etape.notifications().canal() != null)
						? etape.notifications().canal()
						: "WEBSOCKET";
				infos.add(new EtapeInformation(
						etape.code(), etape.libelle(),
						etape.publicDestinataires() != null ? etape.publicDestinataires() : List.of(),
						canal));
				continue;
			}

			// TACHES_PARALLELES (D-W07)
			if ("TACHES_PARALLELES".equals(etape.type())) {
				List<TacheACreer> taches = new ArrayList<>();
				boolean bloquee = false;
				if (etape.taches() != null) {
					for (TacheParalleleDefinition tp : etape.taches()) {
						ResultatRegleResolue r = resMap.get(etape.code() + ":" + tp.code());
						if (r == null || r.passer()) {
							continue;
						}
						if (r.bloquer() || r.candidats().isEmpty()) {
							if (tp.estObligatoire()) {
								bloquee = true;
								taches.add(new TacheACreer(tp.code(), tp.libelle(), TypeTache.TACHE, true, List.of()));
							}
							continue;
						}
						taches.add(new TacheACreer(tp.code(), tp.libelle(), TypeTache.TACHE, tp.estObligatoire(), r.candidats()));
					}
				}
				if (taches.isEmpty()) {
					sautees.add(new EtapeSautee(etape.code(), etape.libelle(), RaisonSautEtape.AUCUN_INTERVENANT));
					continue;
				}
				return new PlanAvancement(
						sautees, infos,
						new EtapeActivee(etape, bloquee, taches),
						bloquee ? StatutInstance.BLOQUEE : null);
			}

			// VALIDATION ou TACHE
			ResultatRegleResolue r = resMap.get(etape.code());
			if (r != null && r.passer()) {
				sautees.add(new EtapeSautee(etape.code(), etape.libelle(), RaisonSautEtape.AUCUN_INTERVENANT));
				continue;
			}
			TypeTache typeTache = "VALIDATION".equals(etape.type()) ? TypeTache.VALIDATION : TypeTache.TACHE;
			if (r == null || r.bloquer() || r.candidats().isEmpty()) {
				TacheACreer tacheBloquee = new TacheACreer(null, etape.libelle(), typeTache, true, List.of());
				return new PlanAvancement(
						sautees, infos,
						new EtapeActivee(etape, true, List.of(tacheBloquee)),
						StatutInstance.BLOQUEE);
			}

			// Mode TOUS (D-W08) : une tâche par candidat, max 10 (au-delà -> BLOQUEE)
			if ("VALIDATION".equals(etape.type()) && "TOUS".equals(etape.intervenants().modeEffectif())) {
				if (r.candidats().size() > 10) {
					TacheACreer tacheBloquee = new TacheACreer(null, etape.libelle(), TypeTache.VALIDATION, true, List.of());
					return new PlanAvancement(
							sautees, infos,
							new EtapeActivee(etape, true, List.of(tacheBloquee)),
							StatutInstance.BLOQUEE);
				}
				List<TacheACreer> tachesTous = new ArrayList<>();
				for (IntervenantResolu cand : r.candidats()) {
					tachesTous.add(new TacheACreer(null, etape.libelle(), TypeTache.VALIDATION, true, List.of(cand)));
				}
				return new PlanAvancement(sautees, infos, new EtapeActivee(etape, false, tachesTous), null);
			}

			// Mode UN_SEUL ou TACHE : tâche unique partagée
			TacheACreer tacheUnique = new TacheACreer(null, etape.libelle(), typeTache, true, r.candidats());
			return new PlanAvancement(sautees, infos, new EtapeActivee(etape, false, List.of(tacheUnique)), null);
		}

		// Fin de la liste d'étapes -> APPROUVEE (D-W11)
		return new PlanAvancement(sautees, infos, null, StatutInstance.APPROUVEE);
	}

	private static int trouverIndex(List<EtapeDefinition> etapes, String code) {
		for (int i = 0; i < etapes.size(); i++) {
			if (etapes.get(i).code().equals(code)) {
				return i;
			}
		}
		return -1;
	}
}
