package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.OrigineDelegation;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationAdminRequest;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationMoiRequest;
import com.hr.workflow.dto.DelegationDtos.DelegationResponse;
import com.hr.workflow.entity.WfDelegation;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.DelegationResolutionService;
import com.hr.workflow.moteur.OutboxService;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.moteur.TextesNotificationWorkflow;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfDelegationRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des délégations manuelles (D-W21, CA-11.1 à CA-11.5, CA-11.7).
 * <strong>Aucun appel HTTP dans une transaction</strong> (§ 9.6.1).
 */
@Service
public class DelegationService {

	private static final int DUREE_MAX_JOURS = 90;

	private final WfDelegationRepository delegationRepository;
	private final WfTacheRepository tacheRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final WorkflowReferentielClient referentielClient;
	private final OutboxService outboxService;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final Clock clock;

	public DelegationService(
			WfDelegationRepository delegationRepository,
			WfTacheRepository tacheRepository,
			WfInstanceRepository instanceRepository,
			WfTacheCandidatRepository candidatRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			WorkflowReferentielClient referentielClient,
			OutboxService outboxService,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.delegationRepository = delegationRepository;
		this.tacheRepository = tacheRepository;
		this.instanceRepository = instanceRepository;
		this.candidatRepository = candidatRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.referentielClient = referentielClient;
		this.outboxService = outboxService;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public List<DelegationResponse> listerMoi(UUID appelantCompteId, String role, Boolean actives) {
		Optional<CollaborateurResume> moi = referentielClient.collaborateurParCompte(appelantCompteId);
		if (moi.isEmpty() || moi.get().id() == null) {
			return List.of();
		}
		UUID collabId = moi.get().id();
		LocalDate aujourdhui = aujourdhuiSociete();
		String roleNorm = role != null ? role.trim().toUpperCase(Locale.ROOT) : "DELEGANT";

		Specification<WfDelegation> spec = (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			if ("DELEGUE".equals(roleNorm)) {
				preds.add(cb.equal(root.get("delegueCollaborateurId"), collabId));
			} else if ("TOUS".equals(roleNorm)) {
				preds.add(cb.or(
						cb.equal(root.get("delegantCollaborateurId"), collabId),
						cb.equal(root.get("delegueCollaborateurId"), collabId)));
			} else {
				preds.add(cb.equal(root.get("delegantCollaborateurId"), collabId));
			}
			ajouterFiltreActives(preds, actives, aujourdhui, root, cb);
			return cb.and(preds.toArray(Predicate[]::new));
		};

		Sort tri = Sort.by(Sort.Order.desc("du"), Sort.Order.desc("creeeLe"));
		return txReadOnly.execute(status -> delegationRepository.findAll(spec, tri).stream()
				.map(d -> versResponse(d, aujourdhui))
				.toList());
	}

	public PageResponse<DelegationResponse> listerAdmin(
			Boolean actives,
			UUID delegantId,
			UUID delegueId,
			int page,
			int taille) {
		int p = Math.max(page, 0);
		int t = Math.min(Math.max(taille, 1), 100);
		LocalDate aujourdhui = aujourdhuiSociete();

		Specification<WfDelegation> spec = (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			if (delegantId != null) {
				preds.add(cb.equal(root.get("delegantCollaborateurId"), delegantId));
			}
			if (delegueId != null) {
				preds.add(cb.equal(root.get("delegueCollaborateurId"), delegueId));
			}
			ajouterFiltreActives(preds, actives, aujourdhui, root, cb);
			return cb.and(preds.toArray(Predicate[]::new));
		};

		Sort tri = Sort.by(Sort.Order.desc("du"), Sort.Order.desc("creeeLe"));
		Page<WfDelegation> res = txReadOnly.execute(status -> delegationRepository.findAll(spec, PageRequest.of(p, t, tri)));
		List<DelegationResponse> elements = res != null
				? res.getContent().stream().map(d -> versResponse(d, aujourdhui)).toList()
				: List.of();
		long total = res != null ? res.getTotalElements() : 0L;
		int num = res != null ? res.getNumber() : p;
		int size = res != null ? res.getSize() : t;
		return new PageResponse<>(elements, num, size, total);
	}

	public DelegationResponse creerMoi(UUID appelantCompteId, CreerDelegationMoiRequest req) {
		LocalDate aujourdhui = aujourdhuiSociete();
		validerDatesEtDuree(req.du(), req.au(), aujourdhui);
		List<String> objetTypes = validerEtNormaliserObjetTypes(req.objetTypes());
		String motif = normaliserMotif(req.motif());

		CollaborateurResume delegant = referentielClient.collaborateurParCompte(appelantCompteId)
				.filter(this::estActifAvecCompte)
				.orElseThrow(() -> WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Aucune fiche collaborateur active n'est associée à votre compte."));

		if (req.delegueId() == null || delegant.id().equals(req.delegueId())) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"Le délégué doit être différent du délégant.");
		}
		CollaborateurResume delegue = referentielClient.collaborateurParId(req.delegueId())
				.filter(this::estActifAvecCompte)
				.orElseThrow(() -> WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Le collaborateur délégué est introuvable ou inactif."));

		return persisterCreation(delegant, delegue, req.du(), req.au(), objetTypes, motif, appelantCompteId, aujourdhui);
	}

	public DelegationResponse creerAdmin(CreerDelegationAdminRequest req, UUID auteurCompteId) {
		LocalDate aujourdhui = aujourdhuiSociete();
		validerDatesEtDuree(req.du(), req.au(), aujourdhui);
		if (req.delegantId() == null || req.delegueId() == null || req.delegantId().equals(req.delegueId())) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"Le délégant et le délégué doivent être distincts.");
		}
		List<String> objetTypes = validerEtNormaliserObjetTypes(req.objetTypes());
		String motif = normaliserMotif(req.motif());

