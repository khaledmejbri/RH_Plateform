package com.hr.workflow.moteur;

import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.AbsenceCollaborateur;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.entity.WfDelegation;
import com.hr.workflow.moteur.AvancementService.TacheACreer;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfDelegationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution des délégations manuelles (D-W21) et automatiques pendant congé (D-W22) à la création de tâches.
 * <strong>Aucun appel HTTP dans une transaction</strong> (§ 9.6.1).
 */
@Service
public class DelegationResolutionService {

	private final WfDelegationRepository delegationRepository;
	private final WorkflowReferentielClient referentielClient;
	private final ResolutionIntervenantsService resolutionService;
	private final TransactionTemplate txReadOnly;

	public DelegationResolutionService(
			WfDelegationRepository delegationRepository,
			WorkflowReferentielClient referentielClient,
			ResolutionIntervenantsService resolutionService,
			PlatformTransactionManager transactionManager) {
		this.delegationRepository = delegationRepository;
		this.referentielClient = referentielClient;
		this.resolutionService = resolutionService;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
	}

	public record CandidatSupplementaire(
			UUID collaborateurId,
			UUID compteId,
			String nom,
			OrigineCandidat origine,
			UUID pourCollaborateurId,
			String pourNom,
			UUID delegationId
	) {
	}

	/**
	 * Résout les délégués (manuels + auto congés) pour une tâche unique (D-W4-06).
	 */
	public List<CandidatSupplementaire> resoudreDeleguesPourTache(
			LocalDate aujourdhui,
			String objetType,
			UUID demandeurCollabId,
			List<UUID> beneficiaires,
			List<IntervenantResolu> titulaires,
			boolean delegationAutoActive) {
		if (titulaires == null || titulaires.isEmpty()) {
			return List.of();
		}
		List<List<CandidatSupplementaire>> parTache = resoudreDeleguesPourTaches(
				aujourdhui,
				objetType,
				demandeurCollabId,
				beneficiaires,
				List.of(new TacheACreer(null, "", null, true, titulaires)),
				delegationAutoActive);
		return parTache.isEmpty() ? List.of() : parTache.get(0);
	}

