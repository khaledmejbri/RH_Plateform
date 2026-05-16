package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowFormation;
import com.hr.referentiel.domain.CibleDemandeFormationRh;
import com.hr.referentiel.domain.OrigineDemandeFormationRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.dto.DemandeFormationCreationRequest;
import com.hr.referentiel.dto.DemandeFormationIntegrationRequest;
import com.hr.referentiel.dto.DemandeFormationRhResponse;
import com.hr.referentiel.dto.DemandeRefusRequest;
import com.hr.referentiel.dto.FormationCollaborateurCibleResponse;
import com.hr.referentiel.dto.FormationWorkflowHistoryResponse;
import com.hr.referentiel.dto.UniteResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.FormationWorkflowHistory;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DemandeFormationRhService {

	private final DemandeFormationRhRepository demandeFormationRepository;
	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final RhNotificationPublisher notificationPublisher;
	private final FormationWorkflowHistoryRepository workflowHistoryRepository;

	public DemandeFormationRhService(
			DemandeFormationRhRepository demandeFormationRepository,
			CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			RhNotificationPublisher notificationPublisher,
			FormationWorkflowHistoryRepository workflowHistoryRepository) {
		this.demandeFormationRepository = demandeFormationRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.notificationPublisher = notificationPublisher;
		this.workflowHistoryRepository = workflowHistoryRepository;
	}

	@Transactional
	public DemandeFormationRhResponse creer(DemandeFormationCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		validerDates(req);

		DemandeFormationRh demande = new DemandeFormationRh();
		demande.setDemandeur(demandeur);
		demande.setTypeFormation(trim(req.getTypeFormation()));
		demande.setOrganisme(trim(req.getOrganisme()));
		demande.setDureeHeures(req.getDureeHeures());
		demande.setCoutEstime(req.getCoutEstime());
		demande.setObjectifsPedagogiques(trim(req.getObjectifsPedagogiques()));
		demande.setJustification(trim(req.getJustification()));
		demande.setDateSouhaiteeDebut(req.getDateSouhaiteeDebut());
		demande.setDateSouhaiteeFin(req.getDateSouhaiteeFin());
		appliquerCible(req, demandeur, demande);

		DemandeFormationRh saved = demandeFormationRepository.save(demande);
		
		// Enregistrer l'historique de workflow
		enregistrerWorkflow(saved, ActionWorkflowFormation.CREATION_DEMANDE,
				demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
				"Demande de formation créée");
		enregistrerWorkflow(saved, ActionWorkflowFormation.SOUMISE_A_RH,
				demandeur.getId(), demandeur.getPrenom() + " " + demandeur.getNom(),
				"Demande soumise au RH pour validation");
		
		// Notifier le RH et les collaborateurs cibles si RO
		notificationPublisher.notifierDemandeFormationSoumise(saved.getDemandeur(), saved.getTypeFormation());
		if (saved.getOrigine() == OrigineDemandeFormationRh.RESPONSABLE_OPERATIONNEL 
				&& saved.getCollaborateursCibles() != null && !saved.getCollaborateursCibles().isEmpty()) {
			notifierCollaborateursCibles(saved);
		}
		
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<DemandeFormationRhResponse> mesDemandes(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return demandeFormationRepository.findByDemandeurIdOrderByCreeLeDesc(c.getId()).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeFormationRhResponse> listerPourRh(StatutDemandeFormationRh statut) {
		List<DemandeFormationRh> lignes = statut == null
				? demandeFormationRepository.findAllByOrderByCreeLeDesc()
				: demandeFormationRepository.findByStatutOrderByCreeLeDesc(statut);
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeFormationRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeFormationRh demande = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!demande.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Acces refuse.");
			}
		}
		return toResponse(demande);
	}

	@Transactional(readOnly = true)
	public List<UniteResponse> unitesCibles(Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		if (!estChefDepartement(demandeur)) {
			throw new IllegalArgumentException("Seul un chef de departement peut cibler une unite.");
		}
		UUID uniteChefId = demandeur.getUnite().getId();
		return uniteRepository.findByActifTrueOrderByCodeAsc().stream()
				.filter(u -> u.getId().equals(uniteChefId)
						|| (u.getParent() != null && u.getParent().getId().equals(uniteChefId)))
				.map(this::toUniteResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<FormationCollaborateurCibleResponse> collaborateursCibles(Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		if (!estResponsableOperationnel(demandeur)) {
			throw new IllegalArgumentException("Seul un responsable operationnel peut cibler des collaborateurs.");
		}
		return collaborateurRepository.findByUniteId(demandeur.getUnite().getId()).stream()
				.filter(c -> !c.getId().equals(demandeur.getId()))
				.filter(c -> "ACTIF".equalsIgnoreCase(c.getStatut()))
				.map(c -> new FormationCollaborateurCibleResponse(c.getId(),
						c.getPrenom() + " " + c.getNom(), c.getMatricule()))
				.collect(Collectors.toList());
	}

	@Transactional
	public DemandeFormationRhResponse integrerPlan(UUID id, DemandeFormationIntegrationRequest req) {
		DemandeFormationRh demande = charger(id);
		verifierStatut(demande, StatutDemandeFormationRh.EN_VALIDATION_RRH,
				"Cette demande de formation n'est pas en attente RH.");
		demande.setStatut(StatutDemandeFormationRh.INTEGREE_PLAN);
		demande.setCommentaireRh(trimToNull(req != null ? req.getCommentaireRh() : null));
		demandeFormationRepository.save(demande);
		
		// Enregistrer l'historique
		enregistrerWorkflow(demande, ActionWorkflowFormation.INTEGRATION_PLAN,
				null, "RH", "Intégrée au plan annuel" + (req != null && req.getCommentaireRh() != null ? ": " + req.getCommentaireRh() : ""));
		
		notificationPublisher.notifierDemandeFormationIntegree(demande.getDemandeur(), demande.getTypeFormation());
		return toResponse(demande);
	}

	@Transactional
	public DemandeFormationRhResponse refuser(UUID id, DemandeRefusRequest req) {
		DemandeFormationRh demande = charger(id);
		verifierStatut(demande, StatutDemandeFormationRh.EN_VALIDATION_RRH,
				"Cette demande de formation n'est pas en attente RH.");
		demande.setStatut(StatutDemandeFormationRh.REFUSEE);
		demande.setCommentaireRh(req.getMotifRefus().trim());
		demandeFormationRepository.save(demande);
		
		// Enregistrer l'historique
		enregistrerWorkflow(demande, ActionWorkflowFormation.REFUS_RRH,
				null, "RH", "Refusée: " + req.getMotifRefus());
		
		notificationPublisher.notifierDemandeFormationRefusee(demande.getDemandeur(), demande.getTypeFormation(),
				req.getMotifRefus());
		return toResponse(demande);
	}

	@Transactional
	public DemandeFormationRhResponse annuler(UUID id, Jwt jwt) {
		DemandeFormationRh demande = charger(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!demande.getDemandeur().getId().equals(c.getId())) {
			throw new IllegalArgumentException("Seul le demandeur peut annuler sa demande de formation.");
		}
		verifierStatut(demande, StatutDemandeFormationRh.EN_VALIDATION_RRH,
				"Annulation possible uniquement avant traitement RH.");
		demande.setStatut(StatutDemandeFormationRh.ANNULEE);
		demandeFormationRepository.save(demande);
		
		// Enregistrer l'historique
		enregistrerWorkflow(demande, ActionWorkflowFormation.ANNULATION_DEMANDEUR,
				c.getId(), c.getPrenom() + " " + c.getNom(), "Annulée par le demandeur");
		
		notificationPublisher.notifierDemandeFormationAnnulee(demande.getDemandeur(), demande.getTypeFormation());
		return toResponse(demande);
	}

	@Transactional(readOnly = true)
	public List<FormationWorkflowHistoryResponse> obtenirHistorique(UUID id, Jwt jwt, boolean rh) {
		DemandeFormationRh demande = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			// Le demandeur ou les collaborateurs cibles peuvent voir l'historique
			boolean estCible = demande.getCollaborateursCibles() != null 
				&& demande.getCollaborateursCibles().contains(c.getId());
			if (!demande.getDemandeur().getId().equals(c.getId()) && !estCible) {
				throw new IllegalArgumentException("Acces refuse.");
			}
		}
		return workflowHistoryRepository.findByDemandeFormationIdOrderByDateActionAsc(id).stream()
				.map(this::toWorkflowHistoryResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeFormationRhResponse> mesDemandesEnTantQueRo(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!estResponsableOperationnel(c)) {
			throw new IllegalArgumentException("Seul un responsable operationnel peut acceder a cette liste.");
		}
		return demandeFormationRepository.findByDemandeurIdOrderByCreeLeDesc(c.getId()).stream()
				.filter(d -> d.getOrigine() == OrigineDemandeFormationRh.RESPONSABLE_OPERATIONNEL)
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeFormationRhResponse> formationsOuJeSuisCible(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return demandeFormationRepository.findAllByOrderByCreeLeDesc().stream()
				.filter(d -> d.getCollaborateursCibles() != null && d.getCollaborateursCibles().contains(c.getId()))
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	private void notifierCollaborateursCibles(DemandeFormationRh demande) {
		if (demande.getCollaborateursCibles() == null || demande.getCollaborateursCibles().isEmpty()) {
			return;
		}
		List<Collaborateur> collaborateurs = collaborateurRepository.findAllById(demande.getCollaborateursCibles());
		for (Collaborateur collab : collaborateurs) {
			notificationPublisher.notifierCollaborateurInviteFormation(
				collab,
				demande.getTypeFormation(),
				demande.getOrganisme(),
				demande.getDateSouhaiteeDebut(),
				demande.getDateSouhaiteeFin()
			);
			enregistrerWorkflow(demande, ActionWorkflowFormation.NOTIFICATION_ENVOYEE,
				collab.getId(), collab.getPrenom() + " " + collab.getNom(),
				"Notification d'invitation envoyée");
		}
	}

	private void enregistrerWorkflow(DemandeFormationRh demande, ActionWorkflowFormation action,
			UUID acteurId, String acteurNom, String commentaire) {
		FormationWorkflowHistory history = new FormationWorkflowHistory();
		history.setDemandeFormation(demande);
		history.setAction(action);
		history.setActeurIdentifiant(acteurId);
		history.setActeurNom(acteurNom);
		history.setCommentaire(commentaire);
		workflowHistoryRepository.save(history);
	}

	private FormationWorkflowHistoryResponse toWorkflowHistoryResponse(FormationWorkflowHistory h) {
		FormationWorkflowHistoryResponse r = new FormationWorkflowHistoryResponse();
		r.setIdentifiant(h.getId());
		r.setAction(h.getAction());
		r.setActeurIdentifiant(h.getActeurIdentifiant());
		r.setActeurNom(h.getActeurNom());
		r.setCommentaire(h.getCommentaire());
		r.setDateAction(h.getDateAction());
		return r;
	}

	private void appliquerCible(DemandeFormationCreationRequest req, Collaborateur demandeur,
			DemandeFormationRh demande) {
		if (estChefDepartement(demandeur)) {
			if (req.getUniteCibleIdentifiant() == null) {
				throw new IllegalArgumentException("L'unite cible est obligatoire pour un chef de departement.");
			}
			UniteOrganisation uniteCible = uniteRepository.findById(req.getUniteCibleIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Unite cible introuvable."));
			if (!uniteAppartientAuDepartement(uniteCible, demandeur.getUnite())) {
				throw new IllegalArgumentException("Cette unite n'appartient pas au departement du demandeur.");
			}
			demande.setOrigine(OrigineDemandeFormationRh.CHEF_DEPARTEMENT);
			demande.setCible(CibleDemandeFormationRh.UNITE);
			demande.setUniteCible(uniteCible);
			demande.setCollaborateursCibles(new LinkedHashSet<>());
			return;
		}

		if (estResponsableOperationnel(demandeur)) {
			Set<UUID> ids = req.getCollaborateursCiblesIdentifiants();
			if (ids == null || ids.isEmpty()) {
				throw new IllegalArgumentException("Au moins un collaborateur cible est obligatoire pour un RO.");
			}
			List<Collaborateur> collaborateurs = collaborateurRepository.findAllById(ids);
			if (collaborateurs.size() != ids.size()) {
				throw new IllegalArgumentException("Un ou plusieurs collaborateurs cibles sont introuvables.");
			}
			UUID uniteRoId = demandeur.getUnite().getId();
			for (Collaborateur c : collaborateurs) {
				if (c.getUnite() == null || !c.getUnite().getId().equals(uniteRoId)) {
					throw new IllegalArgumentException("Les collaborateurs cibles doivent appartenir a l'unite du RO.");
				}
			}
			demande.setOrigine(OrigineDemandeFormationRh.RESPONSABLE_OPERATIONNEL);
			demande.setCible(CibleDemandeFormationRh.COLLABORATEURS);
			demande.setCollaborateursCibles(new LinkedHashSet<>(ids));
			return;
		}

		throw new IllegalArgumentException(
				"Seuls un chef de departement ou un responsable operationnel peuvent creer une demande de formation.");
	}

	private boolean uniteAppartientAuDepartement(UniteOrganisation uniteCible, UniteOrganisation uniteChef) {
		if (uniteChef == null) return false;
		return uniteCible.getId().equals(uniteChef.getId())
				|| (uniteCible.getParent() != null && uniteCible.getParent().getId().equals(uniteChef.getId()));
	}

	private Collaborateur exigerCollaborateurDetail(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return collaborateurRepository.findDetailById(c.getId())
				.orElseThrow(() -> new IllegalStateException("Collaborateur introuvable."));
	}

	private DemandeFormationRh charger(UUID id) {
		return demandeFormationRepository.findDetailById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande de formation introuvable : " + id));
	}

	private static void verifierStatut(DemandeFormationRh demande, StatutDemandeFormationRh attendu, String message) {
		if (demande.getStatut() != attendu) {
			throw new IllegalArgumentException(message + " Statut actuel : " + demande.getStatut());
		}
	}

	private static void validerDates(DemandeFormationCreationRequest req) {
		if (req.getDateSouhaiteeDebut() != null && req.getDateSouhaiteeFin() != null
				&& req.getDateSouhaiteeFin().isBefore(req.getDateSouhaiteeDebut())) {
			throw new IllegalArgumentException("La date de fin souhaitee doit etre apres la date de debut.");
		}
	}

	private static boolean estChefDepartement(Collaborateur c) {
		return "RESPONSABLE".equalsIgnoreCase(c.getProfilAcces());
	}

	private static boolean estResponsableOperationnel(Collaborateur c) {
		String profil = c.getProfilAcces();
		return "RO".equalsIgnoreCase(profil) || "RESPONSABLE_OPERATIONNEL".equalsIgnoreCase(profil);
	}

	private DemandeFormationRhResponse toResponse(DemandeFormationRh demande) {
		DemandeFormationRhResponse r = new DemandeFormationRhResponse();
		r.setIdentifiant(demande.getId());
		r.setDemandeurIdentifiant(demande.getDemandeur().getId());
		r.setDemandeurNom(demande.getDemandeur().getPrenom() + " " + demande.getDemandeur().getNom());
		r.setOrigine(demande.getOrigine());
		r.setCible(demande.getCible());
		if (demande.getUniteCible() != null) {
			r.setUniteCibleIdentifiant(demande.getUniteCible().getId());
			r.setUniteCibleLibelle(demande.getUniteCible().getLibelle());
		}
		r.setCollaborateursCiblesIdentifiants(demande.getCollaborateursCibles());
		r.setTypeFormation(demande.getTypeFormation());
		r.setOrganisme(demande.getOrganisme());
		r.setDureeHeures(demande.getDureeHeures());
		r.setCoutEstime(demande.getCoutEstime());
		r.setObjectifsPedagogiques(demande.getObjectifsPedagogiques());
		r.setJustification(demande.getJustification());
		r.setDateSouhaiteeDebut(demande.getDateSouhaiteeDebut());
		r.setDateSouhaiteeFin(demande.getDateSouhaiteeFin());
		r.setStatut(demande.getStatut());
		r.setCommentaireRh(demande.getCommentaireRh());
		r.setCreeLe(demande.getCreeLe());
		r.setModifieLe(demande.getModifieLe());
		return r;
	}

	private UniteResponse toUniteResponse(UniteOrganisation u) {
		return new UniteResponse(
				u.getId(),
				u.getCode(),
				u.getLibelle(),
				u.getParent() != null ? u.getParent().getId() : null,
				u.isActif(),
				u.getCreeLe(),
				u.getModifieLe());
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

	private static String trimToNull(String value) {
		String trimmed = trim(value);
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}
}