		CollaborateurResume delegant = referentielClient.collaborateurParId(req.delegantId())
				.filter(this::estActifAvecCompte)
				.orElseThrow(() -> WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Le collaborateur délégant est introuvable ou inactif."));
		CollaborateurResume delegue = referentielClient.collaborateurParId(req.delegueId())
				.filter(this::estActifAvecCompte)
				.orElseThrow(() -> WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Le collaborateur délégué est introuvable ou inactif."));

		return persisterCreation(delegant, delegue, req.du(), req.au(), objetTypes, motif, auteurCompteId, aujourdhui);
	}

	public void annulerMoi(UUID delegationId, UUID appelantCompteId) {
		CollaborateurResume moi = referentielClient.collaborateurParCompte(appelantCompteId)
				.orElseThrow(() -> WorkflowMetierException.notFound("DELEGATION_INTROUVABLE", "Délégation introuvable."));

		txWrite.executeWithoutResult(status -> {
			WfDelegation d = delegationRepository.findById(delegationId)
					.filter(del -> moi.id().equals(del.getDelegantCollaborateurId()))
					.orElseThrow(() -> WorkflowMetierException.notFound("DELEGATION_INTROUVABLE", "Délégation introuvable."));
			annulerDansTransaction(d, appelantCompteId);
		});
	}

	public void annulerAdmin(UUID delegationId, UUID auteurCompteId) {
		txWrite.executeWithoutResult(status -> {
			WfDelegation d = delegationRepository.findById(delegationId)
					.orElseThrow(() -> WorkflowMetierException.notFound("DELEGATION_INTROUVABLE", "Délégation introuvable."));
			annulerDansTransaction(d, auteurCompteId);
		});
	}

	private DelegationResponse persisterCreation(
			CollaborateurResume delegant,
			CollaborateurResume delegue,
			LocalDate du,
			LocalDate au,
			List<String> objetTypes,
			String motif,
			UUID creeParCompteId,
			LocalDate aujourdhuiSociete) {
		return txWrite.execute(status -> {
			if (delegationRepository.existsChevauchement(delegant.id(), du, au)) {
				throw WorkflowMetierException.conflict("DELEGATION_CHEVAUCHEMENT",
						"Une délégation active chevauche déjà cette période pour ce délégant.");
			}

			Instant maintenant = clock.instant();
			WfDelegation del = new WfDelegation();
			del.setDelegantCollaborateurId(delegant.id());
			del.setDelegantNom(delegant.nom());
			del.setDelegueCollaborateurId(delegue.id());
			del.setDelegueCompteId(delegue.compteId());
			del.setDelegueNom(delegue.nom());
			del.setDu(du);
			del.setAu(au);
			del.setObjetTypes(objetTypes);
			del.setMotif(motif);
			del.setOrigine(OrigineDelegation.MANUELLE);
			del.setCreeeParCompteId(creeParCompteId);
			del.setCreeeLe(maintenant);
			del = delegationRepository.saveAndFlush(del);

			// Notification WF_DELEGATION au délégué (D-W27)
			if (delegue.compteId() != null) {
				String texte = TextesNotificationWorkflow.delegationRecue(delegant.nom(), du, au);
				outboxService.publierNotification(
						UUID.randomUUID(), delegue.compteId().toString(), "WEBSOCKET", "WF_DELEGATION",
						"Délégation reçue", texte, "DELEGATION", del.getId(), null);
			}

			// Application immédiate sur les tâches ouvertes si la délégation couvre aujourd'hui (CA-11.1)
			if (!du.isAfter(aujourdhuiSociete) && !au.isBefore(aujourdhuiSociete)) {
				appliquerSurTachesOuvertes(del, maintenant);
			}

			return versResponse(del, aujourdhuiSociete);
		});
	}

	private void appliquerSurTachesOuvertes(WfDelegation del, Instant maintenant) {
		List<WfTache> taches = tacheRepository.findTachesOuvertesPourTitulaire(del.getDelegantCollaborateurId());
		for (WfTache candTache : taches) {
			if (!DelegationResolutionService.couvreObjetType(del, candTache.getInstance().getObjetType())) {
				continue;
			}
			// Ordre strict des verrous : wf_instance puis wf_tache
			WfInstance inst = instanceRepository.findByIdForUpdate(candTache.getInstance().getId()).orElse(null);
			WfTache t = tacheRepository.findByIdForUpdate(candTache.getId()).orElse(null);
			if (inst == null || t == null || inst.getStatut().estTerminale() || t.getStatut() != StatutTache.A_TRAITER) {
				continue;
			}
			// Vérifier que le délégant est toujours titulaire actif sur cette tâche (pas de chaîne CA-11.7)
			Optional<WfTacheCandidat> titulaireOpt = candidatRepository.findById(
					new WfTacheCandidatId(t.getId(), del.getDelegantCollaborateurId()));
			if (titulaireOpt.isEmpty()
					|| titulaireOpt.get().getRetireLe() != null
					|| titulaireOpt.get().getOrigine() != OrigineCandidat.TITULAIRE) {
				continue;
			}
			// Invariant I1 : le délégué ne doit être ni demandeur ni bénéficiaire (CA-11.2)
			UUID delegueId = del.getDelegueCollaborateurId();
			if (delegueId.equals(inst.getDemandeurCollaborateurId())
					|| beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(inst.getId(), delegueId)) {
				continue;
			}

			WfTacheCandidatId pk = new WfTacheCandidatId(t.getId(), delegueId);
			WfTacheCandidat existant = candidatRepository.findById(pk).orElse(null);
			if (existant != null && existant.getRetireLe() == null) {
				// Déjà candidat actif sur cette tâche : ne pas dupliquer ni écraser un TITULAIRE
				continue;
			}

			WfTacheCandidat cible = existant != null ? existant : new WfTacheCandidat();
			cible.setId(pk);
			cible.setCompteId(del.getDelegueCompteId());
			cible.setNom(del.getDelegueNom());
			cible.setOrigine(OrigineCandidat.DELEGUE);
			cible.setPourCollaborateurId(del.getDelegantCollaborateurId());
			cible.setPourNom(del.getDelegantNom());
			cible.setDelegationId(del.getId());
			cible.setAjouteLe(maintenant);
			cible.setRetireLe(null);
			candidatRepository.save(cible);

			Map<String, Object> details = new HashMap<>();
			details.put("origine", OrigineCandidat.DELEGUE.name());
			details.put("delegation_id", del.getId().toString());
			details.put("delegant_id", del.getDelegantCollaborateurId().toString());
			details.put("delegue_id", delegueId.toString());
			enregistrerHistorique(inst.getId(), t.getId(), "DELEGATION_APPLIQUEE",
					null, "Système", del.getDelegantNom(), details, maintenant);

			if (del.getDelegueCompteId() != null) {
				String typeLibelle = catalogue.findByObjetType(inst.getObjetType())
						.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
				String texte = TextesNotificationWorkflow.tacheCreee(
						typeLibelle, inst.getDemandeurNom(), t.getLibelle(), t.getEcheanceLe(), inst.getObjetNumero());
				outboxService.publierNotification(
						UUID.randomUUID(), del.getDelegueCompteId().toString(), "WEBSOCKET", "WF_TACHE_CREEE",
						"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
		}
	}

	private void annulerDansTransaction(WfDelegation d, UUID acteurCompteId) {
		if (d.getAnnuleeLe() != null) {
			return;
		}
		Instant maintenant = clock.instant();
		d.setAnnuleeLe(maintenant);
		d.setAnnuleeParCompteId(acteurCompteId);
		delegationRepository.save(d);

		List<WfTacheCandidat> actifs = candidatRepository.findActifsParDelegationSurTachesOuvertes(d.getId());
		for (WfTacheCandidat cand : actifs) {
			WfTache t = tacheRepository.findById(cand.getId().getTacheId()).orElse(null);
			if (t == null) {
				continue;
			}
			// Ordre strict des verrous : wf_instance puis wf_tache
			instanceRepository.findByIdForUpdate(t.getInstance().getId());
			WfTache tLock = tacheRepository.findByIdForUpdate(t.getId()).orElse(null);
			if (tLock == null || tLock.getStatut() != StatutTache.A_TRAITER) {
				continue;
			}
			WfTacheCandidat cLock = candidatRepository.findById(cand.getId()).orElse(null);
			if (cLock != null && cLock.getRetireLe() == null) {
				cLock.setRetireLe(maintenant);
				candidatRepository.save(cLock);
			}
		}
	}

	private LocalDate aujourdhuiSociete() {
		LocalDate ref = LocalDate.now(clock);
		CalendrierSocieteResponse cal = referentielClient.calendrierSociete(ref, ref);
		ZoneId fuseau = PlanificationEcheanceService.parserFuseau(cal != null ? cal.fuseau() : null);
		return LocalDate.now(clock.withZone(fuseau));
	}

	private void validerDatesEtDuree(LocalDate du, LocalDate au, LocalDate aujourdhuiSociete) {
		if (du == null || au == null || au.isBefore(du)) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"Les dates de délégation sont invalides.");
		}
		if (du.isBefore(aujourdhuiSociete)) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"La date de début de la délégation ne peut pas être antérieure à aujourd'hui.");
		}
		if (ChronoUnit.DAYS.between(du, au) > DUREE_MAX_JOURS) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"La durée d'une délégation ne peut pas dépasser 90 jours.");
		}
	}

	private List<String> validerEtNormaliserObjetTypes(List<String> objetTypes) {
		if (objetTypes == null || objetTypes.isEmpty()) {
			return null;
		}
		Set<String> normalises = new LinkedHashSet<>();
		for (String raw : objetTypes) {
			if (raw == null || raw.isBlank()) {
				throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Type d'objet vide dans la délégation.");
			}
			String code = raw.trim();
			if (!catalogue.existe(code)) {
				throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
						"Type d'objet inconnu : " + code);
			}
			normalises.add(code);
		}
		return normalises.isEmpty() ? null : new ArrayList<>(normalises);
	}

	private String normaliserMotif(String motif) {
		if (motif == null) {
			return null;
		}
		String trimmed = motif.trim();
		if (trimmed.length() > 300) {
			throw WorkflowMetierException.unprocessable("DELEGATION_INVALIDE",
					"Le motif de délégation ne peut pas dépasser 300 caractères.");
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	private boolean estActifAvecCompte(CollaborateurResume c) {
		return c != null && c.id() != null && c.compteId() != null && "ACTIF".equalsIgnoreCase(c.statut());
	}

	private static void ajouterFiltreActives(
			List<Predicate> preds,
			Boolean actives,
			LocalDate aujourdhui,
			jakarta.persistence.criteria.Root<WfDelegation> root,
			jakarta.persistence.criteria.CriteriaBuilder cb) {
		if (Boolean.TRUE.equals(actives)) {
			preds.add(cb.isNull(root.get("annuleeLe")));
			preds.add(cb.greaterThanOrEqualTo(root.get("au"), aujourdhui));
		} else if (Boolean.FALSE.equals(actives)) {
			preds.add(cb.or(
					cb.isNotNull(root.get("annuleeLe")),
					cb.lessThan(root.get("au"), aujourdhui)));
		}
	}

	private static DelegationResponse versResponse(WfDelegation d, LocalDate aujourdhuiSociete) {
		boolean active = d.getAnnuleeLe() == null
				&& !d.getDu().isAfter(aujourdhuiSociete)
				&& !d.getAu().isBefore(aujourdhuiSociete);
		return new DelegationResponse(
				d.getId(),
				d.getDelegantCollaborateurId(),
				d.getDelegantNom(),
				d.getDelegueCollaborateurId(),
				d.getDelegueNom(),
				d.getDu(),
				d.getAu(),
				d.getObjetTypes(),
				d.getMotif(),
				d.getOrigine(),
				active,
				d.getCreeeLe(),
				d.getAnnuleeLe());
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