	/**
	 * Résout les délégués pour l'ensemble des tâches d'une étape activée en ne lisant les délégations
	 * et les absences du jour qu'une seule fois (D-W4-06).
	 */
	public List<List<CandidatSupplementaire>> resoudreDeleguesPourTaches(
			LocalDate aujourdhui,
			String objetType,
			UUID demandeurCollabId,
			List<UUID> beneficiaires,
			List<TacheACreer> taches,
			boolean delegationAutoActive) {
		if (taches == null || taches.isEmpty()) {
			return List.of();
		}

		Set<UUID> tousTitulaireIds = new HashSet<>();
		for (TacheACreer tc : taches) {
			if (tc.candidats() != null) {
				for (IntervenantResolu c : tc.candidats()) {
					if (c.collaborateurId() != null) {
						tousTitulaireIds.add(c.collaborateurId());
					}
				}
			}
		}
		if (tousTitulaireIds.isEmpty()) {
			return taches.stream().map(t -> List.<CandidatSupplementaire>of()).toList();
		}

		// 1. Lecture seule en transaction courte des délégations manuelles actives aujourd'hui
		List<WfDelegation> actives = txReadOnly.execute(status ->
				delegationRepository.findActivesPourDelegantsALaDate(tousTitulaireIds, aujourdhui));
		Map<UUID, WfDelegation> manuellesParDelegant = new LinkedHashMap<>();
		if (actives != null) {
			for (WfDelegation d : actives) {
				if (couvreObjetType(d, objetType)) {
					manuellesParDelegant.putIfAbsent(d.getDelegantCollaborateurId(), d);
				}
			}
		}

		boolean auMoinsUnSansManuelle = tousTitulaireIds.stream()
				.anyMatch(id -> !manuellesParDelegant.containsKey(id));

		// 2. Hors transaction : absences du jour (si D-W22 activée et au moins un titulaire sans délégation manuelle)
		Set<UUID> absentsIds = Set.of();
		if (delegationAutoActive && auMoinsUnSansManuelle) {
			List<AbsenceCollaborateur> absences = referentielClient.absences(aujourdhui);
			if (absences != null && !absences.isEmpty()) {
				Set<UUID> set = new HashSet<>();
				for (AbsenceCollaborateur a : absences) {
					if (a != null && a.collaborateurId() != null) {
						set.add(a.collaborateurId());
					}
				}
				absentsIds = set;
			}
		}

		Set<UUID> exclusI1 = new HashSet<>();
		if (demandeurCollabId != null) {
			exclusI1.add(demandeurCollabId);
		}
		if (beneficiaires != null) {
			for (UUID b : beneficiaires) {
				if (b != null) {
					exclusI1.add(b);
				}
			}
		}

		// Cache local par titulaire pour éviter de résoudre deux fois le manager d'un même titulaire absent
		Map<UUID, List<IntervenantResolu>> cacheRemplacantsAuto = new HashMap<>();
		List<List<CandidatSupplementaire>> resultatsParTache = new ArrayList<>(taches.size());

		for (TacheACreer tc : taches) {
			List<IntervenantResolu> titulaires = tc.candidats();
			if (titulaires == null || titulaires.isEmpty()) {
				resultatsParTache.add(List.of());
				continue;
			}
			Set<UUID> dejaPresents = new HashSet<>();
			for (IntervenantResolu t : titulaires) {
				if (t.collaborateurId() != null) {
					dejaPresents.add(t.collaborateurId());
				}
			}

			List<CandidatSupplementaire> supplementaires = new ArrayList<>();
			for (IntervenantResolu titulaire : titulaires) {
				UUID titulaireId = titulaire.collaborateurId();
				if (titulaireId == null) {
					continue;
				}
				WfDelegation manuelle = manuellesParDelegant.get(titulaireId);
				if (manuelle != null) {
					UUID delegueId = manuelle.getDelegueCollaborateurId();
					if (delegueId != null
							&& manuelle.getDelegueCompteId() != null
							&& !exclusI1.contains(delegueId)
							&& dejaPresents.add(delegueId)) {
						supplementaires.add(new CandidatSupplementaire(
								delegueId,
								manuelle.getDelegueCompteId(),
								manuelle.getDelegueNom(),
								OrigineCandidat.DELEGUE,
								titulaireId,
								titulaire.nom(),
								manuelle.getId()));
					}
				} else if (delegationAutoActive && absentsIds.contains(titulaireId)) {
					List<IntervenantResolu> remplacants = cacheRemplacantsAuto.computeIfAbsent(titulaireId,
							id -> resoudreRemplacantsConges(aujourdhui, id, demandeurCollabId, beneficiaires));
					for (IntervenantResolu remp : remplacants) {
						if (remp.collaborateurId() != null
								&& remp.compteId() != null
								&& !exclusI1.contains(remp.collaborateurId())
								&& !remp.collaborateurId().equals(titulaireId)
								&& dejaPresents.add(remp.collaborateurId())) {
							supplementaires.add(new CandidatSupplementaire(
									remp.collaborateurId(),
									remp.compteId(),
									remp.nom(),
									OrigineCandidat.DELEGUE_AUTO,
									titulaireId,
									titulaire.nom(),
									null));
						}
					}
				}
			}
			resultatsParTache.add(supplementaires);
		}
		return resultatsParTache;
	}

	/**
	 * Résout hors transaction le remplaçant d'un titulaire en congé approuvé (D-W22 : MANAGER_NOEUD, repli ROLE [RH, ADMIN]).
	 */
	public List<IntervenantResolu> resoudreRemplacantsConges(
			LocalDate aujourdhui,
			UUID titulaireCollabId,
			UUID demandeurCollabId,
			List<UUID> beneficiaires) {
		List<UUID> exclure = new ArrayList<>();
		if (demandeurCollabId != null) {
			exclure.add(demandeurCollabId);
		}
		if (beneficiaires != null) {
			for (UUID b : beneficiaires) {
				if (b != null) {
					exclure.add(b);
				}
			}
		}
		exclure.add(titulaireCollabId);

		RegleAResoudre regle = new RegleAResoudre(
				"delegation_auto",
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("ROLE", List.of("RH", "ADMIN")),
				true);
		LotResolu lot = resolutionService.resoudre(aujourdhui, titulaireCollabId, List.of(), exclure, List.of(regle));
		if (lot == null || lot.resultats() == null) {
			return List.of();
		}
		ResultatRegleResolue res = lot.resultats().get("delegation_auto");
		if (res == null || res.bloquer() || res.candidats() == null) {
			return List.of();
		}
		return res.candidats();
	}

	public static boolean couvreObjetType(WfDelegation delegation, String objetType) {
		if (delegation == null) {
			return false;
		}
		List<String> types = delegation.getObjetTypes();
		return types == null || types.isEmpty() || (objetType != null && types.contains(objetType));
	}
}
