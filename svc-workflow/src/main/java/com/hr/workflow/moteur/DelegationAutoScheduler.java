package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.AbsenceCollaborateur;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.entity.WfDelegation;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfDelegationRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Synchronisation quotidienne des délégations manuelles (début/fin) et automatiques pendant congé approuvé (D-W21, D-W22).
 * Sous {@link VerrouPlanificateur}, <strong>aucun appel HTTP dans une transaction</strong> (§ 9.6.1).
 */
@Component
public class DelegationAutoScheduler {

	private static final Logger log = LoggerFactory.getLogger(DelegationAutoScheduler.class);
	private static final Comparator<WfTache> TRI_INSTANCE_TACHE = Comparator
			.comparing((WfTache t) -> t.getInstance() != null ? t.getInstance().getId() : null,
					Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(WfTache::getId, Comparator.nullsLast(Comparator.naturalOrder()));

	private final WfDelegationRepository delegationRepository;
	private final WfTacheRepository tacheRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final WorkflowReferentielClient referentielClient;
	private final DelegationResolutionService delegationResolutionService;
	private final OutboxService outboxService;
	private final VerrouPlanificateur verrouPlanificateur;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final Clock clock;

	public DelegationAutoScheduler(
			WfDelegationRepository delegationRepository,
			WfTacheRepository tacheRepository,
			WfInstanceRepository instanceRepository,
			WfTacheCandidatRepository candidatRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			WorkflowReferentielClient referentielClient,
			DelegationResolutionService delegationResolutionService,
			OutboxService outboxService,
			VerrouPlanificateur verrouPlanificateur,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.delegationRepository = delegationRepository;
		this.tacheRepository = tacheRepository;
		this.instanceRepository = instanceRepository;
		this.candidatRepository = candidatRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.referentielClient = referentielClient;
		this.delegationResolutionService = delegationResolutionService;
		this.outboxService = outboxService;
		this.verrouPlanificateur = verrouPlanificateur;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(cron = "${workflow.delegations.cron:0 30 0 * * *}", zone = "Africa/Tunis")
	public void executer() {
		verrouPlanificateur.executerSiLibre("wf-delegations", this::synchroniserDelegationsDuJour);
	}

	/** Visible pour les tests. Retourne le nombre de mouvements de candidats appliqués. */
	public int synchroniserDelegationsDuJour() {
		LocalDate ref = LocalDate.now(clock);
		CalendrierSocieteResponse cal = referentielClient.calendrierSociete(ref, ref);
		ZoneId fuseau = PlanificationEcheanceService.parserFuseau(cal != null ? cal.fuseau() : null);
		LocalDate aujourdhui = LocalDate.now(clock.withZone(fuseau));
		boolean delegationAutoConges = cal == null || cal.delegationAutoConges();

		int mouvements = 0;

		// 1. Retirer les candidats DELEGUE liés à une délégation expirée ou annulée
		Integer retiresManuelles = txWrite.execute(status -> retirerDeleguesManuellesExpirees(aujourdhui));
		if (retiresManuelles != null) {
			mouvements += retiresManuelles;
		}

		// 2. Appliquer les délégations manuelles actives aujourd'hui sur les tâches ouvertes
		Integer ajoutsManuelles = txWrite.execute(status -> appliquerDelegationsManuellesActives(aujourdhui));
		if (ajoutsManuelles != null) {
			mouvements += ajoutsManuelles;
		}

		// 3. Délégation automatique pendant congé approuvé (D-W22)
		if (!delegationAutoConges) {
			Integer retiresAuto = txWrite.execute(status -> retirerTousDeleguesAuto());
			if (retiresAuto != null) {
				mouvements += retiresAuto;
			}
		} else {
			mouvements += synchroniserDeleguesConges(aujourdhui);
		}

		if (mouvements > 0) {
			log.info("Synchronisation des délégations pour {} : {} modification(s).", aujourdhui, mouvements);
		}
		return mouvements;
	}

	private int retirerDeleguesManuellesExpirees(LocalDate aujourdhui) {
		List<WfTacheCandidat> expires = candidatRepository.findCandidatsDeleguesExpiresSurTachesOuvertes(aujourdhui);
		return retirerCandidatsSousVerrou(expires);
	}

	private int retirerTousDeleguesAuto() {
		List<WfTacheCandidat> actifs = candidatRepository.findCandidatsDeleguesAutoSurTachesOuvertes();
		return retirerCandidatsSousVerrou(actifs);
	}

	private int retirerCandidatsSousVerrou(List<WfTacheCandidat> candidats) {
		if (candidats == null || candidats.isEmpty()) {
			return 0;
		}
		record CandidatARetirer(WfTacheCandidatId pk, UUID instanceId, UUID tacheId) {}
		List<CandidatARetirer> tries = new ArrayList<>(candidats.size());
		for (WfTacheCandidat c : candidats) {
			WfTache t = tacheRepository.findById(c.getId().getTacheId()).orElse(null);
			if (t == null || t.getInstance() == null) {
				continue;
			}
			tries.add(new CandidatARetirer(c.getId(), t.getInstance().getId(), t.getId()));
		}
		tries.sort(Comparator.comparing(CandidatARetirer::instanceId, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(CandidatARetirer::tacheId, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(item -> item.pk().getCollaborateurId(), Comparator.nullsLast(Comparator.naturalOrder())));

		Instant maintenant = clock.instant();
		int count = 0;
		for (CandidatARetirer item : tries) {
			// Ordre strict des verrous : wf_instance puis wf_tache
			instanceRepository.findByIdForUpdate(item.instanceId());
			WfTache tLock = tacheRepository.findByIdForUpdate(item.tacheId()).orElse(null);
			if (tLock == null || tLock.getStatut() != StatutTache.A_TRAITER) {
				continue;
			}
			WfTacheCandidat cLock = candidatRepository.findById(item.pk()).orElse(null);
			if (cLock != null && cLock.getRetireLe() == null) {
				cLock.setRetireLe(maintenant);
				candidatRepository.save(cLock);
				count++;
			}
		}
		return count;
	}

	private int appliquerDelegationsManuellesActives(LocalDate aujourdhui) {
		List<WfDelegation> actives = delegationRepository.findActivesALaDate(aujourdhui);
		if (actives == null || actives.isEmpty()) {
			return 0;
		}
		Map<UUID, List<WfDelegation>> parDelegant = actives.stream()
				.collect(Collectors.groupingBy(WfDelegation::getDelegantCollaborateurId));

		List<WfTache> taches = tacheRepository.findTachesOuvertesAvecInstance().stream()
				.sorted(TRI_INSTANCE_TACHE)
				.toList();
		if (taches.isEmpty()) {
			return 0;
		}
		List<UUID> tacheIds = taches.stream().map(WfTache::getId).toList();
		Map<UUID, List<WfTacheCandidat>> candsParTache = candidatRepository.findByIdTacheIdIn(tacheIds).stream()
				.collect(Collectors.groupingBy(c -> c.getId().getTacheId()));

		Map<UUID, DefinitionWorkflow> cacheDefinitions = new HashMap<>();
		Instant maintenant = clock.instant();
		int count = 0;

		for (WfTache tache : taches) {
			List<WfTacheCandidat> cands = candsParTache.getOrDefault(tache.getId(), List.of());
			boolean auMoinsUnCandidatEligible = cands.stream().anyMatch(c ->
					c.getRetireLe() == null
							&& c.getOrigine() == OrigineCandidat.TITULAIRE
							&& parDelegant.containsKey(c.getId().getCollaborateurId()));
			if (!auMoinsUnCandidatEligible) {
				continue;
			}

			// Ordre strict des verrous : wf_instance puis wf_tache
			WfInstance inst = instanceRepository.findByIdForUpdate(tache.getInstance().getId()).orElse(null);
			WfTache tLock = tacheRepository.findByIdForUpdate(tache.getId()).orElse(null);
			if (inst == null || tLock == null || inst.getStatut().estTerminale() || tLock.getStatut() != StatutTache.A_TRAITER) {
				continue;
			}

			List<WfTacheCandidat> candsFrais = candidatRepository.findByIdTacheId(tLock.getId());
			Map<UUID, WfTacheCandidat> mapCands = candsFrais.stream()
					.collect(Collectors.toMap(c -> c.getId().getCollaborateurId(), c -> c, (a, b) -> a));
			Set<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
					.map(b -> b.getId().getCollaborateurId()).collect(Collectors.toSet());

			for (WfTacheCandidat titulaire : candsFrais) {
				if (titulaire.getRetireLe() != null || titulaire.getOrigine() != OrigineCandidat.TITULAIRE) {
					continue;
				}
				List<WfDelegation> dels = parDelegant.get(titulaire.getId().getCollaborateurId());
				if (dels == null) {
					continue;
				}
				for (WfDelegation del : dels) {
					if (!DelegationResolutionService.couvreObjetType(del, inst.getObjetType())) {
						continue;
					}
					UUID delegueId = del.getDelegueCollaborateurId();
					if (delegueId == null || del.getDelegueCompteId() == null) {
						continue;
					}
					if (delegueId.equals(inst.getDemandeurCollaborateurId()) || benefs.contains(delegueId)) {
						continue;
					}
					WfTacheCandidat existant = mapCands.get(delegueId);
					if (existant != null && existant.getRetireLe() == null) {
						continue;
					}

					WfTacheCandidat cible = existant != null ? existant : new WfTacheCandidat();
					cible.setId(new WfTacheCandidatId(tLock.getId(), delegueId));
					cible.setCompteId(del.getDelegueCompteId());
					cible.setNom(del.getDelegueNom());
					cible.setOrigine(OrigineCandidat.DELEGUE);
					cible.setPourCollaborateurId(del.getDelegantCollaborateurId());
					cible.setPourNom(del.getDelegantNom());
					cible.setDelegationId(del.getId());
					cible.setAjouteLe(maintenant);
					cible.setRetireLe(null);
					candidatRepository.save(cible);
					mapCands.put(delegueId, cible);
					count++;

					Map<String, Object> details = new HashMap<>();
					details.put("origine", OrigineCandidat.DELEGUE.name());
					details.put("delegation_id", del.getId().toString());
					details.put("delegant_id", del.getDelegantCollaborateurId().toString());
					details.put("delegue_id", delegueId.toString());
					enregistrerHistorique(inst.getId(), tLock.getId(), "DELEGATION_APPLIQUEE",
							del.getDelegantNom(), details, maintenant);

					String typeLibelle = catalogue.findByObjetType(inst.getObjetType())
							.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
					String canal = resoudreCanalNotification(inst, tLock, cacheDefinitions);
					String texte = TextesNotificationWorkflow.tacheCreee(
							typeLibelle, inst.getDemandeurNom(), tLock.getLibelle(), tLock.getEcheanceLe(), inst.getObjetNumero());
					outboxService.publierNotification(
							UUID.randomUUID(), del.getDelegueCompteId().toString(), canal, "WF_TACHE_CREEE",
							"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
					break;
				}
			}
		}
		return count;
	}

	private int synchroniserDeleguesConges(LocalDate aujourdhui) {
		// Hors transaction : lecture des absences du jour
		List<AbsenceCollaborateur> absences = referentielClient.absences(aujourdhui);
		Set<UUID> absentsIds = absences == null ? Set.of() : absences.stream()
				.map(AbsenceCollaborateur::collaborateurId)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());

		record RetraitAuto(
				WfTacheCandidatId pk,
				UUID instanceId,
				UUID tacheId
		) {}
		record AjoutAutoAResoudre(
				UUID tacheId,
				UUID instanceId,
				UUID titulaireId,
				String titulaireNom,
				UUID demandeurCollabId,
				List<UUID> beneficiaires,
				String canal
		) {}
		record EtatSnapshot(
				List<RetraitAuto> aRetirer,
				List<AjoutAutoAResoudre> aAjouter
		) {}

		EtatSnapshot etat = txReadOnly.execute(status -> {
			List<WfTache> taches = tacheRepository.findTachesOuvertesAvecInstance();
			if (taches.isEmpty()) {
				return new EtatSnapshot(List.of(), List.of());
			}
			List<UUID> tacheIds = taches.stream().map(WfTache::getId).toList();
			List<UUID> instanceIds = taches.stream().map(t -> t.getInstance().getId()).distinct().toList();

			Map<UUID, List<WfTacheCandidat>> candsParTache = candidatRepository.findByIdTacheIdIn(tacheIds).stream()
					.collect(Collectors.groupingBy(c -> c.getId().getTacheId()));
			Map<UUID, List<UUID>> benefsParInstance = beneficiaireRepository.findByIdInstanceIdIn(instanceIds).stream()
					.collect(Collectors.groupingBy(
							b -> b.getId().getInstanceId(),
							Collectors.mapping(b -> b.getId().getCollaborateurId(), Collectors.toList())));
			List<WfDelegation> manuelles = delegationRepository.findActivesALaDate(aujourdhui);
			Map<UUID, List<WfDelegation>> manuellesParDelegant = manuelles.stream()
					.collect(Collectors.groupingBy(WfDelegation::getDelegantCollaborateurId));

			Map<UUID, DefinitionWorkflow> cacheDefinitions = new HashMap<>();
			List<RetraitAuto> aRetirer = new ArrayList<>();
			List<AjoutAutoAResoudre> aAjouter = new ArrayList<>();

			for (WfTache t : taches) {
				WfInstance inst = t.getInstance();
				List<WfTacheCandidat> cands = candsParTache.getOrDefault(t.getId(), List.of());
				List<UUID> benefs = benefsParInstance.getOrDefault(inst.getId(), List.of());

				Set<UUID> titulairesCouvertAuto = new HashSet<>();
				for (WfTacheCandidat c : cands) {
					if (c.getRetireLe() != null || c.getOrigine() != OrigineCandidat.DELEGUE_AUTO) {
						continue;
					}
					UUID pourId = c.getPourCollaborateurId();
					boolean toujoursAbsent = pourId != null && absentsIds.contains(pourId);
					boolean aManuelle = pourId != null && aDelegationManuelleCouvrante(manuellesParDelegant.get(pourId), inst.getObjetType());
					if (!toujoursAbsent || aManuelle) {
						aRetirer.add(new RetraitAuto(c.getId(), inst.getId(), t.getId()));
					} else {
						titulairesCouvertAuto.add(pourId);
					}
				}

				String canal = null;
				for (WfTacheCandidat c : cands) {
					if (c.getRetireLe() != null || c.getOrigine() != OrigineCandidat.TITULAIRE) {
						continue;
					}
					UUID titulaireId = c.getId().getCollaborateurId();
					if (!absentsIds.contains(titulaireId)) {
						continue;
					}
					if (aDelegationManuelleCouvrante(manuellesParDelegant.get(titulaireId), inst.getObjetType())) {
						continue;
					}
					if (titulairesCouvertAuto.contains(titulaireId)) {
						continue;
					}
					if (canal == null) {
						canal = resoudreCanalNotification(inst, t, cacheDefinitions);
					}
					aAjouter.add(new AjoutAutoAResoudre(
							t.getId(), inst.getId(), titulaireId, c.getNom(),
							inst.getDemandeurCollaborateurId(), benefs, canal));
				}
			}
			return new EtatSnapshot(aRetirer, aAjouter);
		});

		if (etat == null || (etat.aRetirer().isEmpty() && etat.aAjouter().isEmpty())) {
			return 0;
		}

		// Hors transaction : résolution des remplaçants pour les titulaires nouvellement absents
		record CleResolution(UUID titulaireId, UUID demandeurId, Set<UUID> benefs) {}
		Map<CleResolution, List<IntervenantResolu>> cacheRemplacants = new HashMap<>();

		List<List<IntervenantResolu>> resolutions = new ArrayList<>(etat.aAjouter().size());
		for (AjoutAutoAResoudre ajout : etat.aAjouter()) {
			CleResolution cle = new CleResolution(
					ajout.titulaireId(), ajout.demandeurCollabId(), new HashSet<>(ajout.beneficiaires()));
			List<IntervenantResolu> remps = cacheRemplacants.computeIfAbsent(cle, k ->
					delegationResolutionService.resoudreRemplacantsConges(
							aujourdhui, ajout.titulaireId(), ajout.demandeurCollabId(), ajout.beneficiaires()));
			resolutions.add(remps);
		}

		record CleTache(UUID instanceId, UUID tacheId) implements Comparable<CleTache> {
			@Override
			public int compareTo(CleTache o) {
				int cmp = this.instanceId.compareTo(o.instanceId);
				return cmp != 0 ? cmp : this.tacheId.compareTo(o.tacheId);
			}
		}
		record AjoutResolu(AjoutAutoAResoudre ajout, List<IntervenantResolu> remps) {}
		record OperationsTache(List<WfTacheCandidatId> retraits, List<AjoutResolu> ajouts) {}

		TreeMap<CleTache, OperationsTache> opsParTache = new TreeMap<>();
		for (RetraitAuto r : etat.aRetirer()) {
			opsParTache.computeIfAbsent(new CleTache(r.instanceId(), r.tacheId()),
					k -> new OperationsTache(new ArrayList<>(), new ArrayList<>())).retraits().add(r.pk());
		}
		for (int i = 0; i < etat.aAjouter().size(); i++) {
			List<IntervenantResolu> remps = resolutions.get(i);
			if (remps == null || remps.isEmpty()) {
				continue;
			}
			AjoutAutoAResoudre ajout = etat.aAjouter().get(i);
			opsParTache.computeIfAbsent(new CleTache(ajout.instanceId(), ajout.tacheId()),
					k -> new OperationsTache(new ArrayList<>(), new ArrayList<>())).ajouts().add(new AjoutResolu(ajout, remps));
		}

		if (opsParTache.isEmpty()) {
			return 0;
		}

		// Transaction courte : appliquer retraits et ajouts sous verrou (wf_instance puis wf_tache) triés par (instanceId, tacheId)
		Integer count = txWrite.execute(status -> {
			Instant maintenant = clock.instant();
			int nb = 0;

			for (Map.Entry<CleTache, OperationsTache> entry : opsParTache.entrySet()) {
				CleTache cle = entry.getKey();
				OperationsTache ops = entry.getValue();

				WfInstance inst = instanceRepository.findByIdForUpdate(cle.instanceId()).orElse(null);
				WfTache tLock = tacheRepository.findByIdForUpdate(cle.tacheId()).orElse(null);
				if (inst == null || tLock == null || inst.getStatut().estTerminale()
						|| tLock.getStatut() != StatutTache.A_TRAITER) {
					continue;
				}

				for (WfTacheCandidatId pk : ops.retraits()) {
					WfTacheCandidat cLock = candidatRepository.findById(pk).orElse(null);
					if (cLock != null && cLock.getRetireLe() == null && cLock.getOrigine() == OrigineCandidat.DELEGUE_AUTO) {
						cLock.setRetireLe(maintenant);
						candidatRepository.save(cLock);
						nb++;
					}
				}

				if (ops.ajouts().isEmpty()) {
					continue;
				}

				Set<UUID> benefs = beneficiaireRepository.findByIdInstanceId(inst.getId()).stream()
						.map(b -> b.getId().getCollaborateurId()).collect(Collectors.toSet());
				Map<UUID, WfTacheCandidat> existants = candidatRepository.findByIdTacheId(tLock.getId()).stream()
						.collect(Collectors.toMap(c -> c.getId().getCollaborateurId(), c -> c, (a, b) -> a));

				for (AjoutResolu ar : ops.ajouts()) {
					AjoutAutoAResoudre ajout = ar.ajout();
					for (IntervenantResolu remp : ar.remps()) {
						if (remp.collaborateurId() == null || remp.compteId() == null) {
							continue;
						}
						if (remp.collaborateurId().equals(inst.getDemandeurCollaborateurId())
								|| benefs.contains(remp.collaborateurId())
								|| remp.collaborateurId().equals(ajout.titulaireId())) {
							continue;
						}
						WfTacheCandidat ex = existants.get(remp.collaborateurId());
						if (ex != null && ex.getRetireLe() == null) {
							continue;
						}
						WfTacheCandidat cible = ex != null ? ex : new WfTacheCandidat();
						cible.setId(new WfTacheCandidatId(tLock.getId(), remp.collaborateurId()));
						cible.setCompteId(remp.compteId());
						cible.setNom(remp.nom());
						cible.setOrigine(OrigineCandidat.DELEGUE_AUTO);
						cible.setPourCollaborateurId(ajout.titulaireId());
						cible.setPourNom(ajout.titulaireNom());
						cible.setDelegationId(null);
						cible.setAjouteLe(maintenant);
						cible.setRetireLe(null);
						candidatRepository.save(cible);
						existants.put(remp.collaborateurId(), cible);
						nb++;

						Map<String, Object> details = new HashMap<>();
						details.put("origine", OrigineCandidat.DELEGUE_AUTO.name());
						details.put("delegant_id", ajout.titulaireId().toString());
						details.put("delegue_id", remp.collaborateurId().toString());
						enregistrerHistorique(inst.getId(), tLock.getId(), "DELEGATION_APPLIQUEE",
								ajout.titulaireNom(), details, maintenant);

						String typeLibelle = catalogue.findByObjetType(inst.getObjetType())
								.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
						String texte = TextesNotificationWorkflow.tacheCreee(
								typeLibelle, inst.getDemandeurNom(), tLock.getLibelle(), tLock.getEcheanceLe(), inst.getObjetNumero());
						outboxService.publierNotification(
								UUID.randomUUID(), remp.compteId().toString(), ajout.canal(), "WF_TACHE_CREEE",
								"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
					}
				}
			}
			return nb;
		});
		return count != null ? count : 0;
	}

	private String resoudreCanalNotification(WfInstance inst, WfTache t, Map<UUID, DefinitionWorkflow> cacheDefinitions) {
		if (inst == null || t == null || inst.getModeleVersion() == null
				|| inst.getModeleVersion().getDefinition() == null || t.getEtapeExecution() == null) {
			return "WEBSOCKET";
		}
		String etapeCode = t.getEtapeExecution().getEtapeCode();
		if (etapeCode == null) {
			return "WEBSOCKET";
		}
		UUID versionId = inst.getModeleVersion().getId();
		DefinitionWorkflow def = versionId != null
				? cacheDefinitions.computeIfAbsent(versionId, id -> codec.deserialiser(inst.getModeleVersion().getDefinition()))
				: codec.deserialiser(inst.getModeleVersion().getDefinition());
		if (def == null || def.etapes() == null) {
			return "WEBSOCKET";
		}
		return def.etapes().stream()
				.filter(e -> etapeCode.equals(e.code()))
				.findFirst()
				.map(e -> e.notifications() != null && e.notifications().canal() != null
						&& !e.notifications().canal().isBlank()
						? e.notifications().canal() : "WEBSOCKET")
				.orElse("WEBSOCKET");
	}

	private static boolean aDelegationManuelleCouvrante(List<WfDelegation> manuelles, String objetType) {
		if (manuelles == null || manuelles.isEmpty()) {
			return false;
		}
		for (WfDelegation d : manuelles) {
			if (DelegationResolutionService.couvreObjetType(d, objetType)) {
				return true;
			}
		}
		return false;
	}

	private void enregistrerHistorique(
			UUID instanceId, UUID tacheId, String typeEvenement,
			String pourNom, Map<String, Object> details, Instant horodatage) {
		WfHistorique h = new WfHistorique();
		h.setInstanceId(instanceId);
		h.setTacheId(tacheId);
		h.setTypeEvenement(typeEvenement);
		h.setActeurNom("Système");
		h.setPourNom(pourNom);
		h.setDetails(details);
		h.setHorodatage(horodatage);
		historiqueRepository.save(h);
	}
}
