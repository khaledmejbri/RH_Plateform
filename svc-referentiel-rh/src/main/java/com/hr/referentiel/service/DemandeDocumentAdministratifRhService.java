package com.hr.referentiel.service;

import lombok.extern.slf4j.Slf4j;
import com.hr.referentiel.config.ReferentielEvenementsProperties;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.kafka.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DemandeDocumentAdministratifRhService {

	public static final String REGLE_FIFO = "FIFO_PREMIER_ARRIVE_PREMIER_SERVI";

	private final DemandeDocumentAdministratifRhRepository repository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final ReferentielEvenementsProperties evenementsProperties;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;


	public DemandeDocumentAdministratifRhService(DemandeDocumentAdministratifRhRepository repository,
			CollaborateurConnecteService collaborateurConnecteService,
			ReferentielEvenementsProperties evenementsProperties,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.evenementsProperties = evenementsProperties;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
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
			NotificationMessage notification = new NotificationMessage("WEBSOCKET", "RH", "Nouvelle demande de document", "Nouvelle demande de type " + type.name() + " soumise par " + demandeur.getId());
			kafkaTemplate.send("notifications-topic", objectMapper.writeValueAsString(notification));
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
	public DemandeDocumentAdministratifRhResponse marquerDisponible(UUID id, DemandeDocumentDisponibleRequest req) {

		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH) {
			throw new IllegalArgumentException("La demande doit être en traitement RH.");
		}
		d.setStatut(StatutDocumentAdministratifDemandeRh.DISPONIBLE);
		d.setReferenceLivrable(req.getReferenceLivrable().trim());
		if (req.getCommentaireRh() != null) {
			d.setCommentaireRh(req.getCommentaireRh().trim());
		}
		DemandeDocumentAdministratifRh saved = repository.save(d);

		try {
			NotificationMessage notification = new NotificationMessage("WEBSOCKET", d.getDemandeur().getId().toString(), "Document disponible", "Votre document demandé est maintenant validé et disponible.");
			kafkaTemplate.send("notifications-topic", objectMapper.writeValueAsString(notification));
		} catch (Exception e) {
			log.error("Erreur lors de l'envoi de la notification Kafka (Document disponible)", e);
		}

		return toResponse(saved);
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse rejeter(UUID id, DocumentRejetRhRequest req) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			throw new IllegalArgumentException("Impossible de rejeter cette demande dans son état actuel.");
		}
		d.setStatut(StatutDocumentAdministratifDemandeRh.REJETEE);
		d.setCommentaireRh(req.getMotif().trim());
		return toResponse(repository.save(d));
	}

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

	private DemandeDocumentAdministratifRhResponse toResponse(DemandeDocumentAdministratifRh d) {
		Integer rang = null;
		if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			long avant = repository.countByStatutAndCreeLeBefore(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, d.getCreeLe());
			rang = (int) avant + 1;
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
				enRetard);
	}
}
