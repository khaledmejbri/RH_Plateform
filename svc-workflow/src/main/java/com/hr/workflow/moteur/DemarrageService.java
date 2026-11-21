package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.WorkflowMoteurDtos.DemarrerInstanceRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DemarrerInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.EtapeCouranteDto;
import com.hr.workflow.entity.WfBeneficiaire;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.AvancementService.EtapeActivee;
import com.hr.workflow.moteur.AvancementService.EtapeInformation;
import com.hr.workflow.moteur.AvancementService.EtapeSautee;
import com.hr.workflow.moteur.AvancementService.PlanAvancement;
import com.hr.workflow.moteur.AvancementService.TacheACreer;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Démarrage d'une instance de workflow (§ 9.6.3, § 9.6.7).
 * <strong>Aucun appel HTTP dans une transaction</strong> : toutes les résolutions se font hors transaction,
 * puis une transaction courte persiste l'instance, les tâches, l'historique et l'outbox.
 */
@Service
public class DemarrageService {

	private final WfActivationRepository activationRepository;
	private final WfModeleRepository modeleRepository;
	private final WfModeleVersionRepository versionRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WfEtapeExecutionRepository etapeExecutionRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final ResolutionIntervenantsService resolutionService;
	private final WorkflowReferentielClient referentielClient;
	private final PlanificationEcheanceService planificationEcheanceService;
	private final AvancementService avancementService;
	private final OutboxService outboxService;
	private final TransactionTemplate txReadOnly;
	private final TransactionTemplate txWrite;
	private final Clock clock;

	public DemarrageService(
			WfActivationRepository activationRepository,
			WfModeleRepository modeleRepository,
			WfModeleVersionRepository versionRepository,
			WfInstanceRepository instanceRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WfEtapeExecutionRepository etapeExecutionRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			ResolutionIntervenantsService resolutionService,
			WorkflowReferentielClient referentielClient,
			PlanificationEcheanceService planificationEcheanceService,
			AvancementService avancementService,
			OutboxService outboxService,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.activationRepository = activationRepository;
		this.modeleRepository = modeleRepository;
		this.versionRepository = versionRepository;
		this.instanceRepository = instanceRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.etapeExecutionRepository = etapeExecutionRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.resolutionService = resolutionService;
		this.referentielClient = referentielClient;
		this.planificationEcheanceService = planificationEcheanceService;
		this.avancementService = avancementService;
		this.outboxService = outboxService;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.txWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public record ResultatDemarrage(boolean cree, DemarrerInstanceResponse reponse) {
	}

	public ResultatDemarrage demarrer(String cleDemarrageHeader, DemarrerInstanceRequest req) {
		String cleDemarrage = (cleDemarrageHeader != null && !cleDemarrageHeader.isBlank())
				? cleDemarrageHeader.trim()
				: req.objetType() + ":" + req.objetId();

		TypeObjetCatalogue typeCatalogue = catalogue.findByObjetType(req.objetType())
				.orElseThrow(() -> WorkflowMetierException.badRequest("TYPE_OBJET_INCONNU",
						"Type d'objet inconnu : " + req.objetType()));

		// Étapes 1 & 2 (lecture seule) : idempotence, conflit, activation MOTEUR, sélection modèle (D-W01)
		record Preparation(
				DemarrerInstanceResponse existante,
				UUID versionId,
				String modeleCode,
				int versionNumero,
				DefinitionWorkflow definition) {
		}

		Preparation prep = txReadOnly.execute(status -> {
			Optional<WfInstance> parCle = instanceRepository.findByCleDemarrage(cleDemarrage);
			if (parCle.isPresent()) {
				return new Preparation(construireReponseExistante(parCle.get()), null, null, 0, null);
			}
			Optional<WfInstance> parObjet = instanceRepository.findByObjetTypeAndObjetId(req.objetType(), req.objetId());
			if (parObjet.isPresent()) {
				throw WorkflowMetierException.conflict("INSTANCE_EXISTANTE",
						"Une instance de workflow existe déjà pour cet objet.");
			}

			ModeActivation mode = activationRepository.findByObjetType(req.objetType())
					.map(a -> a.getMode())
					.orElse(ModeActivation.HERITE);
			if (mode != ModeActivation.MOTEUR) {
				throw WorkflowMetierException.conflict("MODE_HERITE",
						"Le type " + req.objetType() + " n'est pas activé en mode MOTEUR.");
			}

			record ModeleEtVersion(WfModele modele, WfModeleVersion version) {}
			String sousType = req.sousType() != null && !req.sousType().isBlank() ? req.sousType().trim() : null;
			Optional<ModeleEtVersion> choix = Optional.empty();
			if (sousType != null) {
				choix = modeleRepository.findActifParObjetTypeEtSousType(req.objetType(), sousType)
						.flatMap(m -> versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)
								.map(v -> new ModeleEtVersion(m, v)));
			}
			if (choix.isEmpty()) {
				choix = modeleRepository.findActifParObjetTypeEtSousType(req.objetType(), null)
						.flatMap(m -> versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)
								.map(v -> new ModeleEtVersion(m, v)));
			}

			ModeleEtVersion mv = choix.orElseThrow(() -> WorkflowMetierException.unprocessable("WORKFLOW_ABSENT",
					"Ce type de demande n'est pas ouvert dans votre société."));
			WfModele modele = mv.modele();
			WfModeleVersion versionPubliee = mv.version();

			DefinitionWorkflow def = codec.deserialiser(versionPubliee.getDefinition());
			return new Preparation(null, versionPubliee.getId(), modele.getCode(), versionPubliee.getNumero(), def);
		});

