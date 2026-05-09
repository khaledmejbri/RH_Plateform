package com.hr.referentiel.service;

import com.hr.referentiel.dto.*;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.PlainteRh;
import com.hr.referentiel.kafka.NotificationMessage;
import com.hr.referentiel.repository.PlainteRhRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CDC v2 §M04 :
 * - INTERNE  : notifie RH uniquement.
 * - EXTERNE  : notifie simultanément RH + Services Techniques + Direction E&S.
 * - Transitions de statut validées (pas de saut).
 * - Log d'actions horodaté à chaque changement.
 * - Commentaire RH obligatoire pour RESOLU/FERME.
 */
@Service
@Slf4j
public class PlainteRhService {

	/** Transitions autorisées : clé = statut courant, valeur = statuts cibles valides. */
	private static final Map<StatutPlainteRh, Set<StatutPlainteRh>> TRANSITIONS_AUTORISEES = Map.of(
			StatutPlainteRh.NOUVEAU,       Set.of(StatutPlainteRh.EN_ANALYSE),
			StatutPlainteRh.EN_ANALYSE,    Set.of(StatutPlainteRh.EN_TRAITEMENT),
			StatutPlainteRh.EN_TRAITEMENT, Set.of(StatutPlainteRh.RESOLU),
			StatutPlainteRh.RESOLU,        Set.of(StatutPlainteRh.FERME),
			StatutPlainteRh.FERME,         Set.of()
	);

