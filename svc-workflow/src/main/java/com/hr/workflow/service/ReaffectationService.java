package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.dto.DelegationDtos.ReaffecterTacheRequest;
import com.hr.workflow.dto.SupervisionDtos.CandidatAdminDto;
import com.hr.workflow.dto.SupervisionDtos.ReaffecterTacheResponse;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.OutboxService;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.moteur.PlanificationEcheanceService.PlanificationTache;
import com.hr.workflow.moteur.TextesNotificationWorkflow;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Réaffectation RH d'une tâche ouverte et déblocage d'une instance {@code BLOQUEE} (D-W23, CA-12.1, CA-12.2).
 * <strong>Aucun appel HTTP dans une transaction</strong> (§ 9.6.1).
 * Ordre strict des verrous : {@code wf_instance} puis {@code wf_tache}.
 */
@Service
public class ReaffectationService {

	private final WfInstanceRepository instanceRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final WorkflowReferentielClient referentielClient;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final OutboxService outboxService;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final Clock clock;

	public ReaffectationService(
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			OutboxService outboxService,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.referentielClient = referentielClient;
		this.planificationEcheanceService = planificationEcheanceService;
		this.outboxService = outboxService;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	private record ContexteReaffectation(
			UUID instanceId,
			StatutInstance statutInstance,
			String objetType,
			UUID objetId,
			String objetNumero,
			UUID demandeurCollabId,
			UUID demandeurCompteId,
			String demandeurNom,
			Map<String, Object> variables,
			String etapeCode,
			EtapeDefinition etapeDef,
			Instant echeanceLe,
			List<UUID> beneficiaires
	) {
	}

	public ReaffecterTacheResponse reaffecter(
			UUID tacheId,
			ReaffecterTacheRequest req,
			UUID appelantCompteId,
			String appelantNom) {
		String motif = req.motif() != null ? req.motif().trim() : "";
		if (motif.length() < 5 || motif.length() > 500) {
			throw WorkflowMetierException.unprocessable("MOTIF_OBLIGATOIRE",
					"Un motif de 5 à 500 caractères est obligatoire.");
		}

		if (req.collaborateurIds() == null || req.collaborateurIds().isEmpty() || req.collaborateurIds().size() > 10) {
			throw WorkflowMetierException.unprocessable("COLLABORATEUR_INACTIF",
					"Veuillez sélectionner entre 1 et 10 collaborateurs.");
		}
		Set<UUID> collabIdsUniques = new LinkedHashSet<>();
		for (UUID cid : req.collaborateurIds()) {
			if (cid == null) {
				throw WorkflowMetierException.unprocessable("COLLABORATEUR_INACTIF",
						"Identifiant de collaborateur invalide.");
			}
			collabIdsUniques.add(cid);
		}

		// 1. Lecture contexte en transaction courte (readOnly)
		ContexteReaffectation ctx = txReadOnly.execute(status -> {
			WfTache t = tacheRepository.findByIdAvecDetails(tacheId)
					.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
			WfInstance inst = t.getInstance();
			WfEtapeExecution ee = t.getEtapeExecution();
			DefinitionWorkflow def = codec.deserialiser(inst.getModeleVersion().getDefinition());
			EtapeDefinition etapeDef = def.etapes().stream()
					.filter(e -> e.code().equals(ee.getEtapeCode()))
					.findFirst().orElse(null);
			List<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
					.map(b -> b.getId().getCollaborateurId()).toList();
			return new ContexteReaffectation(
					inst.getId(), inst.getStatut(), inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero(),
					inst.getDemandeurCollaborateurId(), inst.getDemandeurCompteId(), inst.getDemandeurNom(),
					inst.getVariables() != null ? inst.getVariables() : Map.of(),
					ee.getEtapeCode(), etapeDef, t.getEcheanceLe(), benefs);
		});

		// 2. Contrôle I1 (D-W23) : appelant ≠ demandeur, et aucun candidat dans [demandeur, bénéficiaires]
		if (appelantCompteId != null && appelantCompteId.equals(ctx.demandeurCompteId())) {
			throw WorkflowMetierException.forbidden("AUTO_DECISION_INTERDITE",
					"Vous ne pouvez pas réaffecter votre propre demande.");
		}
		if (appelantCompteId != null && ctx.demandeurCollabId() != null) {
			Optional<CollaborateurResume> appelantFiche = referentielClient.collaborateurParCompte(appelantCompteId);
			if (appelantFiche.isPresent() && ctx.demandeurCollabId().equals(appelantFiche.get().id())) {
				throw WorkflowMetierException.forbidden("AUTO_DECISION_INTERDITE",
						"Vous ne pouvez pas réaffecter votre propre demande.");
			}
		}
		for (UUID cid : collabIdsUniques) {
			if (cid.equals(ctx.demandeurCollabId()) || ctx.beneficiaires().contains(cid)) {
				throw WorkflowMetierException.forbidden("AUTO_DECISION_INTERDITE",
						"Le demandeur ou un bénéficiaire ne peut pas être intervenant sur cette tâche.");
			}
		}

		// 3. HORS TRANSACTION : vérification et résolution des collaborateurs cibles au référentiel
		List<CollaborateurResume> resolus = new ArrayList<>(collabIdsUniques.size());
		for (UUID cid : collabIdsUniques) {
			CollaborateurResume collab = referentielClient.collaborateurParId(cid)
					.filter(c -> c.id() != null && c.compteId() != null && "ACTIF".equalsIgnoreCase(c.statut()))
					.orElseThrow(() -> WorkflowMetierException.unprocessable("COLLABORATEUR_INACTIF",
							"Collaborateur introuvable ou inactif : " + cid));
			resolus.add(collab);
		}

		// 4. HORS TRANSACTION : calcul d'échéance si l'instance était BLOQUEE et la tâche sans échéance
		PlanificationTache planifDeblocage = null;
		if (ctx.statutInstance() == StatutInstance.BLOQUEE && ctx.echeanceLe() == null && ctx.etapeDef() != null) {
			LocalDate aujourdhui = LocalDate.now(clock);
			CalendrierSocieteResponse cal = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));
			planifDeblocage = planificationEcheanceService.calculerPlanification(
					clock.instant(), ctx.etapeDef().delai(), ctx.etapeDef().relances(),
					ctx.etapeDef().aEcheance(), cal, ctx.variables());
		}
		final PlanificationTache finalPlanif = planifDeblocage;

