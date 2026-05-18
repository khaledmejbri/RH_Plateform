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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CDC v2 §M01 — Demandes administratives avec notifications correctes à chaque étape.
 *
 * Chaîne de notification par hiérarchie :
 *   Soumission           → RO de l'unité du demandeur (ou RH si pas de RO)
 *   Validation RO        → tous les RH actifs
 *   Refus RO             → demandeur
 *   Approbation RRH      → demandeur
 *   Refus RRH            → demandeur
 *   Annulation demandeur → RO de l'unité (pour info)
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

		boolean aUnSuperieur = superieurActif(demandeur) != null;
		d.setStatut(aUnSuperieur
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
		Collaborateur superieur = collaborateurConnecteService.exigerCollaborateur(jwt);
		return demandeRepo.findByDemandeurSuperieurIdAndStatutOrderByCreeLeDesc(
						superieur.getId(), StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR).stream()
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
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId())
				.orElseThrow(() -> new IllegalStateException("Collaborateur introuvable."));
		boolean avecRo = superieurActif(demandeur) != null;
		return new DemandeAdministrativeSuiviResponse(d.getId(), d.getTypeDemande(), d.getStatut(),
				avecRo, etapesAdministratif(d.getStatut(), avecRo));
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
	 * Vérifie que le connecté est le RO de l'unité du demandeur et le retourne.
	 */
	private Collaborateur verifierEstRoDuDemandeurEtRetourner(Jwt jwt, DemandeAdministrativeRh d) {
		Collaborateur connecte = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur demandeur = chargerDemandeurDetail(d);
		Collaborateur superieur = superieurActif(demandeur);
		if (superieur == null || !superieur.getId().equals(connecte.getId())) {
			throw new IllegalArgumentException(
					"Seul le Responsable Opérationnel (RO) de l'unité du demandeur peut valider cette étape.");
		}
		return superieur;
	}

	private Collaborateur superieurActif(Collaborateur demandeur) {
		if (demandeur == null || demandeur.getSuperieur() == null) return null;
		Collaborateur superieur = demandeur.getSuperieur();
		if (!"ACTIF".equalsIgnoreCase(superieur.getStatut())) return null;
		return superieur;
	}

	private static List<DemandeAdministrativeRh> filtrerStatut(
			List<DemandeAdministrativeRh> lignes, StatutDemandeAdministrativeRh s) {
		return s == null ? lignes
				: lignes.stream().filter(d -> d.getStatut() == s).collect(Collectors.toList());
	}

	private static List<WorkflowEtapeResponse> etapesAdministratif(
			StatutDemandeAdministrativeRh s, boolean avecRo) {
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));
		if (avecRo) {
			boolean enCours  = s == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			boolean terminee = s != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
					&& s != StatutDemandeAdministrativeRh.SOUMISE;
			etapes.add(new WorkflowEtapeResponse("RO", "Validation Responsable Opérationnel", terminee, enCours));
		}
		boolean rrhEnCours  = s == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH;
		boolean rrhTerminee = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE
				|| s == StatutDemandeAdministrativeRh.ANNULEE;
		etapes.add(new WorkflowEtapeResponse("RRH", "Approbation RRH", rrhTerminee, rrhEnCours));
		String libelleFin = switch (s) {
			case APPROUVEE -> "Approuvée ✓";
			case REFUSEE   -> "Refusée";
			case ANNULEE   -> "Annulée par le demandeur";
			default        -> "Clôture";
		};
		boolean cloture = rrhTerminee;
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleFin, cloture, false));
		return etapes;
	}

	private DemandeAdministrativeRhResponse toResponse(DemandeAdministrativeRh d) {
		return new DemandeAdministrativeRhResponse(
				d.getId(), d.getTypeDemande(), d.getDemandeur().getId(), d.getStatut(),
				d.getContenu(), d.getPeriodeDebut(), d.getPeriodeFin(),
				d.getMotifRefus(), d.getCreeLe(), d.getModifieLe());
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