	private final PlainteRhRepository plainteRhRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	public PlainteRhService(PlainteRhRepository plainteRhRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper) {
		this.plainteRhRepository = plainteRhRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public PlainteRhResponse creer(PlainteRhCreationRequest req, Jwt jwt) {
		Collaborateur auteur = collaborateurConnecteService.exigerCollaborateur(jwt);

		PlainteRh p = new PlainteRh();
		p.setTypePlainte(req.getTypePlainte());
		p.setAuteur(auteur);
		p.setTitre(req.getTitre().trim());
		p.setDescription(req.getDescription().trim());
		p.setStatut(StatutPlainteRh.NOUVEAU);
		p.setPiecesJointes(req.getPiecesJointes() != null ? req.getPiecesJointes() : new ArrayList<>());
		if (req.getTranscriptionAudio() != null && !req.getTranscriptionAudio().isBlank()) {
			p.setTranscriptionAudio(req.getTranscriptionAudio().trim());
		}
		// Initialiser le log
		p.setLogActions(new ArrayList<>());

		PlainteRh saved = plainteRhRepository.save(p);

		// CDC §M04 : notification selon type
		if (req.getTypePlainte() == TypePlainteRh.INTERNE) {
			envoyerNotification("RH", "Nouvelle plainte interne",
					"Plainte [" + saved.getNumeroTicket() + "] déposée par " + auteur.getId());
		} else {
			// EXTERNE : notifier 3 destinataires simultanément
			envoyerNotification("RH", "Nouvelle plainte externe",
					"Plainte externe [" + saved.getNumeroTicket() + "] déposée par " + auteur.getId());
			envoyerNotification("SERVICES_TECHNIQUES", "Nouvelle plainte externe",
					"Plainte externe [" + saved.getNumeroTicket() + "] nécessite votre attention.");
			envoyerNotification("DIRECTION_ENV_SOCIAL", "Nouvelle plainte externe",
					"Plainte externe [" + saved.getNumeroTicket() + "] nécessite votre attention.");
		}

		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<PlainteRhResponse> mesPlaintes(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return plainteRhRepository.findByAuteurIdOrderByCreeLeDesc(c.getId()).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<PlainteRhResponse> listerToutPourRh(TypePlainteRh type, StatutPlainteRh statut) {
		return plainteRhRepository.findAllPourRhOrderByCreeLeDesc().stream()
				.filter(p -> type == null || p.getTypePlainte() == type)
				.filter(p -> statut == null || p.getStatut() == statut)
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public PlainteRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		PlainteRh p = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!p.getAuteur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé à cette plainte.");
			}
		}
		return toResponse(p);
	}

	@Transactional(readOnly = true)
	public PlainteSuiviResponse suivi(UUID id, Jwt jwt, boolean rh) {
		PlainteRh p = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!p.getAuteur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé à cette plainte.");
			}
		}
		return new PlainteSuiviResponse(p.getId(), p.getTypePlainte(), p.getStatut(), etapesPlainte(p.getStatut()));
	}

	@Transactional
	public PlainteRhResponse mettreAJourStatut(UUID id, PlainteRhStatutMiseAJourRequest req, UUID acteurId) {
		PlainteRh p = charger(id);
		StatutPlainteRh ancienStatut = p.getStatut();
		StatutPlainteRh nouveauStatut = req.getStatut();

		// Validation de la transition
		Set<StatutPlainteRh> autorisees = TRANSITIONS_AUTORISEES.getOrDefault(ancienStatut, Set.of());
		if (!autorisees.contains(nouveauStatut)) {
			throw new IllegalArgumentException(
					"Transition interdite : " + ancienStatut + " → " + nouveauStatut
					+ ". Transitions autorisées depuis " + ancienStatut + " : " + autorisees);
		}

		// Commentaire obligatoire pour RESOLU et FERME
		if ((nouveauStatut == StatutPlainteRh.RESOLU || nouveauStatut == StatutPlainteRh.FERME)
				&& (req.getCommentaireRh() == null || req.getCommentaireRh().isBlank())) {
			throw new IllegalArgumentException(
					"Un commentaire RH est obligatoire pour passer au statut " + nouveauStatut + ".");
		}

		p.setStatut(nouveauStatut);
		if (req.getCommentaireRh() != null && !req.getCommentaireRh().isBlank()) {
			p.setCommentaireRh(req.getCommentaireRh().trim());
		}

		// Log horodaté
		Map<String, String> logEntry = new LinkedHashMap<>();
		logEntry.put("ancien_statut", ancienStatut.name());
		logEntry.put("nouveau_statut", nouveauStatut.name());
		logEntry.put("acteur_id", acteurId != null ? acteurId.toString() : "SYSTEME");
		logEntry.put("horodatage", Instant.now().toString());
		if (req.getCommentaireRh() != null && !req.getCommentaireRh().isBlank()) {
			logEntry.put("commentaire", req.getCommentaireRh().trim());
		}
		List<Map<String, String>> log = new ArrayList<>(p.getLogActions());
		log.add(logEntry);
		p.setLogActions(log);

		PlainteRh saved = plainteRhRepository.save(p);

		// Notifier l'auteur du changement de statut
		envoyerNotification(p.getAuteur().getId().toString(),
				"Mise à jour de votre plainte [" + p.getNumeroTicket() + "]",
				"Statut : " + ancienStatut + " → " + nouveauStatut);

		return toResponse(saved);
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	private PlainteRh charger(UUID id) {
		return plainteRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Plainte introuvable : " + id));
	}

	private void envoyerNotification(String destinataire, String titre, String corps) {
		try {
			NotificationMessage n = new NotificationMessage("WEBSOCKET", destinataire, titre, corps);
			kafkaTemplate.send("notifications-topic", objectMapper.writeValueAsString(n));
		} catch (Exception e) {
			log.error("Erreur notification Kafka vers {} : {}", destinataire, e.getMessage());
		}
	}

	private static List<WorkflowEtapeResponse> etapesPlainte(StatutPlainteRh courant) {
		List<WorkflowEtapeResponse> list = new ArrayList<>();
		for (StatutPlainteRh s : StatutPlainteRh.values()) {
			String libelle = switch (s) {
				case NOUVEAU      -> "Dépôt enregistré";
				case EN_ANALYSE   -> "En analyse (RH)";
				case EN_TRAITEMENT -> "En traitement";
				case RESOLU       -> "Résolu";
				case FERME        -> "Clôturé (archivé)";
			};
			boolean terminee = courant.ordinal() > s.ordinal();
			boolean enCours  = courant == s;
			list.add(new WorkflowEtapeResponse(s.name(), libelle, terminee, enCours));
		}
		return list;
	}

	private PlainteRhResponse toResponse(PlainteRh p) {
		return new PlainteRhResponse(
				p.getId(),
				p.getNumeroTicket(),
				p.getTypePlainte(),
				p.getAuteur().getId(),
				p.getTitre(),
				p.getDescription(),
				p.getPiecesJointes(),
				p.getTranscriptionAudio(),
				p.getStatut(),
				p.getCommentaireRh(),
				p.getLogActions(),
				p.getCreeLe(),
				p.getModifieLe());
	}
}