		// 5. Transaction courte : verrous wf_instance puis wf_tache, remplacement candidats, déblocage, historique, outbox
		return txWrite.execute(status -> {
			WfInstance inst = instanceRepository.findByIdForUpdate(ctx.instanceId())
					.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
			WfTache t = tacheRepository.findByIdForUpdate(tacheId)
					.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));

			if (inst.getStatut().estTerminale()) {
				throw WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE", "Cette instance est déjà terminée.");
			}
			if (t.getStatut() != StatutTache.A_TRAITER) {
				throw WorkflowMetierException.conflict("TACHE_DEJA_TRAITEE", "Cette tâche a déjà été traitée.");
			}
			if (!t.getVersion().equals(req.version())) {
				throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
						"La tâche a été modifiée par un autre utilisateur.");
			}

			Instant maintenant = clock.instant();
			Map<UUID, WfTacheCandidat> existants = candidatRepository.findByIdTacheId(t.getId()).stream()
					.collect(Collectors.toMap(c -> c.getId().getCollaborateurId(), c -> c, (a, b) -> a));

			// Retirer les candidats actifs absents de la nouvelle liste
			for (WfTacheCandidat ex : existants.values()) {
				if (ex.getRetireLe() == null && !collabIdsUniques.contains(ex.getId().getCollaborateurId())) {
					ex.setRetireLe(maintenant);
					candidatRepository.save(ex);
				}
			}

			boolean debloquee = inst.getStatut() == StatutInstance.BLOQUEE;
			if (debloquee) {
				inst.setStatut(StatutInstance.EN_COURS);
				instanceRepository.save(inst);
				if (t.getEcheanceLe() == null && finalPlanif != null) {
					t.setEcheanceLe(finalPlanif.echeanceLe());
					t.setProchaineRelanceLe(finalPlanif.prochaineRelanceLe());
					t.setEscaladeAFaireLe(finalPlanif.escaladeAFaireLe());
				}
			}

			String typeLibelle = catalogue.findByObjetType(inst.getObjetType())
					.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
			String canal = (ctx.etapeDef() != null && ctx.etapeDef().notifications() != null
					&& ctx.etapeDef().notifications().canal() != null)
					? ctx.etapeDef().notifications().canal() : "WEBSOCKET";
			UUID evId = UUID.randomUUID();
			List<String> nomsNouveaux = new ArrayList<>(resolus.size());
			List<CandidatAdminDto> candidatsDto = new ArrayList<>(resolus.size());

			for (CollaborateurResume target : resolus) {
				WfTacheCandidat ex = existants.get(target.id());
				boolean etaitActif = ex != null && ex.getRetireLe() == null;

				WfTacheCandidat cible = ex != null ? ex : new WfTacheCandidat();
				cible.setId(new WfTacheCandidatId(t.getId(), target.id()));
				cible.setCompteId(target.compteId());
				cible.setNom(target.nom());
				cible.setOrigine(OrigineCandidat.REAFFECTATION);
				cible.setPourCollaborateurId(null);
				cible.setPourNom(null);
				cible.setDelegationId(null);
				cible.setAjouteLe(maintenant);
				cible.setRetireLe(null);
				candidatRepository.save(cible);
				nomsNouveaux.add(target.nom());
				candidatsDto.add(new CandidatAdminDto(
						target.id(), target.compteId(), target.nom(), OrigineCandidat.REAFFECTATION,
						null, null, maintenant, null));

				if (!etaitActif) {
					String texte = TextesNotificationWorkflow.tacheCreee(
							typeLibelle, inst.getDemandeurNom(), t.getLibelle(), t.getEcheanceLe(), inst.getObjetNumero());
					outboxService.publierNotification(
							evId, target.compteId().toString(), canal, "WF_TACHE_CREEE",
							"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}

			if (debloquee) {
				String etapeLib = ctx.etapeDef() != null ? ctx.etapeDef().libelle() : t.getLibelle();
				outboxService.publierEvenementWorkflow(
						evId, "TACHE_CREEE", inst, ctx.etapeCode(), etapeLib, t.getEcheanceLe());
			}

			Instant reaffecteeLe = (t.getReaffecteeLe() != null && !maintenant.isAfter(t.getReaffecteeLe()))
					? t.getReaffecteeLe().plusNanos(1000)
					: maintenant;
			t.setReaffecteeLe(reaffecteeLe);
			t = tacheRepository.saveAndFlush(t);

			Map<String, Object> details = new HashMap<>();
			details.put("motif", motif);
			details.put("nouveaux_candidats", nomsNouveaux);
			if (debloquee) {
				details.put("debloquee", true);
			}
			WfHistorique h = new WfHistorique();
			h.setInstanceId(inst.getId());
			h.setTacheId(t.getId());
			h.setTypeEvenement("REAFFECTEE");
			h.setActeurCompteId(appelantCompteId);
			h.setActeurNom(appelantNom);
			h.setDetails(details);
			h.setHorodatage(maintenant);
			historiqueRepository.save(h);

			return new ReaffecterTacheResponse(
					t.getId(), inst.getId(), inst.getStatut(), t.getEcheanceLe(), t.getVersion(), candidatsDto);
		});
	}
}
