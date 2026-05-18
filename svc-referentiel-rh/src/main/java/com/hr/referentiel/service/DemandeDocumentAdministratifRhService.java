package com.hr.referentiel.service;

import lombok.extern.slf4j.Slf4j;
import com.hr.referentiel.config.ReferentielEvenementsProperties;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DemandeDocumentAdministratifRhService {

	public static final String REGLE_FIFO = "FIFO_PREMIER_ARRIVE_PREMIER_SERVI";

	/** Message retourné lorsque le traitement viole l'ordre FIFO sans justification. */
	public static final String MSG_VIOLATION_FIFO =
			"Des demandes antérieures sont encore en attente. "
					+ "Pour traiter cette demande hors ordre FIFO, veuillez fournir une justification obligatoire "
					+ "(champ justification_derogation_fifo).";

	private final DemandeDocumentAdministratifRhRepository repository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final ReferentielEvenementsProperties evenementsProperties;
	private final RhNotificationPublisher notificationPublisher;


	public DemandeDocumentAdministratifRhService(DemandeDocumentAdministratifRhRepository repository,
			CollaborateurConnecteService collaborateurConnecteService,
			ReferentielEvenementsProperties evenementsProperties,
			RhNotificationPublisher notificationPublisher) {
		this.repository = repository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.evenementsProperties = evenementsProperties;
		this.notificationPublisher = notificationPublisher;
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse soumettre(DemandeDocumentAdministratifCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = collaborateurConnecteService.exigerCollaborateur(jwt);
		TypeDocumentAdministratifDemandeRh type = req.getTypeDocument();
		int sla = evenementsProperties.delaiSlaHeuresPourDocument(type.name(), type.getDelaiSlaHeuresDefaut());
		Instant echeance = Instant.now().plus(sla, ChronoUnit.HOURS);

		DemandeDocumentAdministratifRh d = new DemandeDocumentAdministratifRh();
		d.setDemandeur(demandeur);
		d.setTypeDocument(type);
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		d.setDelaiSlaHeures(sla);
		d.setDateEcheanceTraitement(echeance);
		if (req.getCommentaireDemandeur() != null) {
			d.setCommentaireDemandeur(req.getCommentaireDemandeur().trim());
		}
		d = repository.save(d);

		try {
			notificationPublisher.notifierDemandeDocumentRecue(demandeur, type.name());
		} catch (Exception e) {
			log.error("Erreur lors de l'envoi de la notification Kafka (Nouvelle demande)", e);
		}

		return toResponse(d);
	}

	@Transactional(readOnly = true)
	public List<DemandeDocumentAdministratifRhResponse> mesDemandes(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return repository.findByDemandeurIdOrderByCreeLeDesc(c.getId()).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeDocumentAdministratifRhResponse> fileAttentePourRh() {
		return repository.findByStatutInOrderByCreeLeAsc(List.of(
				StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE,
				StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
		)).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeDocumentAdministratifRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		return toResponse(d);
	}

	@Transactional(readOnly = true)
	public DemandeDocumentSuiviResponse suivi(UUID id, Jwt jwt, boolean rh) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		Integer rang = null;
		int devant = 0;
		if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			long avant = repository.countByStatutAndCreeLeBefore(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, d.getCreeLe());
			devant = (int) avant;
			rang = devant + 1;
		}
		boolean enRetard = d.getStatut() != StatutDocumentAdministratifDemandeRh.DISPONIBLE
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.REJETEE
				&& Instant.now().isAfter(d.getDateEcheanceTraitement());
		return new DemandeDocumentSuiviResponse(
				d.getId(),
				d.getTypeDocument(),
				d.getStatut(),
				REGLE_FIFO,
				rang,
				d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE ? devant : null,
				d.getDelaiSlaHeures(),
				d.getDateEcheanceTraitement(),
				enRetard,
				etapesDocument(d.getStatut()));
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse prendreProchaineDeLaFile() {
		if (repository.existsByStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH)) {
			throw new IllegalArgumentException("Vous devez terminer le traitement en cours avant de prendre une nouvelle demande.");
		}

		List<DemandeDocumentAdministratifRh> file = repository
				.findQueueForUpdate(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		if (file.isEmpty()) {
			throw new IllegalArgumentException("Aucune demande en file d'attente.");
		}
		DemandeDocumentAdministratifRh d = file.get(0);
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);
		return toResponse(repository.save(d));
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse marquerDisponible(UUID id, DemandeDocumentDisponibleRequest req, Jwt jwt) {

		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			throw new IllegalArgumentException("La demande doit être en traitement RH ou en file d'attente.");
		}

		// ── Validation FIFO ──────────────────────────────────────────────────
		validerOrdreFifo(d, req.getJustificationDerogationFifo(), jwt);

		d.setStatut(StatutDocumentAdministratifDemandeRh.DISPONIBLE);
		d.setReferenceLivrable(req.getReferenceLivrable().trim());
		if (req.getCommentaireRh() != null) {
			d.setCommentaireRh(req.getCommentaireRh().trim());
		}
		DemandeDocumentAdministratifRh saved = repository.save(d);

		try {
			notificationPublisher.notifierDocumentDisponible(d.getDemandeur(), d.getTypeDocument().name(), req.getReferenceLivrable());
		} catch (Exception e) {
			log.error("Erreur lors de l'envoi de la notification Kafka (Document disponible)", e);
		}

		return toResponse(saved);
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse rejeter(UUID id, DocumentRejetRhRequest req, Jwt jwt) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			throw new IllegalArgumentException("Impossible de rejeter cette demande dans son état actuel.");
		}

		// ── Validation FIFO ──────────────────────────────────────────────────
		validerOrdreFifo(d, req.getJustificationDerogationFifo(), jwt);

		d.setStatut(StatutDocumentAdministratifDemandeRh.REJETEE);
		d.setCommentaireRh(req.getMotif().trim());
		return toResponse(repository.save(d));
	}

	// ─── FIFO Validation ─────────────────────────────────────────────────────

	/**
	 * Vérifie l'ordre FIFO avant de traiter une demande.
	 *
	 * <p>Règle métier : la plus ancienne demande en attente doit toujours être traitée en premier.
	 * Si la demande courante n'est pas la plus ancienne, le RH doit fournir une justification
	 * obligatoire pour déroger à l'ordre FIFO (cas prioritaire exceptionnel).</p>
	 *
	 * @param demande                 la demande que le RH tente de traiter
	 * @param justificationDerogation justification de la dérogation FIFO (nullable)
	 * @param jwt                     le token du RH effectuant l'action
	 * @throws IllegalStateException si l'ordre FIFO est violé sans justification
	 */
	void validerOrdreFifo(DemandeDocumentAdministratifRh demande, String justificationDerogation, Jwt jwt) {
		// Si le RH tente de traiter une nouvelle demande alors qu'il y en a déjà une en cours, on bloque.
		if (demande.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			if (repository.existsByStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH)) {
				throw new IllegalStateException("Vous devez terminer le traitement en cours avant de prendre une nouvelle demande.");
			}
		}

		Optional<DemandeDocumentAdministratifRh> plusAncienne = repository
				.findFirstByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);

		// S'il n'y a aucune demande en attente, ou si la demande courante EST la plus ancienne → OK
		// Pas besoin de donnée justificative pour le premier
		if (plusAncienne.isEmpty() || plusAncienne.get().getId().equals(demande.getId())) {
			return;
		}

		// La demande n'est pas la prochaine dans l'ordre FIFO
		// → une justification est obligatoire
		if (justificationDerogation == null || justificationDerogation.isBlank()) {
			throw new IllegalStateException(MSG_VIOLATION_FIFO);
		}

		// Justification fournie → enregistrer la dérogation
		demande.setJustificationDerogationFifo(justificationDerogation.trim());
		
		Collaborateur rhActeur = null;
		if (jwt != null) {
			try {
				rhActeur = collaborateurConnecteService.exigerCollaborateur(jwt);
				demande.setDerogationFifoPar(rhActeur.getId());
			} catch (Exception e) {
				log.warn("Impossible d'identifier le RH acteur pour la dérogation FIFO", e);
			}
		}

		log.info("Dérogation FIFO autorisée pour la demande {} — justification : {}",
				demande.getId(), justificationDerogation.trim());

		// Envoi de la notification au DRH (supérieur du RH)
		if (rhActeur != null) {
			try {
				notificationPublisher.notifierDerogationFifo(
						rhActeur, 
						demande.getTypeDocument().name(), 
						justificationDerogation.trim(), 
						demande.getDemandeur()
				);
			} catch (Exception e) {
				log.error("Erreur lors de l'envoi de la notification au DRH pour dérogation FIFO", e);
			}
		}
	}

	// ─── Workflow Steps ──────────────────────────────────────────────────────

	private static List<WorkflowEtapeResponse> etapesDocument(StatutDocumentAdministratifDemandeRh s) {
		List<WorkflowEtapeResponse> list = new ArrayList<>();
		list.add(new WorkflowEtapeResponse("TRANSMISE_RRH", "Demande transmise au RRH (file unique)", true, false));

		boolean fileTerminee = s == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				|| s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		boolean fileEnCours = s == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE;
		list.add(new WorkflowEtapeResponse("FILE_ATTENTE",
				"En file d'attente — ordre chronologique (premier arrivé, premier servi)", fileTerminee, fileEnCours));

		boolean traitTerminee = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		boolean traitEnCours = s == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH;
		list.add(new WorkflowEtapeResponse("TRAITEMENT_RH", "Traitement par le RRH", traitTerminee, traitEnCours));

		boolean cloture = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		String libelle = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE ? "Document disponible"
				: s == StatutDocumentAdministratifDemandeRh.REJETEE ? "Demande refusée par le RRH" : "Clôture";
		list.add(new WorkflowEtapeResponse("CLOTURE", libelle, cloture, false));

		return list;
	}

	// ─── Response Mapping ────────────────────────────────────────────────────

	private DemandeDocumentAdministratifRhResponse toResponse(DemandeDocumentAdministratifRh d) {
		Integer rang = null;
		boolean estProchaineFifo = false;

		if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			long avant = repository.countByStatutAndCreeLeBefore(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, d.getCreeLe());
			rang = (int) avant + 1;
			estProchaineFifo = (rang == 1);
		} else if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH) {
			// Une demande en traitement a déjà passé le contrôle FIFO, elle ne doit pas déclencher d'alerte
			estProchaineFifo = true;
			rang = 1;
		}

		boolean enRetard = d.getStatut() != StatutDocumentAdministratifDemandeRh.DISPONIBLE
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.REJETEE
				&& Instant.now().isAfter(d.getDateEcheanceTraitement());

		return new DemandeDocumentAdministratifRhResponse(
				d.getId(),
				d.getDemandeur().getId(),
				d.getTypeDocument(),
				d.getStatut(),
				REGLE_FIFO,
				d.getDelaiSlaHeures(),
				d.getDateEcheanceTraitement(),
				d.getCommentaireDemandeur(),
				d.getCommentaireRh(),
				d.getReferenceLivrable(),
				d.getCreeLe(),
				d.getModifieLe(),
				rang,
				enRetard,
				estProchaineFifo,
				d.getJustificationDerogationFifo(),
				d.getDerogationFifoPar());
	}
}
