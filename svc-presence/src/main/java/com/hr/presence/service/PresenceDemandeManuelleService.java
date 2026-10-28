package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Pointage manuel après un rejet (D-P04) : demande du collaborateur, validation par le responsable
 * D-H03 (sinon RH / ADMIN), personne ne valide sa propre demande, alerte RH au-delà du seuil mensuel.
 */
@Service
public class PresenceDemandeManuelleService {

	private static final Logger log = LoggerFactory.getLogger(PresenceDemandeManuelleService.class);
	private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRENCH).withZone(ZONE);

	private final PresenceDemandeManuelleRepository demandeRepository;
	private final PresencePointageRepository pointageRepository;
	private final ReferentielClient referentielClient;
	private final PresenceAccessService accessService;
	private final PresenceEventPublisher eventPublisher;
	private final PresenceProperties properties;

	public PresenceDemandeManuelleService(
			PresenceDemandeManuelleRepository demandeRepository,
			PresencePointageRepository pointageRepository,
			ReferentielClient referentielClient,
			PresenceAccessService accessService,
			PresenceEventPublisher eventPublisher,
			PresenceProperties properties) {
		this.demandeRepository = demandeRepository;
		this.pointageRepository = pointageRepository;
		this.referentielClient = referentielClient;
		this.accessService = accessService;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
	}

	@Transactional
	public DemandeManuelleResponse creer(DemandeManuelleCreationRequest request) {
		ValideurPointage moi = referentielClient.monValideurPointage();
		PresencePointage rejet = pointageRepository.findById(request.pointageRejeteId())
				.orElseThrow(() -> PresenceMetierException.notFound("POINTAGE_INTROUVABLE", "Pointage introuvable"));
		if (!appartientA(rejet.getCollaborateurId(), moi)) {
			throw new SecurityException("Ce pointage ne vous appartient pas");
		}
		if (!rejet.getStatut().name().startsWith("REJETE_")) {
			throw PresenceMetierException.unprocessable("POINTAGE_NON_REJETE",
					"Seul un pointage refusé peut faire l'objet d'une demande manuelle");
		}
		if (demandeRepository.existsByPointageRejeteId(rejet.getId())) {
			throw PresenceMetierException.conflict("DEMANDE_DEJA_FAITE",
					"Une demande de pointage manuel existe déjà pour ce refus");
		}

		Instant maintenant = Instant.now();
		Instant heure = request.heureDeclaree() != null ? request.heureDeclaree() : rejet.getServerTs();
		if (heure.isAfter(maintenant.plus(Duration.ofMinutes(5)))) {
			throw PresenceMetierException.unprocessable("HEURE_FUTURE", "L'heure déclarée ne peut pas être dans le futur");
		}
		int maxJours = properties.getManuel().getAncienneteMaxJours();
		if (heure.isBefore(maintenant.minus(maxJours, ChronoUnit.DAYS))) {
			throw PresenceMetierException.unprocessable("HEURE_TROP_ANCIENNE",
					"L'heure déclarée doit dater de moins de " + maxJours + " jours");
		}

		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(rejet.getCollaborateurId());
		d.setCollaborateurCompteId(texte(moi.collaborateurCompteId()));
		d.setCollaborateurNom(moi.collaborateurNom());
		d.setSiteId(rejet.getSiteId());
		d.setType(request.type() != null ? parseType(request.type()) : rejet.getType());
		d.setHeureDeclaree(heure);
		d.setJustification(request.justification().trim());
		d.setValideurId(texte(moi.valideurId()));
		d.setValideurCompteId(texte(moi.valideurCompteId()));
		d.setValideurNom(moi.valideurNom());
		PresenceDemandeManuelle saved = demandeRepository.save(d);

		String libelle = libelleType(saved.getType());
		eventPublisher.notifier(
				saved.validationParRh() ? PresenceEventPublisher.DESTINATAIRE_RH : adresse(saved.getValideurCompteId(), saved.getValideurId()),
				"Pointage manuel à valider — " + nom(saved),
				nom(saved) + " demande un pointage manuel (" + libelle + " du " + HEURE.format(heure) + ") : "
						+ saved.getJustification());
		log.info("Demande pointage manuel {} collab={} valideur={}", saved.getId(), saved.getCollaborateurId(),
				saved.validationParRh() ? "RH" : saved.getValideurId());
		return toResponse(saved, null, rejet.getMotifRejet());
	}

	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> mesDemandes() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		return demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(moi.collaborateurId().toString()).stream()
				.map(d -> toResponse(d, null, null))
				.toList();
	}

	/** File du responsable : demandes dont il est le valideur attendu. */
	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> aValider() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		return demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(
						moi.collaborateurId().toString(), StatutDemandeManuelle.EN_ATTENTE).stream()
				.map(d -> toResponse(d, null, motifRejet(d)))
				.toList();
	}

	/** Vue RH : toutes les demandes, avec le compteur mensuel et l'alerte de seuil. */
	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> listerAdmin(StatutDemandeManuelle statut) {
		List<PresenceDemandeManuelle> demandes = statut == null
				? demandeRepository.findAllByOrderByCreeLeDesc()
				: demandeRepository.findByStatutOrderByCreeLeDesc(statut);
		return demandes.stream()
				.map(d -> toResponse(d, manuelsDuMois(d.getCollaborateurId(), d.getHeureDeclaree()), motifRejet(d)))
				.toList();
	}

	@Transactional
	public DemandeManuelleResponse valider(UUID id) {
		PresenceDemandeManuelle d = exigerEnAttente(id);
		String decideur = autoriserDecision(d);

		PresencePointage rejet = pointageRepository.findById(d.getPointageRejeteId()).orElse(null);
		PresencePointage p = new PresencePointage();
		p.setCollaborateurId(d.getCollaborateurId());
		p.setType(d.getType());
		p.setStatut(PointageStatut.VALIDE_MANUEL);
		p.setServerTs(d.getHeureDeclaree());
		p.setSiteId(d.getSiteId());
		p.setDemandeManuelleId(d.getId());
		if (rejet != null) {
			p.setLatitude(rejet.getLatitude());
			p.setLongitude(rejet.getLongitude());
			p.setAccuracyMetres(rejet.getAccuracyMetres());
			p.setDistanceMetres(rejet.getDistanceMetres());
		}
		PresencePointage pointage = pointageRepository.save(p);

		d.setStatut(StatutDemandeManuelle.VALIDEE);
		d.setDecidePar(decideur);
		d.setDecideLe(Instant.now());
		d.setPointageId(pointage.getId());
		PresenceDemandeManuelle saved = demandeRepository.save(d);

		eventPublisher.notifier(adresse(saved.getCollaborateurCompteId(), saved.getCollaborateurId()),
				"Pointage manuel validé",
				"Votre pointage manuel (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été validé par " + decideur + ".");

		long duMois = manuelsDuMois(saved.getCollaborateurId(), saved.getHeureDeclaree());
		int seuil = properties.getManuel().getSeuilAlerteMois();
		if (duMois > seuil) {
			eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH,
					"Pointages manuels fréquents — " + nom(saved),
					nom(saved) + " a " + duMois + " pointages manuels validés ce mois-ci (seuil : " + seuil + ").");
		}
		return toResponse(saved, duMois, null);
	}

	@Transactional
	public DemandeManuelleResponse refuser(UUID id, String motif) {
		PresenceDemandeManuelle d = exigerEnAttente(id);
		String decideur = autoriserDecision(d);
		d.setStatut(StatutDemandeManuelle.REFUSEE);
		d.setDecidePar(decideur);
		d.setDecideLe(Instant.now());
		d.setMotifRefus(motif.trim());
		PresenceDemandeManuelle saved = demandeRepository.save(d);
		eventPublisher.notifier(adresse(saved.getCollaborateurCompteId(), saved.getCollaborateurId()),
				"Pointage manuel refusé",
				"Votre pointage manuel (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été refusé par " + decideur + ". Motif : " + saved.getMotifRefus());
		return toResponse(saved, null, null);
	}

	/**
	 * Valideur attendu, ou RH / ADMIN ; jamais le demandeur lui-même. Renvoie le nom du décideur.
	 */
	private String autoriserDecision(PresenceDemandeManuelle d) {
		if (accessService.isBackofficeEcriture()) {
			String compte = accessService.currentCompteId();
			if (compte != null && compte.equals(d.getCollaborateurCompteId())) {
				throw new SecurityException("Vous ne pouvez pas valider votre propre pointage manuel");
			}
			ValideurPointage moi = null;
			try {
				moi = referentielClient.monValideurPointage();
			} catch (RuntimeException ex) {
				// compte RH sans fiche : libellé générique
			}
			if (moi != null && appartientA(d.getCollaborateurId(), moi)) {
				throw new SecurityException("Vous ne pouvez pas valider votre propre pointage manuel");
			}
			String nom = moi != null ? moi.collaborateurNom() : null;
			return (nom != null ? nom : "les RH") + " (RH)";
		}
		ValideurPointage moi = referentielClient.monValideurPointage();
		String monId = moi.collaborateurId().toString();
		if (monId.equals(d.getCollaborateurId())) {
			throw new SecurityException("Vous ne pouvez pas valider votre propre pointage manuel");
		}
		if (d.getValideurId() == null || !d.getValideurId().equals(monId)) {
			throw new SecurityException("Vous n'êtes pas le responsable attendu pour cette demande");
		}
		return moi.collaborateurNom() != null ? moi.collaborateurNom() : "votre responsable";
	}

	private PresenceDemandeManuelle exigerEnAttente(UUID id) {
		PresenceDemandeManuelle d = demandeRepository.findById(id)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		if (d.getStatut() != StatutDemandeManuelle.EN_ATTENTE) {
			throw PresenceMetierException.conflict("DEMANDE_DEJA_TRAITEE", "Cette demande a déjà été traitée");
		}
		return d;
	}

	long manuelsDuMois(String collaborateurId, Instant reference) {
		ZonedDateTime debut = reference.atZone(ZONE).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
		return demandeRepository.compterValideesSurPeriode(
				collaborateurId, debut.toInstant(), debut.plusMonths(1).toInstant());
	}

	private String motifRejet(PresenceDemandeManuelle d) {
		return pointageRepository.findById(d.getPointageRejeteId()).map(PresencePointage::getMotifRejet).orElse(null);
	}

	private DemandeManuelleResponse toResponse(PresenceDemandeManuelle d, Long duMois, String motifRejet) {
		return new DemandeManuelleResponse(
				d.getId(),
				d.getStatut(),
				d.getType(),
				d.getHeureDeclaree(),
				d.getJustification(),
				d.getCollaborateurId(),
				d.getCollaborateurNom(),
				d.getSiteId(),
				d.getPointageRejeteId(),
				motifRejet,
				d.getValideurNom(),
				d.validationParRh(),
				d.getDecidePar(),
				d.getDecideLe(),
				d.getMotifRefus(),
				d.getPointageId(),
				d.getCreeLe(),
				duMois,
				duMois != null ? duMois > properties.getManuel().getSeuilAlerteMois() : null);
	}

	/** Le pointage a pu être enregistré sous l'identifiant de fiche (en-tête) ou de compte (JWT). */
	private static boolean appartientA(String collaborateurId, ValideurPointage moi) {
		return Objects.equals(collaborateurId, texte(moi.collaborateurId()))
				|| Objects.equals(collaborateurId, texte(moi.collaborateurCompteId()));
	}

	/** Notifications adressées au compte (claim STOMP), repli sur la fiche — cf. SCRUM-27. */
	private static String adresse(String compteId, String ficheId) {
		return compteId != null ? compteId : ficheId;
	}

	private static String nom(PresenceDemandeManuelle d) {
		return d.getCollaborateurNom() != null ? d.getCollaborateurNom() : "Un collaborateur";
	}

	private static String libelleType(PointageType type) {
		return type == PointageType.SORTIE ? "sortie" : "entrée";
	}

	private static String texte(UUID id) {
		return id != null ? id.toString() : null;
	}

	private static PointageType parseType(String raw) {
		try {
			return PointageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw PresenceMetierException.unprocessable("TYPE_INVALIDE", "type doit être ENTREE ou SORTIE");
		}
	}
}
