package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CDC v2 §M01 — Demandes administratives avec notifications correctes à chaque étape.
 *
 * Valideur 1er niveau = manager ACTIF du nœud d'unité d'affectation (snapshot à la création).
 * Le champ fiche {@code superieur} est dérivé ; le JWT RO seul ne suffit pas.
 *
 * Chaîne de notification par hiérarchie :
 *   Soumission           → manager du nœud (ou RH si aucun)
 *   Validation manager   → tous les RH actifs
 *   Refus manager        → demandeur
 *   Approbation RRH      → demandeur
 *   Refus RRH            → demandeur
 *   Annulation demandeur → manager du nœud (pour info)
 */
@Service
public class DemandeAdministrativeRhService {

	private final DemandeAdministrativeRhRepository demandeRepo;
	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final DemandeAdministrativeValidationService validationService;
	private final RhNotificationPublisher notificationPublisher;
	private final DemandeAdminWorkflowHistoryRepository workflowHistoryRepository;
	private final NumeroLisibleService numeroLisibleService;
	private final ParametreService parametreService;
	private final CongeDecompteService congeDecompteService;
	private final AnnulationCongeService annulationCongeService;
	private final ChampsDecideurSolde champsDecideurSolde;
	private final AuteurParametreResolver auteurResolver;
	private final WorkflowActivationService workflowActivationService;
	private final WorkflowDemarrageService workflowDemarrageService;
	private final com.hr.referentiel.client.WorkflowMoteurClient moteurClient;
	private final WorkflowVariablesBuilder variablesBuilder;
	private final org.springframework.transaction.support.TransactionTemplate txEcriture;
	private final org.springframework.transaction.support.TransactionTemplate txLecture;

