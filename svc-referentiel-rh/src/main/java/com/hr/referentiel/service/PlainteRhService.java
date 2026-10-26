package com.hr.referentiel.service;

import com.hr.referentiel.dto.*;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.PlainteRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.PlainteRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class PlainteRhService {

	private static final Logger log = LoggerFactory.getLogger(PlainteRhService.class);

	/** Transitions autorisées : clé = statut courant, valeur = statuts cibles valides. */
	private static final Map<StatutPlainteRh, Set<StatutPlainteRh>> TRANSITIONS_AUTORISEES = Map.of(
			StatutPlainteRh.NOUVEAU,       Set.of(StatutPlainteRh.EN_ANALYSE),
			StatutPlainteRh.EN_ANALYSE,    Set.of(StatutPlainteRh.EN_TRAITEMENT),
			StatutPlainteRh.EN_TRAITEMENT, Set.of(StatutPlainteRh.RESOLU),
			StatutPlainteRh.RESOLU,        Set.of(StatutPlainteRh.FERME),
			StatutPlainteRh.FERME,         Set.of()
	);

	static final String ACTION_VALIDATION_RO = "VALIDATION_RO";
	static final String ACTION_CHANGEMENT_STATUT = "CHANGEMENT_STATUT";

	private final PlainteRhRepository plainteRhRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final RhNotificationPublisher notificationPublisher;

	public PlainteRhService(PlainteRhRepository plainteRhRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			RhNotificationPublisher notificationPublisher) {
		this.plainteRhRepository = plainteRhRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.notificationPublisher = notificationPublisher;
	}

	@Transactional
	public PlainteRhResponse creer(PlainteRhCreationRequest req, Jwt jwt, boolean peutDeclarerExterne) {
		// § 9.1 / C-M04-01 : auteur d'une plainte EXTERNE = RO / RH (pas le collaborateur lambda).
		if (req.getTypePlainte() == TypePlainteRh.EXTERNE && !peutDeclarerExterne) {
			throw new ReferentielMetierException("PLAINTE_EXTERNE_NON_AUTORISEE",
					"Seul un responsable opérationnel ou la RH peut déclarer une plainte externe.", 403);
		}
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

		// CDC §M04 : notification selon type via RhNotificationPublisher
		if (req.getTypePlainte() == TypePlainteRh.INTERNE) {
			notificationPublisher.notifierPlainteInterne(auteur, saved.getNumeroTicket());
		} else {
			notificationPublisher.notifierPlainteExterne(auteur, saved.getNumeroTicket());
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
	public PlainteRhResponse validerPlainteExterneParRo(UUID id, Jwt jwt) {
		PlainteRh p = charger(id);
		if (p.getTypePlainte() != TypePlainteRh.EXTERNE) {
			throw new IllegalArgumentException("Seules les plaintes externes passent par une validation RO.");
		}
		if (p.getStatut() != StatutPlainteRh.NOUVEAU) {
			throw new IllegalArgumentException("Cette plainte externe n'est pas en attente de validation RO.");
		}

		Collaborateur ro = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur auteur = p.getAuteur();
		Collaborateur roAttendu = auteur.getSuperieur();
		if (roAttendu == null || !"ACTIF".equalsIgnoreCase(roAttendu.getStatut())
				|| !roAttendu.getId().equals(ro.getId())) {
			throw new IllegalArgumentException(
					"Seul le supérieur direct du demandeur peut valider cette plainte.");
		}

		p.setStatut(StatutPlainteRh.EN_ANALYSE);
		List<Map<String, String>> log = new ArrayList<>(
				p.getLogActions() != null ? p.getLogActions() : List.of());
		Map<String, String> logEntry = new LinkedHashMap<>();
		logEntry.put("ancien_statut", StatutPlainteRh.NOUVEAU.name());
		logEntry.put("nouveau_statut", StatutPlainteRh.EN_ANALYSE.name());
		logEntry.put("action", ACTION_VALIDATION_RO);
		logEntry.put("acteur_id", ro.getId().toString());
		logEntry.put("acteur_nom", nomComplet(ro));
		logEntry.put("horodatage", Instant.now().toString());
		logEntry.put("commentaire", "Validée par le RO et transmise au RH");
		log.add(logEntry);
		p.setLogActions(log);

		PlainteRh saved = plainteRhRepository.save(p);
		notificationPublisher.notifierValidationRoPlainteExterne(auteur, p.getNumeroTicket(), ro);
		return toResponse(saved);
	}

	@Transactional
	public PlainteRhResponse mettreAJourStatut(UUID id, PlainteRhStatutMiseAJourRequest req, Jwt jwt) {
		PlainteRh p = charger(id);
		// § 9.2 : qui a changé le statut. Le `sub` du JWT est le matricule, pas un UUID (SCRUM-13).
		Collaborateur acteur = acteurConnecte(jwt);
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
		logEntry.put("action", ACTION_CHANGEMENT_STATUT);
		logEntry.put("acteur_id", acteur != null ? acteur.getId().toString() : jwt.getSubject());
		logEntry.put("acteur_nom", acteur != null ? nomComplet(acteur) : jwt.getSubject());
		logEntry.put("horodatage", Instant.now().toString());
		if (req.getCommentaireRh() != null && !req.getCommentaireRh().isBlank()) {
			logEntry.put("commentaire", req.getCommentaireRh().trim());
		}
		List<Map<String, String>> log = new ArrayList<>(p.getLogActions());
		log.add(logEntry);
		p.setLogActions(log);

		PlainteRh saved = plainteRhRepository.save(p);

		// Notifier l'auteur du changement de statut
		notificationPublisher.notifierChangementStatutPlainte(
				p.getAuteur(), p.getNumeroTicket(),
				ancienStatut.name(), nouveauStatut.name(), req.getCommentaireRh());

		return toResponse(saved);
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	/** Fiche du RH connecté ; null pour un compte technique sans fiche collaborateur. */
	private Collaborateur acteurConnecte(Jwt jwt) {
		try {
			return collaborateurConnecteService.exigerCollaborateur(jwt);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String nomComplet(Collaborateur c) {
		return (c.getPrenom() + " " + c.getNom()).trim();
	}

	/**
	 * État de l'étape RO d'une plainte EXTERNE (§ 9.2 « RO : action valider-ro ») :
	 * EN_ATTENTE tant que NOUVEAU, VALIDEE si le journal contient la validation RO,
	 * NON_REQUISE si la RH l'a prise en charge directement. Null pour une plainte interne.
	 */
	private static String etatValidationRo(PlainteRh p) {
		if (p.getTypePlainte() != TypePlainteRh.EXTERNE) return null;
		boolean validee = p.getLogActions() != null && p.getLogActions().stream()
				.anyMatch(e -> ACTION_VALIDATION_RO.equals(e.get("action"))
						|| "Validée par le RO et transmise au RH".equals(e.get("commentaire")));
		if (validee) return "VALIDEE";
		return p.getStatut() == StatutPlainteRh.NOUVEAU ? "EN_ATTENTE" : "NON_REQUISE";
	}

	private PlainteRh charger(UUID id) {
		return plainteRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Plainte introuvable : " + id));
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
				nomComplet(p.getAuteur()),
				p.getAuteur().getMatricule(),
				p.getTitre(),
				p.getDescription(),
				p.getPiecesJointes(),
				p.getTranscriptionAudio(),
				p.getStatut(),
				p.getCommentaireRh(),
				p.getLogActions(),
				etatValidationRo(p),
				p.getCreeLe(),
				p.getModifieLe());
	}
}
