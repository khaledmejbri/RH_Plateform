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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	public DemandeAdministrativeRhService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService,
			RhNotificationPublisher notificationPublisher,
			DemandeAdminWorkflowHistoryRepository workflowHistoryRepository) {
		this.demandeRepo = demandeRepo;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.validationService = validationService;
		this.notificationPublisher = notificationPublisher;
		this.workflowHistoryRepository = workflowHistoryRepository;
	}

	// ─── Création ─────────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse creer(DemandeAdministrativeRhCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		validationService.validerContenu(req.getTypeDemande(),
				req.getContenu() != null ? req.getContenu() : new HashMap<>());

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
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
			"Demande " + req.getTypeDemande().name() + " créée");
		enregistrerWorkflow(saved, ActionWorkflowAdministratif.SOUMISE_A_RO,
			demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
			"Demande soumise pour validation");

		// Notification : demandeur → RO (ou RH si pas de RO)
		notificationPublisher.notifierDemandeRecue(demandeur,
				req.getTypeDemande().name(), saved.getId().toString());

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
			StatutDemandeAdministrativeRh statut) {
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
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
				throw new IllegalArgumentException("Accès refusé.");
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
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		// H2-R06 : étape supérieur = snapshot uniquement (pas de recalcul live → pas d'étape fantôme)
		Collaborateur snapshot = d.getValideurAttendu();
		boolean avecRo = snapshot != null;
		DemandeAdministrativeSuiviResponse response = new DemandeAdministrativeSuiviResponse(
				d.getId(), d.getTypeDemande(), d.getStatut(), avecRo,
				etapesAdministratif(d.getStatut(), avecRo, snapshot));
		enrichirValideurSuivi(response, snapshot, d.getStatut());
		return response;
	}

	// ─── Validation RO ────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerSuperieur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation RO.");
		Collaborateur ro = verifierEstRoDuDemandeurEtRetourner(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.VALIDATION_RO,
			ro.getId(), ro.getPrenom() + " " + ro.getNom(),
			"Validée par le RO");

		// Notification : RO a validé → RH doit approuver
		notificationPublisher.notifierValidationRo(demandeur, d.getTypeDemande().name(), ro);

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserSuperieur(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation RO.");
		Collaborateur ro = verifierEstRoDuDemandeurEtRetourner(jwt, d);
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.REFUS_RO,
			ro.getId(), ro.getPrenom() + " " + ro.getNom(),
			"Refusée par le RO: " + req.getMotifRefus());

		// Notification : RO a refusé → le demandeur est notifié avec le motif
		notificationPublisher.notifierRefusRo(demandeur, d.getTypeDemande().name(), req.getMotifRefus());

		return toResponse(d);
	}

	// ─── Validation RRH ───────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerRrh(UUID id) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation RRH.");
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.APPROBATION_RRH,
			null, "RH", "Approuvée par le RH");

		// Notification : RRH a approuvé → demandeur
		notificationPublisher.notifierApprobationRrh(demandeur, d.getTypeDemande().name());

		return toResponse(d);
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserRrh(UUID id, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation RRH.");
		Collaborateur demandeur = chargerDemandeurDetail(d);

		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.REFUS_RRH,
			null, "RH", "Refusée par le RH: " + req.getMotifRefus());

		// Notification : RRH a refusé → demandeur avec motif
		notificationPublisher.notifierRefusRrh(demandeur, d.getTypeDemande().name(), req.getMotifRefus());

		return toResponse(d);
	}

	// ─── Annulation par le demandeur ──────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse annuler(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!d.getDemandeur().getId().equals(c.getId())) {
			throw new IllegalArgumentException("Seul le demandeur peut annuler sa demande.");
		}
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				&& d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_RRH) {
			throw new IllegalArgumentException(
					"Annulation impossible au statut " + d.getStatut()
					+ ". Possible si EN_VALIDATION_SUPERIEUR ou EN_VALIDATION_RRH.");
		}
		Collaborateur demandeur = chargerDemandeurDetail(d);
		d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		demandeRepo.save(d);

		// Enregistrer l'historique
		enregistrerWorkflow(d, ActionWorkflowAdministratif.ANNULATION_DEMANDEUR,
			c.getId(), c.getPrenom() + " " + c.getNom(),
			"Annulée par le demandeur");

		// Notification : annulation → RO pour info
		notificationPublisher.notifierAnnulationDemandeur(demandeur, d.getTypeDemande().name());

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
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
	}

	private static void verifierStatut(DemandeAdministrativeRh d,
			StatutDemandeAdministrativeRh attendu, String msg) {
		if (d.getStatut() != attendu) {
			throw new IllegalArgumentException(msg + " Statut actuel : " + d.getStatut());
		}
	}

	/**
	 * Vérifie que le connecté est le manager ACTIF du nœud (snapshot ou live) — sinon 403.
	 * Le JWT {@code RO} d'un autre nœud ne suffit pas.
	 */
	private Collaborateur verifierEstRoDuDemandeurEtRetourner(Jwt jwt, DemandeAdministrativeRh d) {
		Collaborateur connecte = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur attendu = resoudreValideurAttendu(d);
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

	/** Manager ACTIF du nœud d'unité d'affectation — source de vérité M01 (pas le champ fiche superieur). */
	private Collaborateur managerNoeudActif(Collaborateur demandeur) {
		if (demandeur == null || demandeur.getUnite() == null || demandeur.getUnite().getManager() == null) {
			return null;
		}
		Collaborateur manager = demandeur.getUnite().getManager();
		if (!"ACTIF".equalsIgnoreCase(manager.getStatut())) {
			return null;
		}
		return manager;
	}

	private static List<DemandeAdministrativeRh> filtrerStatut(
			List<DemandeAdministrativeRh> lignes, StatutDemandeAdministrativeRh s) {
		return s == null ? lignes
				: lignes.stream().filter(d -> d.getStatut() == s).collect(Collectors.toList());
	}

	private static final String LIBELLE_ROLE_VALIDEUR = "Responsable de service";
	private static final String MSG_SKIP_RRH =
			"Aucun responsable actif sur votre unité : votre demande est directement en validation RRH.";
	private static final String MSG_ATTENTE_RO = "En attente du responsable de votre service.";

	private static void enrichirValideurSuivi(DemandeAdministrativeSuiviResponse response,
			Collaborateur snapshot, StatutDemandeAdministrativeRh statut) {
		response.setValideurAttenduLibelleRole(LIBELLE_ROLE_VALIDEUR);
		if (snapshot != null) {
			response.setValideurAttenduIdentifiant(snapshot.getId());
			response.setValideurAttenduMatricule(snapshot.getMatricule());
			response.setValideurAttenduNomComplet(nomComplet(snapshot));
			if (statut == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR) {
				response.setMessageExplication(MSG_ATTENTE_RO);
			}
		} else {
			response.setMessageExplication(MSG_SKIP_RRH);
		}
	}

	private static String nomComplet(Collaborateur c) {
		String prenom = c.getPrenom() != null ? c.getPrenom().trim() : "";
		String nom = c.getNom() != null ? c.getNom().trim() : "";
		String full = (prenom + " " + nom).trim();
		return full.isEmpty() ? null : full;
	}

	private static List<WorkflowEtapeResponse> etapesAdministratif(
			StatutDemandeAdministrativeRh s, boolean avecRo, Collaborateur snapshot) {
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));
		if (avecRo) {
			boolean enCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			boolean terminee = s != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
					&& s != StatutDemandeAdministrativeRh.SOUMISE;
			String nom = snapshot != null ? nomComplet(snapshot) : null;
			String libelleRo = nom != null
					? "Validation — " + nom
					: "Validation — responsable de service";
			etapes.add(new WorkflowEtapeResponse("RO", libelleRo, terminee, enCours));
		}
		boolean rrhEnCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH;
		boolean rrhTerminee = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE
				|| s == StatutDemandeAdministrativeRh.ANNULEE;
		etapes.add(new WorkflowEtapeResponse("RRH", "Approbation RRH", rrhTerminee, rrhEnCours));
		String libelleFin = switch (s) {
			case APPROUVEE -> "Approuvée ✓";
			case REFUSEE -> "Refusée";
			case ANNULEE -> "Annulée par le demandeur";
			default -> "Clôture";
		};
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleFin, rrhTerminee, false));
		return etapes;
	}

	private DemandeAdministrativeRhResponse toResponse(DemandeAdministrativeRh d) {
		DemandeAdministrativeRhResponse r = new DemandeAdministrativeRhResponse(
				d.getId(), d.getTypeDemande(), d.getDemandeur().getId(), d.getStatut(),
				d.getContenu(), d.getPeriodeDebut(), d.getPeriodeFin(),
				d.getMotifRefus(), d.getCreeLe(), d.getModifieLe());
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
				throw new IllegalArgumentException("Accès refusé.");
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
		r.setActeurIdentifiant(h.getActeurIdentifiant());
		r.setActeurNom(h.getActeurNom());
		r.setCommentaire(h.getCommentaire());
		r.setDateAction(h.getDateAction());
		return r;
	}
}