	public DemandeAdministrativeRhService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService,
			RhNotificationPublisher notificationPublisher,
			DemandeAdminWorkflowHistoryRepository workflowHistoryRepository,
			NumeroLisibleService numeroLisibleService,
			ParametreService parametreService,
			CongeDecompteService congeDecompteService,
			AnnulationCongeService annulationCongeService,
			ChampsDecideurSolde champsDecideurSolde,
			AuteurParametreResolver auteurResolver) {
		this(demandeRepo, collaborateurRepository, collaborateurConnecteService, validationService,
				notificationPublisher, workflowHistoryRepository, numeroLisibleService, parametreService,
				congeDecompteService, annulationCongeService, champsDecideurSolde, auteurResolver,
				null, null, null, null, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public DemandeAdministrativeRhService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService,
			RhNotificationPublisher notificationPublisher,
			DemandeAdminWorkflowHistoryRepository workflowHistoryRepository,
			NumeroLisibleService numeroLisibleService,
			ParametreService parametreService,
			CongeDecompteService congeDecompteService,
			AnnulationCongeService annulationCongeService,
			ChampsDecideurSolde champsDecideurSolde,
			AuteurParametreResolver auteurResolver,
			WorkflowActivationService workflowActivationService,
			WorkflowDemarrageService workflowDemarrageService,
			com.hr.referentiel.client.WorkflowMoteurClient moteurClient,
			WorkflowVariablesBuilder variablesBuilder,
			org.springframework.transaction.PlatformTransactionManager txManager) {
		this.parametreService = parametreService;
		this.congeDecompteService = congeDecompteService;
		this.annulationCongeService = annulationCongeService;
		this.champsDecideurSolde = champsDecideurSolde;
		this.auteurResolver = auteurResolver;
		this.demandeRepo = demandeRepo;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.validationService = validationService;
		this.notificationPublisher = notificationPublisher;
		this.workflowHistoryRepository = workflowHistoryRepository;
		this.numeroLisibleService = numeroLisibleService;
		this.workflowActivationService = workflowActivationService;
		this.workflowDemarrageService = workflowDemarrageService;
		this.moteurClient = moteurClient;
		this.variablesBuilder = variablesBuilder;
		if (txManager != null) {
			this.txEcriture = new org.springframework.transaction.support.TransactionTemplate(txManager);
			this.txLecture = new org.springframework.transaction.support.TransactionTemplate(txManager);
			this.txLecture.setReadOnly(true);
		} else {
			this.txEcriture = null;
			this.txLecture = null;
		}
	}

	// ─── Création ─────────────────────────────────────────────────────────────

	public DemandeAdministrativeRhResponse creer(DemandeAdministrativeRhCreationRequest req, Jwt jwt) {
		// D-W5-04 : mode lu AVANT la transaction de création (hors transaction).
		boolean modeMoteur = workflowActivationService != null
				&& workflowActivationService.estMoteur(req.getTypeDemande());

		DemandeAdministrativeRhResponse creee = txEcriture != null
				? txEcriture.execute(status -> creerEnTransaction(req, jwt, modeMoteur))
				: creerEnTransaction(req, jwt, modeMoteur);

		// D-W5-04 : après validation de la transaction (hors transaction), démarrage moteur.
		// La réponse HTTP 201 ne dépend pas du démarrage.
		if (modeMoteur && creee != null && workflowDemarrageService != null) {
			try {
				workflowDemarrageService.demarrer(creee.getIdentifiant());
				DemandeAdministrativeRhResponse maj = txLecture != null
						? txLecture.execute(status -> demandeRepo.findByIdAvecDemandeur(creee.getIdentifiant())
								.map(this::toResponse).orElse(creee))
						: demandeRepo.findById(creee.getIdentifiant()).map(this::toResponse).orElse(creee);
				if (maj != null) {
					return maj;
				}
			} catch (RuntimeException ignored) {
				// Repris par WorkflowDemarrageRepriseScheduler (D-W5-05)
			}
		}
		return creee;
	}

	private DemandeAdministrativeRhResponse creerEnTransaction(
			DemandeAdministrativeRhCreationRequest req, Jwt jwt, boolean modeMoteur) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		String codeConge = validationService.validerContenu(req.getTypeDemande(),
				req.getContenu() != null ? req.getContenu() : new HashMap<>());

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		// D-L02 : numéro attribué dans la transaction de création, rang du mois de l'instant de création
		Instant creation = Instant.now();
		d.setCreeLe(creation);
		d.setNumero(numeroLisibleService.attribuer(PrefixeNumero.DEM, creation));
		d.setTypeDemande(req.getTypeDemande());
		d.setDemandeur(demandeur);
		d.setContenu(new HashMap<>(req.getContenu()));
		if (codeConge != null) {
			// graphie canonique du code, et marque « déjà utilisé » du type de congé (ADR-UAT-lot3 § 6)
			d.getContenu().put("type_conge", codeConge);
			parametreService.marquerUtilisee("TYPE_CONGE", codeConge);
		}
		DemandeAdministrativePeriodeHelper.appliquerPeriodeIndexee(d);
		if (req.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE) {
			// D-S01 / D-S06 : périodes, jours décomptés, solde disponible et chevauchement (verrou du solde)
			congeDecompteService.preparerCreation(demandeur, codeConge, d);
		}

		if (modeMoteur) {
			// D-W5-04 : statut EN_COURS, workflow_mode = MOTEUR, workflow_etat = A_DEMARRER, valideur_attendu null
			d.setWorkflowMode(WorkflowActivationService.MODE_MOTEUR);
			d.setWorkflowEtat(WorkflowDemarrageService.ETAT_A_DEMARRER);
			d.setValideurAttendu(null);
			d.setStatut(StatutDemandeAdministrativeRh.EN_COURS);

			DemandeAdministrativeRh saved = demandeRepo.save(d);
			enregistrerWorkflow(saved, ActionWorkflowAdministratif.CREATION_DEMANDE,
					demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
					commentaireCreation(req.getTypeDemande(), d.getContenu(),
							code -> ParametreService.enMinuscule(parametreService.libelleOuDefaut("TYPE_CONGE", code))));
			// Aucune notification métier du référentiel en mode MOTEUR
			return toResponse(saved);
		}

		Collaborateur valideurAttendu = managerNoeudActif(demandeur);
		d.setValideurAttendu(valideurAttendu);
		d.setStatut(valideurAttendu != null
				? StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				: StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);

		DemandeAdministrativeRh saved = demandeRepo.save(d);

		// Enregistrer l'historique de workflow
		enregistrerWorkflow(saved, ActionWorkflowAdministratif.CREATION_DEMANDE,
			demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
			commentaireCreation(req.getTypeDemande(), d.getContenu(),
				code -> ParametreService.enMinuscule(parametreService.libelleOuDefaut("TYPE_CONGE", code))));
		enregistrerWorkflow(saved, ActionWorkflowAdministratif.SOUMISE_A_RO,
			demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
			"Demande soumise pour validation");

		// Notification : demandeur → valideur retenu (D-H03), ou RH / ADMIN sans valideur (SCRUM-28)
		notificationPublisher.notifierDemandeRecue(demandeur,
				req.getTypeDemande().name(), saved.getId(), saved.getNumero(), valideurAttendu);

		return toResponse(saved);
	}

	// ─── Lecture ──────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> mesDemandes(Jwt jwt,
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
			lignes = demandeRepo.findByDemandeurCouvrantJour(c.getId(), couvreJour, type, statut);
		} else if (type == null) {
			lignes = filtrerStatut(demandeRepo.findByDemandeurIdOrderByCreeLeDesc(c.getId()), statut);
		} else {
			lignes = filtrerStatut(
					demandeRepo.findByDemandeurIdAndTypeDemandeOrderByCreeLeDesc(c.getId(), type), statut);
		}
		return versReponses(lignes);
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> demandesEnAttenteRo(Jwt jwt) {
		Collaborateur manager = collaborateurConnecteService.exigerCollaborateur(jwt);
		return versReponses(demandeRepo.findEnAttenteRoHerite(
				manager.getId(), StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR));
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> listerToutPourRh(
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut, String q) {
		return listerToutPourRh(type, couvreJour, statut, q, null, null);
	}

	/**
	 * D-N01 : en plus des filtres existants, {@code collaborateurId} (demandeur, fiche) et {@code uniteId} (unité du
	 * demandeur, sous-unités comprises). Un identifiant inconnu donne une liste vide.
	 */
	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> listerToutPourRh(
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut, String q, UUID collaborateurId, UUID uniteId) {
		List<DemandeAdministrativeRh> lignes;
		String numero = NumeroLisibleService.normaliserRecherche(q);
		if (numero != null) {
			// D-L02 : recherche par numéro exact, combinable avec les autres filtres
			lignes = demandeRepo.findPourRhByNumero(numero).stream()
					.filter(d -> type == null || d.getTypeDemande() == type)
					.filter(d -> couvreJour == null || couvre(d, couvreJour))
					.collect(Collectors.toList());
			lignes = filtrerStatut(lignes, statut);
		} else if (couvreJour != null) {
			lignes = demandeRepo.findPourRhCouvrantJour(couvreJour, type, statut);
		} else if (type == null) {
			lignes = filtrerStatut(demandeRepo.findAllPourRhOrderByCreeLeDesc(), statut);
		} else {
			lignes = filtrerStatut(demandeRepo.findAllPourRhByTypeDemandeOrderByCreeLeDesc(type), statut);
		}
		return versReponses(lignes.stream()
				.filter(d -> collaborateurId == null || d.getDemandeur().getId().equals(collaborateurId))
				.filter(d -> uniteId == null
						|| SuperieurHierarchieRules.descendDe(d.getDemandeur().getUnite(), uniteId))
				.collect(Collectors.toList()));
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			boolean valideurAttendu = d.getValideurAttendu() != null && d.getValideurAttendu().getId().equals(c.getId());
			if (!d.getDemandeur().getId().equals(c.getId()) && !valideurAttendu) {
				// SCRUM-25 : demande d'un autre collaborateur = refus de droits (403), pas une donnée invalide
				throw new AccessDeniedException("Accès refusé.");
			}
		}
		DemandeAdministrativeRhResponse r = toResponse(d);
		champsDecideurSolde.annoter(d, r);
		return r;
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeSuiviResponse suivi(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				// SCRUM-25 : demande d'un autre collaborateur = refus de droits (403), pas une donnée invalide
				throw new AccessDeniedException("Accès refusé.");
			}
		}
		if (estModeMoteur(d)) {
			return suiviMoteur(d);
		}
		// H2-R06 : étape supérieur = snapshot uniquement (pas de recalcul live → pas d'étape fantôme)
		Collaborateur snapshot = d.getValideurAttendu();
		boolean avecRo = snapshot != null;
		Set<ActionWorkflowAdministratif> actions = EnumSet.noneOf(ActionWorkflowAdministratif.class);
		if (d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE
				|| d.getStatut() == StatutDemandeAdministrativeRh.REFUSEE) {
			// étapes réellement atteintes avant l'annulation / le refus
			workflowHistoryRepository.findByDemandeAdministrativeIdOrderByDateActionAsc(id)
					.forEach(h -> actions.add(h.getAction()));
		}
		DemandeAdministrativeSuiviResponse response = new DemandeAdministrativeSuiviResponse(
				d.getId(), d.getTypeDemande(), d.getStatut(), avecRo,
				etapesAdministratif(d.getStatut(), avecRo, snapshot, actions));
		response.setNumero(d.getNumero());
		response.setWorkflowMode(d.getWorkflowMode());
		response.setWorkflowInstanceId(d.getWorkflowInstanceId());
		response.setEtapeCouranteLibelle(d.getWorkflowEtapeLibelle());
		response.setEcheanceLe(d.getWorkflowEcheanceLe());
		enrichirValideurSuivi(response, snapshot, d.getStatut(), d.getDemandeur());
		return response;
	}

	static final String MSG_SUIVI_MOTEUR = "Votre demande suit le circuit de validation paramétré par la RH.";

	/**
	 * D-W5-08 / D-W5-13 : une demande MOTEUR n'a ni valideur figé ni étapes héritées ; le suivi montre
	 * le dépôt, l'étape courante projetée depuis le moteur et la clôture.
	 */
	private DemandeAdministrativeSuiviResponse suiviMoteur(DemandeAdministrativeRh d) {
		StatutDemandeAdministrativeRh s = d.getStatut();
		boolean enCours = s == StatutDemandeAdministrativeRh.EN_COURS
				|| s == StatutDemandeAdministrativeRh.A_COMPLETER;
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));
		String libelleEtape;
		if (s == StatutDemandeAdministrativeRh.A_COMPLETER) {
			libelleEtape = "Complément demandé";
		} else if (d.getWorkflowEtapeLibelle() != null && !d.getWorkflowEtapeLibelle().isBlank()) {
			libelleEtape = d.getWorkflowEtapeLibelle();
		} else {
			libelleEtape = "Validation en cours";
		}
		// une demande annulée n'a pas franchi l'étape de validation : pas de coche « terminée »
		boolean etapeTerminee = !enCours && s != StatutDemandeAdministrativeRh.ANNULEE;
		etapes.add(new WorkflowEtapeResponse("WORKFLOW", libelleEtape, etapeTerminee, enCours));
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleCloture(s), !enCours, false));
		DemandeAdministrativeSuiviResponse response = new DemandeAdministrativeSuiviResponse(
				d.getId(), d.getTypeDemande(), s, false, etapes);
		response.setNumero(d.getNumero());
		response.setWorkflowMode(d.getWorkflowMode());
		response.setWorkflowInstanceId(d.getWorkflowInstanceId());
		response.setEtapeCouranteLibelle(d.getWorkflowEtapeLibelle());
		response.setEcheanceLe(d.getWorkflowEcheanceLe());
		if (enCours) {
			response.setMessageExplication(MSG_SUIVI_MOTEUR);
		}
		return response;
	}

	private static String libelleCloture(StatutDemandeAdministrativeRh s) {
		return switch (s) {
			case APPROUVEE -> "Approuvée ✓";
			case REFUSEE -> "Refusée";
			case ANNULEE -> "Annulée par le demandeur";
			case SOUMISE, EN_VALIDATION_SUPERIEUR, EN_VALIDATION_RRH, EN_COURS, A_COMPLETER -> "Clôture";
		};
	}

	// ─── Validation RO ────────────────────────────────────────────────────────

	private static final String MESSAGE_DEMANDE_SOUS_WORKFLOW = "Cette demande se traite depuis Mes tâches.";

	private static boolean estModeMoteur(DemandeAdministrativeRh d) {
		return WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode());
	}

	@Transactional
	public DemandeAdministrativeRhResponse validerSuperieur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		if (estModeMoteur(d)) {
			verifierEstRoDuDemandeurEtRetourner(jwt, d);
			throw ReferentielMetierException.conflict("DEMANDE_SOUS_WORKFLOW", MESSAGE_DEMANDE_SOUS_WORKFLOW);
		}
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation du responsable.");
		Collaborateur ro = verifierEstRoDuDemandeurEtRetourner(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);
		// D-S06.a/b : jours recalculés, 422 SOLDE_INSUFFISANT (statut inchangé) si les jours dépassent l'acquis
		congeDecompteService.controlerValidation(d);

		d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.VALIDATION_RO,
			ro.getId(), ro.getPrenom() + " " + ro.getNom(),
			"Validée par le responsable");

		// Notification : RO a validé → RH doit approuver
		notificationPublisher.notifierValidationRo(demandeur, d.getTypeDemande().name(), ro, d.getId(), d.getNumero());

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserSuperieur(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		if (estModeMoteur(d)) {
			verifierEstRoDuDemandeurEtRetourner(jwt, d);
			throw ReferentielMetierException.conflict("DEMANDE_SOUS_WORKFLOW", MESSAGE_DEMANDE_SOUS_WORKFLOW);
		}
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation du responsable.");
		Collaborateur ro = verifierEstRoDuDemandeurEtRetourner(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.REFUS_RO,
			ro.getId(), ro.getPrenom() + " " + ro.getNom(),
			"Refusée par le responsable : " + req.getMotifRefus().trim());

		// Notification : RO a refusé → le demandeur est notifié avec le motif
		notificationPublisher.notifierRefusRo(demandeur, d.getTypeDemande().name(), req.getMotifRefus(), d.getId(), d.getNumero());

		return toResponse(d);
	}

	// ─── Validation RRH ───────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerRrh(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		if (estModeMoteur(d)) {
			verifierPasPropreDemandeRrh(jwt, d);
			throw ReferentielMetierException.conflict("DEMANDE_SOUS_WORKFLOW", MESSAGE_DEMANDE_SOUS_WORKFLOW);
		}
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation de la RH.");
		Collaborateur rh = verifierPasPropreDemandeRrh(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		// D-S06.c / D-W5-07 : contrôle de l'acquis, jours figés, mouvement « Congé pris » et approbation (méthode commune)
		boolean anterieure = DemandeApprobationSupport.approuverSousVerrou(
				d, auteurResolver.depuis(jwt), congeDecompteService, demandeRepo);

		// Enregistrer l'historique (approbateur tracé, SCRUM-24)
		enregistrerWorkflow(d, ActionWorkflowAdministratif.APPROBATION_RRH,
			rh != null ? rh.getId() : null, nomActeurRh(rh, jwt),
			anterieure ? "Approuvée par la RH. " + CongeDecompteService.MENTION_ANTERIEURE : "Approuvée par la RH");

		// Notification : RRH a approuvé → demandeur
		notificationPublisher.notifierApprobationRrh(demandeur, d.getTypeDemande().name(), d.getId(), d.getNumero());

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserRrh(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		if (estModeMoteur(d)) {
			verifierPasPropreDemandeRrh(jwt, d);
			throw ReferentielMetierException.conflict("DEMANDE_SOUS_WORKFLOW", MESSAGE_DEMANDE_SOUS_WORKFLOW);
		}
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation de la RH.");
		Collaborateur rh = verifierPasPropreDemandeRrh(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.REFUS_RRH,
			rh != null ? rh.getId() : null, nomActeurRh(rh, jwt), "Refusée par la RH : " + req.getMotifRefus().trim());

		// Notification : RRH a refusé → demandeur avec motif
		notificationPublisher.notifierRefusRrh(demandeur, d.getTypeDemande().name(), req.getMotifRefus(), d.getId(), d.getNumero());

		return toResponse(d);
	}

	// ─── Annulation par le demandeur (D-W5-11) ────────────────────────────────

	private record ContexteAnnulationMoteur(
			boolean modeMoteur,
			boolean aDemarrer,
			UUID instanceId,
			com.hr.referentiel.client.WorkflowMoteurClient.ActeurInterne acteur
	) {
	}

	public DemandeAdministrativeRhResponse annuler(UUID id, Jwt jwt) {
		if (txLecture == null || moteurClient == null) {
			return annulerHeriteEnTransaction(id, jwt);
		}
		ContexteAnnulationMoteur ctx = txLecture.execute(status -> {
			DemandeAdministrativeRh d = demandeRepo.findByIdAvecDemandeur(id)
					.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new AccessDeniedException("Seul le demandeur peut annuler sa demande.");
			}
			if (!estModeMoteur(d)) {
				return new ContexteAnnulationMoteur(false, false, null, null);
			}
			if (d.getStatut() == StatutDemandeAdministrativeRh.APPROUVEE
					&& d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE) {
				throw new AccessDeniedException("Pour annuler un congé approuvé, contactez les RH.");
			}
			if (d.getStatut() != StatutDemandeAdministrativeRh.EN_COURS
					&& d.getStatut() != StatutDemandeAdministrativeRh.A_COMPLETER) {
				throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
						"Annulation impossible : la demande est « " + d.getStatut().getLibelle() + " ».");
			}
			boolean aDemarrer = WorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getWorkflowInstanceId() == null;
			var acteur = new com.hr.referentiel.client.WorkflowMoteurClient.ActeurInterne(
					c.getCompteUtilisateurId(), c.getId(), nomComplet(c));
			return new ContexteAnnulationMoteur(true, aDemarrer, d.getWorkflowInstanceId(), acteur);
		});

		if (ctx == null || !ctx.modeMoteur()) {
			return txEcriture.execute(status -> annulerHeriteEnTransaction(id, jwt));
		}
		if (ctx.aDemarrer()) {
			return txEcriture.execute(status -> annulerLocalementMoteur(id, jwt));
		}

		// D-W5-11 : appel moteur HORS transaction
		var issue = moteurClient.annuler(ctx.instanceId(), "Annulée par le demandeur", ctx.acteur());
		if (issue == com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation.INSTANCE_TERMINEE) {
			DemandeAdministrativeRh relue = txLecture.execute(status -> charger(id));
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Annulation impossible : la demande est « "
							+ (relue != null ? relue.getStatut().getLibelle() : "déjà décidée") + " ».");
		}
		if (issue == com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation.INDISPONIBLE) {
			throw ReferentielMetierException.serviceUnavailable("SERVICE_INDISPONIBLE",
					"Annulation momentanément impossible, réessayez dans quelques instants.");
		}
		return txEcriture.execute(status -> annulerLocalementMoteur(id, jwt));
	}

	private DemandeAdministrativeRhResponse annulerLocalementMoteur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!d.getDemandeur().getId().equals(c.getId())) {
			throw new AccessDeniedException("Seul le demandeur peut annuler sa demande.");
		}
		if (d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE) {
			return toResponse(d);
		}
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_COURS
				&& d.getStatut() != StatutDemandeAdministrativeRh.A_COMPLETER) {
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Annulation impossible : la demande est « " + d.getStatut().getLibelle() + " ».");
		}
		d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		d.setMotifAnnulation("Annulée par le demandeur");
		demandeRepo.save(d);
		enregistrerWorkflow(d, ActionWorkflowAdministratif.ANNULATION_DEMANDEUR,
				c.getId(), nomComplet(c), "Annulée par le demandeur");
		// Aucune notification métier du référentiel en mode MOTEUR
		return toResponse(d);
	}

	private DemandeAdministrativeRhResponse annulerHeriteEnTransaction(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = chargerPourDecision(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!d.getDemandeur().getId().equals(c.getId())) {
			throw new AccessDeniedException("Seul le demandeur peut annuler sa demande.");
		}
		if (d.getStatut() == StatutDemandeAdministrativeRh.APPROUVEE
				&& d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE) {
			// D-S06.d : seule la RH annule un congé approuvé
			throw new AccessDeniedException("Pour annuler un congé approuvé, contactez les RH.");
		}
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				&& d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_RRH) {
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Annulation impossible : la demande est « " + d.getStatut().getLibelle()
					+ " ». L'annulation n'est possible que tant que la demande est en attente du responsable ou de la RH.");
		}
		Collaborateur demandeur = chargerDemandeurDetail(d);
		d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.ANNULATION_DEMANDEUR,
			c.getId(), c.getPrenom() + " " + c.getNom(),
			"Annulée par le demandeur");

		// Notification : annulation → RO pour info
		notificationPublisher.notifierAnnulationDemandeur(demandeur, d.getTypeDemande().name(), d.getValideurAttendu(), d.getId(), d.getNumero());

		return toResponse(d);
	}

	// ─── Complément fourni par le demandeur (D-W5-12) ─────────────────────────

	private record ContexteComplement(
			UUID instanceId,
			com.hr.referentiel.client.WorkflowMoteurClient.ActeurInterne acteur
	) {
	}

	private record ResultatComplementTx(
			DemandeAdministrativeRhResponse reponse,
			Map<String, Object> variables
	) {
	}

	public DemandeAdministrativeRhResponse fournirComplement(UUID id, DemandeComplementRequest req, Jwt jwt) {
		if (txLecture == null || moteurClient == null) {
			throw ReferentielMetierException.serviceUnavailable("SERVICE_INDISPONIBLE",
					"Service de workflow non configuré.");
		}
		String commentaire = req.commentaire().trim();
		ContexteComplement ctx = txLecture.execute(status -> {
			DemandeAdministrativeRh d = demandeRepo.findByIdAvecDemandeur(id)
					.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new AccessDeniedException("Seul le demandeur peut fournir un complément sur sa demande.");
			}
			if (!estModeMoteur(d)
					|| d.getStatut() != StatutDemandeAdministrativeRh.A_COMPLETER
					|| d.getWorkflowInstanceId() == null) {
				throw ReferentielMetierException.conflict("ETAT_INCORRECT",
						"Un complément ne peut être fourni que sur une demande en attente de complément.");
			}
			var acteur = new com.hr.referentiel.client.WorkflowMoteurClient.ActeurInterne(
					c.getCompteUtilisateurId(), c.getId(), nomComplet(c));
			return new ContexteComplement(d.getWorkflowInstanceId(), acteur);
		});

		// D-W5-12 : appel moteur HORS transaction (peut rappeler l'endpoint d'effet du référentiel)
		var issue = moteurClient.complementFourni(ctx.instanceId(), ctx.acteur(), commentaire);
		if (issue == com.hr.referentiel.client.WorkflowMoteurClient.IssueComplement.ETAT_INCORRECT) {
			throw ReferentielMetierException.conflict("ETAT_INCORRECT",
					"La demande n'est plus en attente de complément.");
		}
		if (issue == com.hr.referentiel.client.WorkflowMoteurClient.IssueComplement.INDISPONIBLE) {
			throw ReferentielMetierException.serviceUnavailable("SERVICE_INDISPONIBLE",
					"Envoi du complément momentanément impossible, réessayez dans quelques instants.");
		}

		ResultatComplementTx res = txEcriture.execute(status -> {
			DemandeAdministrativeRh d = chargerPourDecision(id);
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			// Si elle est encore A_COMPLETER → EN_COURS ; sinon déjà décidée par un effet pendant la réactivation
			if (d.getStatut() == StatutDemandeAdministrativeRh.A_COMPLETER) {
				d.setStatut(StatutDemandeAdministrativeRh.EN_COURS);
			}
			if (req.piecesJointes() != null && !req.piecesJointes().isEmpty()) {
				ajouterPiecesJointes(d, req.piecesJointes());
			}
			demandeRepo.save(d);
			enregistrerWorkflow(d, ActionWorkflowAdministratif.COMPLEMENT_FOURNI,
					c.getId(), nomComplet(c), commentaire);
			Map<String, Object> vars = variablesBuilder != null ? variablesBuilder.construire(d) : null;
			return new ResultatComplementTx(toResponse(d), vars);
		});

		// Recalcul et envoi des variables HORS transaction
		if (res != null && res.variables() != null) {
			moteurClient.majVariables(ctx.instanceId(), res.variables());
		}
		return res != null ? res.reponse() : null;
	}

	private static void ajouterPiecesJointes(DemandeAdministrativeRh d, List<Object> nouvelles) {
		Map<String, Object> contenu = d.getContenu() != null ? new HashMap<>(d.getContenu()) : new HashMap<>();
		List<Object> liste = new ArrayList<>();
		Object existant = contenu.get("pieces_jointes");
		if (existant instanceof Collection<?> col) {
			liste.addAll(col);
		} else if (existant != null && !String.valueOf(existant).isBlank()) {
			liste.add(existant);
		}
		for (Object pj : nouvelles) {
			if (pj != null) {
				liste.add(pj);
			}
		}
		contenu.put("pieces_jointes", liste);
		d.setContenu(contenu);
	}

	// ─── Annulation par la RH d'un congé approuvé (D4) ───────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse annulerApresApprobation(UUID id, String motif, Jwt jwt) {
		DemandeAdministrativeRh d = annulationCongeService.annuler(id, motif, jwt);
		DemandeAdministrativeRhResponse r = toResponse(d);
		champsDecideurSolde.annoter(d, r);
		return r;
	}

	// ─── Helpers privés ───────────────────────────────────────────────────────

	private List<DemandeAdministrativeRhResponse> versReponses(List<DemandeAdministrativeRh> lignes) {
		List<DemandeAdministrativeRhResponse> reponses = lignes.stream().map(this::toResponse).collect(Collectors.toList());
		champsDecideurSolde.annoter(lignes, reponses);
		return reponses;
	}

	/** Premier verrou de l'ordre « demande puis solde » : le statut est lu après le verrou (ADR § 4). */
	private DemandeAdministrativeRh chargerPourDecision(UUID id) {
		return demandeRepo.findByIdForUpdate(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
	}

	private Collaborateur exigerCollaborateurDetail(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return collaborateurRepository.findDetailById(c.getId())
				.orElseThrow(() -> new IllegalStateException("Collaborateur introuvable."));
	}

	private Collaborateur chargerDemandeurDetail(DemandeAdministrativeRh d) {
		return collaborateurRepository.findDetailById(d.getDemandeur().getId())
				.orElseThrow(() -> new IllegalStateException("Demandeur introuvable."));
	}

	private DemandeAdministrativeRh charger(UUID id) {
		return demandeRepo.findById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
	}

	private static void verifierStatut(DemandeAdministrativeRh d,
			StatutDemandeAdministrativeRh attendu, String msg) {
		if (d.getStatut() != attendu) {
			boolean decidee = d.getStatut() == StatutDemandeAdministrativeRh.APPROUVEE
					|| d.getStatut() == StatutDemandeAdministrativeRh.REFUSEE
					|| d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE;
			throw ReferentielMetierException.conflict(
					decidee ? "DEMANDE_DEJA_DECIDEE" : "ETAPE_INCORRECTE",
					(decidee ? "Cette demande est déjà décidée." : msg)
							+ " Statut actuel : « " + d.getStatut().getLibelle() + " ».");
		}
	}

	/**
	 * Vérifie que le connecté est le manager ACTIF du nœud (snapshot ou live) — sinon 403.
	 * Le JWT {@code RO} d'un autre nœud ne suffit pas.
	 */
	private Collaborateur verifierEstRoDuDemandeurEtRetourner(Jwt jwt, DemandeAdministrativeRh d) {
		Collaborateur connecte = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur attendu = resoudreValideurAttendu(d);
		// D-H03 : jamais sa propre demande, même avec un snapshot antérieur à la règle
		if (connecte.getId().equals(d.getDemandeur().getId())) {
			throw new AccessDeniedException("Vous ne pouvez pas valider votre propre demande.");
		}
		if (attendu == null || !attendu.getId().equals(connecte.getId())) {
			throw new AccessDeniedException(
					"Seul le manager actif du nœud d'unité du demandeur peut valider cette étape.");
		}
		if (!"ACTIF".equalsIgnoreCase(connecte.getStatut())) {
			throw new AccessDeniedException(
					"Seul le manager actif du nœud d'unité du demandeur peut valider cette étape.");
		}
		return connecte;
	}

	/**
	 * Snapshot stocké à la création ; repli live pour demandes legacy sans snapshot.
	 */
	private Collaborateur resoudreValideurAttendu(DemandeAdministrativeRh d) {
		if (d.getValideurAttendu() != null) {
			return d.getValideurAttendu();
		}
		return managerNoeudActif(chargerDemandeurDetail(d));
	}

	/**
	 * Manager ACTIF du nœud d'affectation — source de vérité M01 (pas le champ fiche superieur).
	 * Si le demandeur est lui-même ce manager, remontée au premier ancêtre managé (D-H03, SCRUM-23).
	 */
	private Collaborateur managerNoeudActif(Collaborateur demandeur) {
		return SuperieurHierarchieRules.resoudreValideurM01(demandeur);
	}

	/** D-H03 (SCRUM-24) : un RH / ADMIN ne traite pas l'étape RRH de sa propre demande. */
	private Collaborateur verifierPasPropreDemandeRrh(Jwt jwt, DemandeAdministrativeRh d) {
		Collaborateur connecte = collaborateurConnecteService.resoudre(jwt).orElse(null);
		if (connecte != null && connecte.getId().equals(d.getDemandeur().getId())) {
			throw new AccessDeniedException(
					"Vous ne pouvez pas traiter votre propre demande : un autre RH ou un administrateur doit la traiter.");
		}
		return connecte;
	}

	private static String nomActeurRh(Collaborateur connecte, Jwt jwt) {
		// D-L01.a : jamais d'identifiant technique en repli (le « sujet » du jeton est un identifiant de compte)
		return connecte != null ? connecte.getPrenom() + " " + connecte.getNom() + " (RH)" : "Ressources humaines";
	}

	private static boolean couvre(DemandeAdministrativeRh d, LocalDate jour) {
		return d.getPeriodeDebut() != null && d.getPeriodeFin() != null
				&& !d.getPeriodeDebut().isAfter(jour) && !d.getPeriodeFin().isBefore(jour);
	}

	/** D-L01.b : commentaire d'historique sans code, ex. « Demande de congé annuel créée ». */
	static String commentaireCreation(TypeDemandeAdministrativeRh type, Map<String, Object> contenu,
			java.util.function.Function<String, String> libelleConge) {
		if (type == TypeDemandeAdministrativeRh.CONGE && contenu != null
				&& contenu.get("type_conge") instanceof String brut) {
			try {
				String libelle = libelleConge.apply(brut);
				if (libelle != null && !libelle.isBlank()) {
					return "Demande de " + libelle + " créée";
				}
			} catch (IllegalArgumentException ignore) {
				// type de congé non reconnu : libellé générique ci-dessous
			}
		}
		return "Demande " + type.getLibelleDemande() + " créée";
	}

	private static List<DemandeAdministrativeRh> filtrerStatut(
			List<DemandeAdministrativeRh> lignes, StatutDemandeAdministrativeRh s) {
		return s == null ? lignes
				: lignes.stream().filter(d -> d.getStatut() == s).collect(Collectors.toList());
	}

	private static final String LIBELLE_ROLE_VALIDEUR = "Responsable de service";
	private static final String MSG_SKIP_RRH =
			"Aucun responsable actif sur votre unité : votre demande est directement en validation RH.";
	private static final String MSG_ATTENTE_RO = "En attente du responsable de votre service.";
	// D-H03 : le demandeur est le responsable de son unité → validation par le niveau au-dessus
	private static final String LIBELLE_ROLE_VALIDEUR_REMONTEE = "Responsable hiérarchique";
	private static final String MSG_ATTENTE_REMONTEE =
			"Vous êtes responsable de votre unité : votre demande est validée par votre responsable hiérarchique.";
	private static final String MSG_SKIP_RRH_REMONTEE =
			"Aucun responsable au-dessus de vous : votre demande est directement en validation RH.";

	private static void enrichirValideurSuivi(DemandeAdministrativeSuiviResponse response,
			Collaborateur snapshot, StatutDemandeAdministrativeRh statut, Collaborateur demandeur) {
		boolean demandeurEstManager = demandeur.getUnite() != null && demandeur.getUnite().getManager() != null
				&& demandeur.getUnite().getManager().getId().equals(demandeur.getId());
		response.setValideurAttenduLibelleRole(
				demandeurEstManager ? LIBELLE_ROLE_VALIDEUR_REMONTEE : LIBELLE_ROLE_VALIDEUR);
		if (snapshot != null) {
			response.setValideurAttenduIdentifiant(snapshot.getId());
			response.setValideurAttenduMatricule(snapshot.getMatricule());
			response.setValideurAttenduNomComplet(nomComplet(snapshot));
			if (statut == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR) {
				response.setMessageExplication(demandeurEstManager ? MSG_ATTENTE_REMONTEE : MSG_ATTENTE_RO);
			}
		} else {
			response.setMessageExplication(demandeurEstManager ? MSG_SKIP_RRH_REMONTEE : MSG_SKIP_RRH);
		}
	}

	private static String nomComplet(Collaborateur c) {
		String prenom = c.getPrenom() != null ? c.getPrenom().trim() : "";
		String nom = c.getNom() != null ? c.getNom().trim() : "";
		String full = (prenom + " " + nom).trim();
		return full.isEmpty() ? null : full;
	}

	private static List<WorkflowEtapeResponse> etapesAdministratif(
			StatutDemandeAdministrativeRh s, boolean avecRo, Collaborateur snapshot,
			Set<ActionWorkflowAdministratif> actions) {
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));
		if (avecRo) {
			boolean enCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			boolean terminee = s != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
					&& s != StatutDemandeAdministrativeRh.SOUMISE;
			if (s == StatutDemandeAdministrativeRh.ANNULEE) {
				// annulée avant la décision du responsable : étape non atteinte
				terminee = actions.contains(ActionWorkflowAdministratif.VALIDATION_RO)
						|| actions.contains(ActionWorkflowAdministratif.REFUS_RO);
			}
			String nom = snapshot != null ? nomComplet(snapshot) : null;
			String libelleRo = nom != null
					? "Validation — " + nom
					: "Validation — responsable de service";
			etapes.add(new WorkflowEtapeResponse("RO", libelleRo, terminee, enCours));
		}
		boolean rrhEnCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH;
		// la décision de la RH n'a eu lieu que pour une demande approuvée ou refusée par la RH
		boolean rrhTerminee = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| (s == StatutDemandeAdministrativeRh.REFUSEE && !actions.contains(ActionWorkflowAdministratif.REFUS_RO));
		boolean cloturee = rrhTerminee || s == StatutDemandeAdministrativeRh.ANNULEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE;
		etapes.add(new WorkflowEtapeResponse("RRH", "Approbation RH", rrhTerminee, rrhEnCours));
		String libelleFin = libelleCloture(s);
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleFin, cloturee, false));
		return etapes;
	}

	private DemandeAdministrativeRhResponse toResponse(DemandeAdministrativeRh d) {
		DemandeAdministrativeRhResponse r = new DemandeAdministrativeRhResponse(
				d.getId(), d.getTypeDemande(), d.getDemandeur().getId(), d.getStatut(),
				d.getContenu(), d.getPeriodeDebut(), d.getPeriodeFin(),
				d.getMotifRefus(), d.getCreeLe(), d.getModifieLe());
		r.setNumero(d.getNumero());
		r.setStatutLibelle(parametreService.libelleOuInconnu("STATUT_DEMANDE", d.getStatut().name()));
		enrichirConge(r, d);
		if (d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE && d.getContenu() != null
				&& d.getContenu().get("type_conge") != null) {
			r.setTypeCongeLibelle(parametreService.libelleOuInconnu("TYPE_CONGE",
					String.valueOf(d.getContenu().get("type_conge"))));
		}
		Collaborateur demandeur = d.getDemandeur();
		r.setDemandeurNom(nomComplet(demandeur));
		r.setDemandeurMatricule(demandeur.getMatricule());
		r.setDemandeurUnite(demandeur.getUnite() != null ? demandeur.getUnite().getLibelle() : null);
		Collaborateur snapshot = d.getValideurAttendu();
		if (snapshot != null) {
			r.setValideurAttenduIdentifiant(snapshot.getId());
			r.setValideurAttenduNomComplet(nomComplet(snapshot));
		}
		r.setWorkflowMode(d.getWorkflowMode());
		r.setWorkflowInstanceId(d.getWorkflowInstanceId());
		r.setEtapeCouranteLibelle(d.getWorkflowEtapeLibelle());
		r.setEcheanceLe(d.getWorkflowEcheanceLe());
		return r;
	}

	/** Jours décomptés, périodes (avec libellés) et annulation après approbation d'un congé (annexe A). */
	private static void enrichirConge(DemandeAdministrativeRhResponse r, DemandeAdministrativeRh d) {
		if (d.getTypeDemande() != TypeDemandeAdministrativeRh.CONGE) {
			return;
		}
		r.setJoursDecomptes(d.getJoursDecomptes());
		if (d.getContenu() != null && CongeDecompteService.estAnnuel(d)) {
			Object premier = d.getContenu().get(CongeDecompteService.CLE_PREMIER);
			Object dernier = d.getContenu().get(CongeDecompteService.CLE_DERNIER);
			if (premier != null) {
				r.setPeriodePremierJour(String.valueOf(premier));
				r.setPeriodePremierJourLibelle(libellePeriode(premier));
			}
			if (dernier != null) {
				r.setPeriodeDernierJour(String.valueOf(dernier));
				r.setPeriodeDernierJourLibelle(libellePeriode(dernier));
			}
		}
		boolean annulee = d.getAnnuleApresApprobationLe() != null;
		r.setAnnuleeApresApprobation(annulee);
		if (annulee) {
			r.setMotifAnnulation(d.getMotifAnnulation());
			r.setAnnuleApresApprobationLe(d.getAnnuleApresApprobationLe());
			r.setAnnuleParNom(d.getAnnuleParNom());
			r.setJoursRecredites(d.getJoursDecomptes());
		}
	}

	private static String libellePeriode(Object brut) {
		try {
			return com.hr.referentiel.domain.PeriodeJour.valueOf(String.valueOf(brut)).getLibelle();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ─── Workflow History ─────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<DemandeAdminWorkflowHistoryResponse> obtenirHistorique(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				// SCRUM-25 : demande d'un autre collaborateur = refus de droits (403), pas une donnée invalide
				throw new AccessDeniedException("Accès refusé.");
			}
		}
		return workflowHistoryRepository.findByDemandeAdministrativeIdOrderByDateActionAsc(id).stream()
				.map(this::toWorkflowHistoryResponse)
				.collect(Collectors.toList());
	}

	private void enregistrerWorkflow(DemandeAdministrativeRh demande, ActionWorkflowAdministratif action,
			UUID acteurId, String acteurNom, String commentaire) {
		DemandeAdminWorkflowHistory history = new DemandeAdminWorkflowHistory();
		history.setDemandeAdministrative(demande);
		history.setAction(action);
		history.setActeurIdentifiant(acteurId);
		history.setActeurNom(acteurNom);
		history.setCommentaire(commentaire);
		workflowHistoryRepository.save(history);
	}

	private DemandeAdminWorkflowHistoryResponse toWorkflowHistoryResponse(DemandeAdminWorkflowHistory h) {
		DemandeAdminWorkflowHistoryResponse r = new DemandeAdminWorkflowHistoryResponse();
		r.setIdentifiant(h.getId());
		r.setAction(h.getAction());
		r.setEtapeLibelle(h.getAction() != null ? h.getAction().getLibelle() : null);
		r.setActeurIdentifiant(h.getActeurIdentifiant());
		r.setActeurNom(h.getActeurNom());
		r.setCommentaire(h.getCommentaire());
		r.setDateAction(h.getDateAction());
		return r;
	}
}
