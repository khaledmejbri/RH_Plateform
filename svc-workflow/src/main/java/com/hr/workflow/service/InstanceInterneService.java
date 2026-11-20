package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.AnnulerInstanceRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.ComplementFourniRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.MajVariablesRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.moteur.DecisionService;
import com.hr.workflow.moteur.OutboxService;
import com.hr.workflow.moteur.TextesNotificationWorkflow;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opérations internes sur les instances de workflow (§ 9.7.4).
 */
@Service
public class InstanceInterneService {

	private final WfInstanceRepository instanceRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final OutboxService outboxService;
	private final DecisionService decisionService;
	private final WorkflowUtilisateurService utilisateurService;
	private final Clock clock;

	public InstanceInterneService(
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			OutboxService outboxService,
			DecisionService decisionService,
			WorkflowUtilisateurService utilisateurService,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.outboxService = outboxService;
		this.decisionService = decisionService;
		this.utilisateurService = utilisateurService;
		this.clock = clock;
	}

	@Transactional
	public Map<String, Object> mettreAJourVariables(UUID instanceId, MajVariablesRequest req) {
		WfInstance inst = instanceRepository.findByIdForUpdate(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		if (inst.getStatut().estTerminale()) {
			throw WorkflowMetierException.conflict("INSTANCE_TERMINEE",
					"Impossible de mettre à jour les variables d'une instance terminée.");
		}
		Map<String, Object> fusion = new HashMap<>(inst.getVariables() != null ? inst.getVariables() : Map.of());
		fusion.putAll(req.variables());
		inst.setVariables(fusion);
		instanceRepository.save(inst);
		return fusion;
	}

	@Transactional
	public void annuler(UUID instanceId, AnnulerInstanceRequest req) {
		WfInstance inst = instanceRepository.findByIdForUpdate(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		if (inst.getStatut() == StatutInstance.ANNULEE) {
			return;
		}
		if (inst.getStatut().estTerminale()) {
			throw WorkflowMetierException.conflict("INSTANCE_TERMINEE", "Instance déjà terminée.");
		}

		Instant maintenant = clock.instant();
		String motif = req != null && req.motif() != null ? req.motif() : "Annulée par le demandeur";
		UUID acteurCompteId = req != null && req.acteur() != null ? req.acteur().compteId() : inst.getDemandeurCompteId();
		String acteurNom = req != null && req.acteur() != null && req.acteur().nom() != null
				? req.acteur().nom() : inst.getDemandeurNom();

		TypeObjetCatalogue tc = catalogue.findByObjetType(inst.getObjetType()).orElse(null);
		String typeLibelle = tc != null ? tc.libelle() : inst.getObjetType();

		UUID evId = UUID.randomUUID();
		List<WfTache> tachesOuvertes = tacheRepository.findByInstanceIdAndStatutIn(
				instanceId, List.of(StatutTache.A_TRAITER, StatutTache.RESERVEE));
		for (WfTache t : tachesOuvertes) {
			t.setStatut(StatutTache.ANNULEE);
			tacheRepository.save(t);
			for (WfTacheCandidat cand : candidatRepository.findByIdTacheIdAndRetireLeIsNull(t.getId())) {
				if (cand.getCompteId() != null) {
					String texte = TextesNotificationWorkflow.annuleeParDemandeur(
							typeLibelle, inst.getDemandeurNom(), inst.getObjetNumero());
					outboxService.publierNotification(evId, cand.getCompteId().toString(), "WEBSOCKET",
							"WF_FIN", "Demande annulée", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}
		}

		inst.setStatut(StatutInstance.ANNULEE);
		inst.setEtapeCouranteCode(null);
		inst.setTermineLe(maintenant);
		inst.setResultatMotif(motif);
		instanceRepository.save(inst);

		WfHistorique h = new WfHistorique();
		h.setInstanceId(inst.getId());
		h.setTypeEvenement("INSTANCE_ANNULEE");
		h.setActeurCompteId(acteurCompteId);
		h.setActeurNom(acteurNom);
		h.setDetails(Map.of("motif", motif));
		h.setHorodatage(maintenant);
		historiqueRepository.save(h);

		outboxService.publierEvenementWorkflow(evId, "INSTANCE_TERMINEE", inst, null, null, null);
	}

	/** Complément fourni côté propriétaire : clôt la tâche {@code COMPLEMENT} ouverte et réactive l'étape (D-W10). */
	public DecisionResponse complementFourni(UUID instanceId, ComplementFourniRequest req) {
		WfTache tacheComp = tacheRepository.findByInstanceIdAndStatutIn(instanceId, List.of(StatutTache.A_TRAITER))
				.stream()
				.filter(t -> t.getType() == TypeTache.COMPLEMENT)
				.findFirst()
				.orElseThrow(() -> WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE",
						"Aucune tâche de complément ouverte sur cette instance."));

		UUID acteurCompteId = (req != null && req.acteur() != null && req.acteur().compteId() != null)
				? req.acteur().compteId()
				: tacheComp.getInstance().getDemandeurCompteId();
		String acteurNom = (req != null && req.acteur() != null && req.acteur().nom() != null)
				? req.acteur().nom()
				: tacheComp.getInstance().getDemandeurNom();
		String commentaire = req != null ? req.commentaire() : null;

		DecisionRequest decReq = new DecisionRequest("FAIT", commentaire, tacheComp.getVersion(),
				"comp-" + tacheComp.getId());
		return decisionService.decider(tacheComp.getId(), decReq, acteurCompteId, acteurNom, true).reponse();
	}

	@Transactional(readOnly = true)
	public SuiviInstanceResponse suiviInterneParObjet(String objetType, UUID objetId) {
		return utilisateurService.suiviParObjetInterne(objetType, objetId);
	}
}
