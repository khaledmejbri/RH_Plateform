package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.ResultatEtapeExecution;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.EtapeCouranteDto;
import com.hr.workflow.dto.WorkflowMoteurDtos.InstanceDecisionDto;
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
import com.hr.workflow.moteur.PreparationDecisionService.PlanExecutionDecision;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance transactionnelle de l'issue d'une décision (§ 9.6.4, D-W08 à D-W11, D-W16, D-W27).
 * Aucune dépendance HTTP. Exige une transaction active ({@link Propagation#MANDATORY}).
 */
@Service
public class ApplicationDecisionService {

	private final WfInstanceRepository instanceRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfEtapeExecutionRepository etapeExecutionRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final OutboxService outboxService;
	private final Clock clock;

	public ApplicationDecisionService(
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfEtapeExecutionRepository etapeExecutionRepository,
			WfHistoriqueRepository historiqueRepository,
			OutboxService outboxService,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.etapeExecutionRepository = etapeExecutionRepository;
		this.historiqueRepository = historiqueRepository;
		this.outboxService = outboxService;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public DecisionResponse appliquerDansTransaction(
			WfInstance inst,
			WfTache t,
			String cleIdempotence,
			String action,
			String commentaire,
			UUID appelantCompteId,
			UUID appelantCollabId,
			String appelantNom,
			UUID pourCollabId,
			String pourNom,
			TypeObjetCatalogue typeCatalogue,
			EtapeDefinition etapeTermineeDef,
			PlanExecutionDecision exec) {
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
		t.setCleIdempotence(cleIdempotence);
		tacheRepository.saveAndFlush(t);

		enregistrerHistorique(inst.getId(), t.getId(), "DECISION", appelantCompteId, appelantNom, pourNom,
				Map.of("action", action, "decision", decisionEnum.name(), "commentaire", commentaire != null ? commentaire : ""),
				maintenant);

		UUID evId = UUID.randomUUID();
		EtapeCouranteDto etapeCouranteDto = null;
		PlanAvancement plan = exec.plan();
		PlanAvancement planReactiv = exec.planReactivation();

		if ("RENVOYER".equals(action)) {
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
			comp.setEcheanceLe(exec.echeanceSuivante());
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
			outboxService.publierEvenementWorkflow(evId, "TACHE_TRAITEE", inst, ee.getEtapeCode(), comp.getLibelle(), exec.echeanceSuivante());
			etapeCouranteDto = new EtapeCouranteDto(ee.getEtapeCode(), comp.getLibelle(), exec.echeanceSuivante(), "Demandeur");

		} else if (t.getType() == TypeTache.COMPLEMENT && planReactiv != null) {
			WfEtapeExecution ee = t.getEtapeExecution();
			if (planReactiv.etapeSuivante() != null
					&& planReactiv.etapeSuivante().etape().code().equals(ee.getEtapeCode())) {
				// Réactivation de l'étape courante via AvancementService (D-W10)
				EtapeActivee activee = planReactiv.etapeSuivante();
				EtapeDefinition etapeDef = activee.etape();
				inst.setStatut(activee.bloquee() ? StatutInstance.BLOQUEE : StatutInstance.EN_COURS);
				inst.setEtapeCouranteCode(etapeDef.code());
				instanceRepository.save(inst);

				etapeCouranteDto = creerTachesEtNotificationsEtape(
						inst, ee, activee, exec.echeanceSuivante(), typeCatalogue,
						exec.comptesNotifActivation(), exec.comptesBloquee(), evId, maintenant,
						"Complément fourni");
			} else {
				// Repli PASSER sur l'étape courante lors de la réactivation
				ee.setStatut(StatutEtapeExecution.TERMINEE);
				ee.setTermineeLe(maintenant);
				etapeExecutionRepository.save(ee);
				etapeCouranteDto = appliquerPlanApresEtape(
						inst, ee, t, action, commentaire, appelantCompteId, appelantNom, pourNom,
						typeCatalogue, null, planReactiv, exec, evId, maintenant);
			}

		} else if (plan == null) {
			// Mode TOUS ou TACHES_PARALLELES : d'autres tâches obligatoires restent ouvertes
			WfEtapeExecution ee = t.getEtapeExecution();
			outboxService.publierEvenementWorkflow(evId, "TACHE_TRAITEE", inst, ee.getEtapeCode(), t.getLibelle(), t.getEcheanceLe());
			etapeCouranteDto = new EtapeCouranteDto(ee.getEtapeCode(), t.getLibelle(), t.getEcheanceLe(), null);

		} else {
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

			etapeCouranteDto = appliquerPlanApresEtape(
					inst, ee, t, action, commentaire, appelantCompteId, appelantNom, pourNom,
					typeCatalogue, etapeTermineeDef, plan, exec, evId, maintenant);
		}

		DecisionResponse reponse = new DecisionResponse(
				t.getId(), decisionEnum.name(), new InstanceDecisionDto(inst.getStatut(), etapeCouranteDto));
		t.setReponseDecision(versMap(reponse));
		tacheRepository.save(t);
		return reponse;
	}

	private EtapeCouranteDto appliquerPlanApresEtape(
			WfInstance inst,
			WfEtapeExecution ee,
			WfTache t,
			String action,
			String commentaire,
			UUID appelantCompteId,
			String appelantNom,
			String pourNom,
			TypeObjetCatalogue typeCatalogue,
			EtapeDefinition etapeTermineeDef,
			PlanAvancement plan,
			PlanExecutionDecision exec,
			UUID evId,
			Instant maintenant) {
		if (etapeTermineeDef != null && !exec.comptesNotifFin().isEmpty()) {
			String canalFin = etapeTermineeDef.notifications() != null && etapeTermineeDef.notifications().canal() != null
					? etapeTermineeDef.notifications().canal() : "WEBSOCKET";
			String texteFin = TextesNotificationWorkflow.information(
					etapeTermineeDef.libelle(), typeCatalogue.libelle(), inst.getDemandeurNom(), inst.getObjetNumero());
			UUID evFin = UUID.randomUUID();
			for (UUID compteId : exec.comptesNotifFin()) {
				outboxService.publierNotification(evFin, compteId.toString(), canalFin, "WF_DECISION",
						etapeTermineeDef.libelle(), texteFin, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
		}

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
			for (UUID compteId : exec.comptesInfos().getOrDefault(info.code(), List.of())) {
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
			return null;
		}

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

		if (inst.getDemandeurCompteId() != null && "APPROUVER".equals(action)) {
			String texteDem = TextesNotificationWorkflow.etapeApprouvee(
					typeCatalogue.libelle(), appelantNom, t.getLibelle(), etapeDef.libelle(), inst.getObjetNumero());
			outboxService.publierNotification(evId, inst.getDemandeurCompteId().toString(), "WEBSOCKET",
					"WF_DECISION", "Étape validée", texteDem, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
		}

		return creerTachesEtNotificationsEtape(
				inst, eSuiv, activee, exec.echeanceSuivante(), typeCatalogue,
				exec.comptesNotifActivation(), exec.comptesBloquee(), evId, maintenant,
				"Demande à traiter");
	}

	private EtapeCouranteDto creerTachesEtNotificationsEtape(
			WfInstance inst,
			WfEtapeExecution ee,
			EtapeActivee activee,
			Instant echeanceSuivante,
			TypeObjetCatalogue typeCatalogue,
			List<UUID> comptesNotifActivation,
			List<UUID> comptesBloquee,
			UUID evId,
			Instant maintenant,
			String sujetTacheCreee) {
		EtapeDefinition etapeDef = activee.etape();
		String canal = etapeDef.notifications() != null && etapeDef.notifications().canal() != null
				? etapeDef.notifications().canal() : "WEBSOCKET";

		for (TacheACreer tc : activee.taches()) {
			WfTache nt = new WfTache();
			nt.setInstance(inst);
			nt.setEtapeExecution(ee);
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
						sujetTacheCreee, texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
		}

		for (UUID compteId : comptesNotifActivation) {
			String texte = TextesNotificationWorkflow.information(
					etapeDef.libelle(), typeCatalogue.libelle(), inst.getDemandeurNom(), inst.getObjetNumero());
			outboxService.publierNotification(evId, compteId.toString(), canal, "WF_TACHE_CREEE",
					etapeDef.libelle(), texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
		}

		if (activee.bloquee()) {
			String texteBloque = TextesNotificationWorkflow.bloquee(
					etapeDef.libelle(), typeCatalogue.libelle(), inst.getDemandeurNom(), inst.getObjetNumero());
			for (UUID compteId : comptesBloquee) {
				outboxService.publierNotification(evId, compteId.toString(), "WEBSOCKET", "WF_BLOQUEE",
						"Workflow bloqué", texteBloque, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
			outboxService.publierEvenementWorkflow(evId, "INSTANCE_BLOQUEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceSuivante);
		} else {
			outboxService.publierEvenementWorkflow(evId, "TACHE_CREEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceSuivante);
		}

		String intitule = etapeDef.intervenants() != null
				? CatalogueTypesObjet.libelleRegle(etapeDef.intervenants().regle(), etapeDef.intervenants().params())
				: etapeDef.libelle();
		return new EtapeCouranteDto(etapeDef.code(), etapeDef.libelle(), echeanceSuivante, intitule);
	}

	public static DecisionTache versDecisionTache(String action) {
		return switch (action) {
			case "APPROUVER" -> DecisionTache.APPROUVEE;
			case "REFUSER" -> DecisionTache.REFUSEE;
			case "RENVOYER" -> DecisionTache.RENVOYEE;
			case "FAIT" -> DecisionTache.FAITE;
			default -> throw WorkflowMetierException.unprocessable("ACTION_NON_AUTORISEE", "Action inconnue : " + action);
		};
	}

	public static Map<String, Object> versMap(DecisionResponse rep) {
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
	public static DecisionResponse reconstruireReponse(Map<String, Object> m) {
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
