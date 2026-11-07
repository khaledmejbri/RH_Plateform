package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.domain.TypeConge;
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

	public DemandeAdministrativeRhService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService,
			RhNotificationPublisher notificationPublisher,
			DemandeAdminWorkflowHistoryRepository workflowHistoryRepository,
			NumeroLisibleService numeroLisibleService) {
		this.demandeRepo = demandeRepo;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.validationService = validationService;
		this.notificationPublisher = notificationPublisher;
		this.workflowHistoryRepository = workflowHistoryRepository;
		this.numeroLisibleService = numeroLisibleService;
	}

	// ─── Création ─────────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse creer(DemandeAdministrativeRhCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		validationService.validerContenu(req.getTypeDemande(),
				req.getContenu() != null ? req.getContenu() : new HashMap<>());

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		// D-L02 : numéro attribué dans la transaction de création, rang du mois de l'instant de création
		Instant creation = Instant.now();
		d.setCreeLe(creation);
		d.setNumero(numeroLisibleService.attribuer(PrefixeNumero.DEM, creation));
		d.setTypeDemande(req.getTypeDemande());
		d.setDemandeur(demandeur);
		d.setContenu(new HashMap<>(req.getContenu()));
		DemandeAdministrativePeriodeHelper.appliquerPeriodeIndexee(d);

		Collaborateur valideurAttendu = managerNoeudActif(demandeur);
		d.setValideurAttendu(valideurAttendu);
		d.setStatut(valideurAttendu != null
				? StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				: StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);

		DemandeAdministrativeRh saved = demandeRepo.save(d);

		// Enregistrer l'historique de workflow
		enregistrerWorkflow(saved, ActionWorkflowAdministratif.CREATION_DEMANDE,
			demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
			commentaireCreation(req.getTypeDemande(), d.getContenu()));
		enregistrerWorkflow(saved, ActionWorkflowAdministratif.SOUMISE_A_RO,
			demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
			"Demande soumise pour validation");

		// Notification : demandeur → valideur retenu (D-H03), ou RH / ADMIN sans valideur (SCRUM-28)
		notificationPublisher.notifierDemandeRecue(demandeur,
				req.getTypeDemande().name(), saved.getNumero(), valideurAttendu);

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
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> demandesEnAttenteRo(Jwt jwt) {
		Collaborateur manager = collaborateurConnecteService.exigerCollaborateur(jwt);
		return demandeRepo.findByValideurAttenduIdAndStatutOrderByCreeLeDesc(
						manager.getId(), StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> listerToutPourRh(
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut, String q) {
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
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				// SCRUM-25 : demande d'un autre collaborateur = refus de droits (403), pas une donnée invalide
				throw new AccessDeniedException("Accès refusé.");
			}
		}
		return toResponse(d);
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
		enrichirValideurSuivi(response, snapshot, d.getStatut(), d.getDemandeur());
		return response;
	}

	// ─── Validation RO ────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerSuperieur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation du responsable.");
		Collaborateur ro = verifierEstRoDuDemandeurEtRetourner(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.VALIDATION_RO,
			ro.getId(), ro.getPrenom() + " " + ro.getNom(),
			"Validée par le responsable");

		// Notification : RO a validé → RH doit approuver
		notificationPublisher.notifierValidationRo(demandeur, d.getTypeDemande().name(), ro, d.getNumero());

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserSuperieur(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
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
		notificationPublisher.notifierRefusRo(demandeur, d.getTypeDemande().name(), req.getMotifRefus(), d.getNumero());

		return toResponse(d);
	}

	// ─── Validation RRH ───────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerRrh(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation de la RH.");
		Collaborateur rh = verifierPasPropreDemandeRrh(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		demandeRepo.save(d);

		// Enregistrer l'historique (approbateur tracé, SCRUM-24)
		enregistrerWorkflow(d, ActionWorkflowAdministratif.APPROBATION_RRH,
			rh != null ? rh.getId() : null, nomActeurRh(rh, jwt), "Approuvée par la RH");

		// Notification : RRH a approuvé → demandeur
		notificationPublisher.notifierApprobationRrh(demandeur, d.getTypeDemande().name(), d.getNumero());

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserRrh(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
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
		notificationPublisher.notifierRefusRrh(demandeur, d.getTypeDemande().name(), req.getMotifRefus(), d.getNumero());

		return toResponse(d);
	}

	// ─── Annulation par le demandeur ──────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse annuler(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!d.getDemandeur().getId().equals(c.getId())) {
			throw new AccessDeniedException("Seul le demandeur peut annuler sa demande.");
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
		notificationPublisher.notifierAnnulationDemandeur(demandeur, d.getTypeDemande().name(), d.getValideurAttendu(), d.getNumero());

		return toResponse(d);
	}

	// ─── Helpers privés ───────────────────────────────────────────────────────

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
	static String commentaireCreation(TypeDemandeAdministrativeRh type, Map<String, Object> contenu) {
		if (type == TypeDemandeAdministrativeRh.CONGE && contenu != null
				&& contenu.get("type_conge") instanceof String brut) {
			try {
				return "Demande de " + TypeConge.parse(brut).getLibelle() + " créée";
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
		String libelleFin = switch (s) {
			case APPROUVEE -> "Approuvée ✓";
			case REFUSEE -> "Refusée";
			case ANNULEE -> "Annulée par le demandeur";
			default -> "Clôture";
		};
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleFin, cloturee, false));
		return etapes;
	}

	private DemandeAdministrativeRhResponse toResponse(DemandeAdministrativeRh d) {
		DemandeAdministrativeRhResponse r = new DemandeAdministrativeRhResponse(
				d.getId(), d.getTypeDemande(), d.getDemandeur().getId(), d.getStatut(),
				d.getContenu(), d.getPeriodeDebut(), d.getPeriodeFin(),
				d.getMotifRefus(), d.getCreeLe(), d.getModifieLe());
		r.setNumero(d.getNumero());
		Collaborateur demandeur = d.getDemandeur();
		r.setDemandeurNom(nomComplet(demandeur));
		r.setDemandeurMatricule(demandeur.getMatricule());
		r.setDemandeurUnite(demandeur.getUnite() != null ? demandeur.getUnite().getLibelle() : null);
		Collaborateur snapshot = d.getValideurAttendu();
		if (snapshot != null) {
			r.setValideurAttenduIdentifiant(snapshot.getId());
			r.setValideurAttenduNomComplet(nomComplet(snapshot));
		}
		return r;
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
