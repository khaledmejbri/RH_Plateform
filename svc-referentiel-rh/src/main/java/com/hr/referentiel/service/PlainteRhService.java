package com.hr.referentiel.service;

import com.hr.referentiel.dto.*;
import com.hr.referentiel.kafka.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.PlainteRh;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.repository.PlainteRhRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlainteRhService {

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
		PlainteRh saved = plainteRhRepository.save(p);

		try {
			NotificationMessage notification = new NotificationMessage("WEBSOCKET", "RH", "Nouvelle plainte", "Nouvelle plainte soumise par " + auteur.getId());
			kafkaTemplate.send("notifications-topic", objectMapper.writeValueAsString(notification));
		} catch (Exception e) {
			// ignorer
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
	public List<PlainteRhResponse> listerToutPourRh() {
		return plainteRhRepository.findAllPourRhOrderByCreeLeDesc().stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public PlainteRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		PlainteRh p = plainteRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Plainte introuvable."));
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
		PlainteRh p = plainteRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Plainte introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!p.getAuteur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé à cette plainte.");
			}
		}
		return new PlainteSuiviResponse(p.getId(), p.getTypePlainte(), p.getStatut(), etapesPlainte(p.getStatut()));
	}

	@Transactional
	public PlainteRhResponse mettreAJourStatut(UUID id, PlainteRhStatutMiseAJourRequest req) {
		PlainteRh p = plainteRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Plainte introuvable."));
		p.setStatut(req.getStatut());
		if (req.getCommentaireRh() != null) {
			p.setCommentaireRh(req.getCommentaireRh().trim());
		}
		return toResponse(plainteRhRepository.save(p));
	}

	private static List<WorkflowEtapeResponse> etapesPlainte(StatutPlainteRh courant) {
		List<WorkflowEtapeResponse> list = new ArrayList<>();
		for (StatutPlainteRh s : StatutPlainteRh.values()) {
			String libelle = switch (s) {
				case NOUVEAU -> "Dépôt enregistré";
				case EN_ANALYSE -> "En analyse (RH)";
				case EN_TRAITEMENT -> "En traitement";
				case RESOLU -> "Résolu";
				case FERME -> "Clôturé";
			};
			boolean terminee = courant.ordinal() > s.ordinal();
			boolean enCours = courant == s;
			list.add(new WorkflowEtapeResponse(s.name(), libelle, terminee, enCours));
		}
		return list;
	}

	private PlainteRhResponse toResponse(PlainteRh p) {
		return new PlainteRhResponse(
				p.getId(),
				p.getTypePlainte(),
				p.getAuteur().getId(),
				p.getTitre(),
				p.getDescription(),
				p.getStatut(),
				p.getCommentaireRh(),
				p.getCreeLe(),
				p.getModifieLe());
	}
}