		if (prep.existante() != null) {
			return new ResultatDemarrage(false, prep.existante());
		}

		// Étape 3 (HORS TRANSACTION) : résolution groupée + variables communes + calendrier
		Instant maintenant = clock.instant();
		LocalDate aujourdhui = LocalDate.now(clock);
		List<RegleAResoudre> regles = collecterRegles(prep.definition());
		LotResolu lot = resolutionService.resoudre(
				aujourdhui, req.demandeurCollaborateurId(), req.beneficiaires(), List.of(), regles);
		CalendrierSocieteResponse calendrier = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(60));

		Map<String, Object> variables = new HashMap<>();
		if (req.variables() != null) {
			variables.putAll(req.variables());
		}
		ContexteDemandeur ctx = lot.contexteDemandeur();
		if (ctx != null) {
			variables.put("demandeur_profil_acces", ctx.profilAcces());
			variables.put("demandeur_type_noeud", ctx.typeNoeud());
			variables.put("demandeur_anciennete_mois", ctx.ancienneteMois());
			variables.put("demandeur_est_manager", ctx.estManager());
		}

		Map<String, TypeVariable> typesVars = new HashMap<>();
		for (VariableCatalogue vc : catalogue.variablesPour(req.objetType()).values()) {
			typesVars.put(vc.nom(), vc.type());
		}

		PlanAvancement plan = avancementService.calculerDepuisDebut(
				prep.definition(), variables, typesVars, lot.resultats());

		Instant echeanceEtape = (plan.etapeSuivante() != null && !plan.etapeSuivante().bloquee())
				? planificationEcheanceService.calculerEcheance(
						maintenant, plan.etapeSuivante().etape().delai(), calendrier, variables)
				: null;

		// Résolution hors transaction des publics pour étapes INFORMATION et notifications additionnelles
		Map<String, List<UUID>> comptesInfos = new HashMap<>();
		for (EtapeInformation info : plan.etapesInformation()) {
			comptesInfos.put(info.code(), resolutionService.resoudreComptesPublics(
					aujourdhui, info.publicDestinataires(), req.demandeurCollaborateurId(), req.demandeurCompteId(), List.of()));
		}
		List<UUID> comptesNotifActivation = List.of();
		List<UUID> comptesBloquee = List.of();
		if (plan.etapeSuivante() != null && plan.etapeSuivante().bloquee()) {
			comptesBloquee = resolutionService.resoudreComptesRoles(aujourdhui, List.of("RH", "ADMIN"));
		} else if (plan.etapeSuivante() != null && plan.etapeSuivante().etape().notifications() != null
				&& plan.etapeSuivante().etape().notifications().aLActivation() != null) {
			List<IntervenantResolu> tousCandidats = plan.etapeSuivante().taches().stream()
					.flatMap(t -> t.candidats().stream()).toList();
			comptesNotifActivation = resolutionService.resoudreComptesPublics(
					aujourdhui, plan.etapeSuivante().etape().notifications().aLActivation(),
					req.demandeurCollaborateurId(), req.demandeurCompteId(), tousCandidats);
		}

		// Étape 4 (TRANSACTION COURTE) : persistance atomique + outbox, capture concurrence (§ 9.6.7)
		final List<UUID> finalComptesNotifActivation = comptesNotifActivation;
		final List<UUID> finalComptesBloquee = comptesBloquee;
		try {
			DemarrerInstanceResponse rep = txWrite.execute(status -> persisterDemarrage(
					cleDemarrage, req, typeCatalogue, prep.versionId(), prep.modeleCode(), prep.versionNumero(),
					variables, plan, echeanceEtape, comptesInfos, finalComptesNotifActivation, finalComptesBloquee, maintenant));
			return new ResultatDemarrage(true, rep);
		} catch (DataIntegrityViolationException ex) {
			// Concurrence § 9.6.7 : relire l'instance existante pour la même clé
			Optional<WfInstance> existante = txReadOnly.execute(status -> instanceRepository.findByCleDemarrage(cleDemarrage));
			if (existante.isPresent()) {
				return new ResultatDemarrage(false, construireReponseExistante(existante.get()));
			}
			throw WorkflowMetierException.conflict("INSTANCE_EXISTANTE",
					"Une instance de workflow existe déjà pour cet objet.");
		}
	}

	private DemarrerInstanceResponse persisterDemarrage(
			String cleDemarrage,
			DemarrerInstanceRequest req,
			TypeObjetCatalogue typeCatalogue,
			UUID versionId,
			String modeleCode,
			int versionNumero,
			Map<String, Object> variables,
			PlanAvancement plan,
			Instant echeanceEtape,
			Map<String, List<UUID>> comptesInfos,
			List<UUID> comptesNotifActivation,
			List<UUID> comptesBloquee,
			Instant maintenant) {
		WfModeleVersion versionRef = versionRepository.getReferenceById(versionId);
		WfInstance inst = new WfInstance();
		inst.setModeleVersion(versionRef);
		inst.setModeleCode(modeleCode);
		inst.setModeleVersionNumero(versionNumero);
		inst.setObjetType(req.objetType());
		inst.setSousType(req.sousType());
		inst.setObjetId(req.objetId());
		inst.setObjetNumero(req.objetNumero());
		inst.setServiceProprietaire(typeCatalogue.serviceProprietaire());
		inst.setDemandeurCollaborateurId(req.demandeurCollaborateurId());
		inst.setDemandeurCompteId(req.demandeurCompteId());
		inst.setDemandeurNom(req.demandeurNom());
		inst.setVariables(variables);
		inst.setCleDemarrage(cleDemarrage);
		inst.setDemarreLe(maintenant);

		if (plan.estTerminal()) {
			inst.setStatut(plan.statutFinal());
			inst.setTermineLe(maintenant);
			inst.setEtapeCouranteCode(null);
		} else if (plan.statutFinal() == StatutInstance.BLOQUEE) {
			inst.setStatut(StatutInstance.BLOQUEE);
			inst.setEtapeCouranteCode(plan.etapeSuivante().etape().code());
		} else {
			inst.setStatut(StatutInstance.EN_COURS);
			inst.setEtapeCouranteCode(plan.etapeSuivante().etape().code());
		}
		inst = instanceRepository.saveAndFlush(inst);

		// Bénéficiaires
		if (req.beneficiaires() != null) {
			for (UUID benefId : req.beneficiaires()) {
				if (benefId != null) {
					beneficiaireRepository.save(WfBeneficiaire.of(inst.getId(), benefId));
				}
			}
		}

		enregistrerHistorique(inst.getId(), null, "INSTANCE_DEMARREE", req.demandeurCompteId(), req.demandeurNom(),
				null, Map.of("modele_code", modeleCode, "version", versionNumero), maintenant);

		int ordre = 1;
		for (EtapeSautee saut : plan.etapesSautees()) {
			WfEtapeExecution ee = new WfEtapeExecution();
			ee.setInstance(inst);
			ee.setEtapeCode(saut.code());
			ee.setOrdre(ordre++);
			ee.setStatut(StatutEtapeExecution.SAUTEE);
			ee.setRaisonSaut(saut.raison());
			ee.setActiveLe(maintenant);
			ee.setTermineeLe(maintenant);
			etapeExecutionRepository.save(ee);
			enregistrerHistorique(inst.getId(), null, "ETAPE_SAUTEE", null, "Système", null,
					Map.of("etape_code", saut.code(), "raison", saut.raison().name()), maintenant);
		}

		for (EtapeInformation info : plan.etapesInformation()) {
			WfEtapeExecution ee = new WfEtapeExecution();
			ee.setInstance(inst);
			ee.setEtapeCode(info.code());
			ee.setOrdre(ordre++);
			ee.setStatut(StatutEtapeExecution.TERMINEE);
			ee.setActiveLe(maintenant);
			ee.setTermineeLe(maintenant);
			etapeExecutionRepository.save(ee);
			UUID evId = UUID.randomUUID();
			String texte = TextesNotificationWorkflow.information(
					info.libelle(), typeCatalogue.libelle(), req.demandeurNom(), req.objetNumero());
			for (UUID compteId : comptesInfos.getOrDefault(info.code(), List.of())) {
				outboxService.publierNotification(evId, compteId.toString(), info.canal(), "WF_INFORMATION",
						info.libelle(), texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}
		}

		EtapeCouranteDto etapeCouranteDto = null;
		String effetImmediat = null;

		if (plan.estTerminal()) {
			effetImmediat = plan.statutFinal() == StatutInstance.APPROUVEE ? "APPROUVER" : "REFUSER";
			outboxService.publierEvenementWorkflow(UUID.randomUUID(), "INSTANCE_TERMINEE", inst, null, null, null);
		} else {
			EtapeActivee activee = plan.etapeSuivante();
			EtapeDefinition etapeDef = activee.etape();
			WfEtapeExecution ee = new WfEtapeExecution();
			ee.setInstance(inst);
			ee.setEtapeCode(etapeDef.code());
			ee.setOrdre(ordre);
			ee.setStatut(StatutEtapeExecution.ACTIVE);
			ee.setActiveLe(maintenant);
			ee = etapeExecutionRepository.saveAndFlush(ee);

			UUID evTacheId = UUID.randomUUID();
			String canal = etapeDef.notifications() != null ? etapeDef.notifications().canal() : "WEBSOCKET";

			for (TacheACreer tc : activee.taches()) {
				WfTache tache = new WfTache();
				tache.setInstance(inst);
				tache.setEtapeExecution(ee);
				tache.setCodeTache(tc.codeTache());
				tache.setType(tc.type());
				tache.setLibelle(tc.libelle());
				tache.setStatut(StatutTache.A_TRAITER);
				tache.setObligatoire(tc.obligatoire());
				tache.setEcheanceLe(echeanceEtape);
				tache = tacheRepository.saveAndFlush(tache);

				for (IntervenantResolu cand : tc.candidats()) {
					WfTacheCandidat c = new WfTacheCandidat();
					c.setId(new WfTacheCandidatId(tache.getId(), cand.collaborateurId()));
					c.setCompteId(cand.compteId());
					c.setNom(cand.nom());
					c.setOrigine(OrigineCandidat.TITULAIRE);
					c.setAjouteLe(maintenant);
					candidatRepository.save(c);

					String texte = TextesNotificationWorkflow.tacheCreee(
							typeCatalogue.libelle(), req.demandeurNom(), tc.libelle(), echeanceEtape, req.objetNumero());
					outboxService.publierNotification(evTacheId, cand.compteId().toString(), canal, "WF_TACHE_CREEE",
							"Demande à traiter", texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
			}

			for (UUID compteId : comptesNotifActivation) {
				String texte = TextesNotificationWorkflow.information(
						etapeDef.libelle(), typeCatalogue.libelle(), req.demandeurNom(), req.objetNumero());
				outboxService.publierNotification(evTacheId, compteId.toString(), canal, "WF_TACHE_CREEE",
						etapeDef.libelle(), texte, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
			}

			if (activee.bloquee()) {
				String texteBloque = TextesNotificationWorkflow.bloquee(
						etapeDef.libelle(), typeCatalogue.libelle(), req.demandeurNom(), req.objetNumero());
				UUID evBloque = UUID.randomUUID();
				for (UUID compteId : comptesBloquee) {
					outboxService.publierNotification(evBloque, compteId.toString(), "WEBSOCKET", "WF_BLOQUEE",
							"Workflow bloqué", texteBloque, inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero());
				}
				outboxService.publierEvenementWorkflow(evBloque, "INSTANCE_BLOQUEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceEtape);
			} else {
				outboxService.publierEvenementWorkflow(UUID.randomUUID(), "INSTANCE_DEMARREE", inst, etapeDef.code(), etapeDef.libelle(), echeanceEtape);
				outboxService.publierEvenementWorkflow(evTacheId, "TACHE_CREEE", inst, etapeDef.code(), etapeDef.libelle(), echeanceEtape);
			}

			String intitule = etapeDef.intervenants() != null
					? CatalogueTypesObjet.libelleRegle(etapeDef.intervenants().regle(), etapeDef.intervenants().params())
					: etapeDef.libelle();
			etapeCouranteDto = new EtapeCouranteDto(etapeDef.code(), etapeDef.libelle(), echeanceEtape, intitule);
		}

		return new DemarrerInstanceResponse(inst.getId(), inst.getStatut(), etapeCouranteDto, effetImmediat);
	}

	private DemarrerInstanceResponse construireReponseExistante(WfInstance inst) {
		EtapeCouranteDto etapeDto = null;
		if (inst.getEtapeCouranteCode() != null) {
			List<WfTache> taches = tacheRepository.findByInstanceIdAndStatutIn(
					inst.getId(), List.of(StatutTache.A_TRAITER, StatutTache.RESERVEE));
			Instant echeance = taches.isEmpty() ? null : taches.get(0).getEcheanceLe();
			String libelle = taches.isEmpty() ? inst.getEtapeCouranteCode() : taches.get(0).getLibelle();
			etapeDto = new EtapeCouranteDto(inst.getEtapeCouranteCode(), libelle, echeance, null);
		}
		boolean sansEtapeHumaine = tacheRepository.findByInstanceId(inst.getId()).isEmpty();
		String effetImmediat = (inst.getStatut() == StatutInstance.APPROUVEE && sansEtapeHumaine)
				? "APPROUVER" : null;
		return new DemarrerInstanceResponse(inst.getId(), inst.getStatut(), etapeDto, effetImmediat);
	}

	public static List<RegleAResoudre> collecterRegles(DefinitionWorkflow def) {
		List<RegleAResoudre> regles = new ArrayList<>();
		if (def == null || def.etapes() == null) {
			return regles;
		}
		for (EtapeDefinition e : def.etapes()) {
			if (("VALIDATION".equals(e.type()) || "TACHE".equals(e.type())) && e.intervenants() != null) {
				regles.add(new RegleAResoudre(e.code(), e.intervenants(), e.repliSiVide(), "VALIDATION".equals(e.type())));
			} else if ("TACHES_PARALLELES".equals(e.type()) && e.taches() != null) {
				for (EtapeDefinition.TacheParalleleDefinition tp : e.taches()) {
					regles.add(new RegleAResoudre(e.code() + ":" + tp.code(), tp.intervenants(), tp.repliSiVide(), false));
				}
			}
		}
		return regles;
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
