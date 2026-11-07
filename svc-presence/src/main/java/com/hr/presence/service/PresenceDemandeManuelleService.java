package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.numero.NumeroDemandeService;
import com.hr.presence.numero.PrefixeNumero;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

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
	private final FacialIdentiteService identiteService;
	private final PresenceEventPublisher eventPublisher;
	private final PresenceProperties properties;
	private final PresenceSiteRepository siteRepository;
	private final FenetreHoraireService fenetreHoraire;
	private final NumeroDemandeService numeroService;

	public PresenceDemandeManuelleService(
			PresenceDemandeManuelleRepository demandeRepository,
			PresencePointageRepository pointageRepository,
			ReferentielClient referentielClient,
			PresenceAccessService accessService,
			FacialIdentiteService identiteService,
			PresenceEventPublisher eventPublisher,
			PresenceProperties properties,
			PresenceSiteRepository siteRepository,
			FenetreHoraireService fenetreHoraire,
			NumeroDemandeService numeroService) {
		this.demandeRepository = demandeRepository;
		this.pointageRepository = pointageRepository;
		this.referentielClient = referentielClient;
		this.accessService = accessService;
		this.identiteService = identiteService;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.siteRepository = siteRepository;
		this.fenetreHoraire = fenetreHoraire;
		this.numeroService = numeroService;
	}

	@Transactional
	public DemandeManuelleResponse creer(DemandeManuelleCreationRequest request) {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
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

		PointageType typeDemande = request.type() != null ? parseType(request.type()) : rejet.getType();
		// D-P12.c : l'heure déclarée doit tomber dans la fenêtre du jour du site (horaire modifié depuis : celle en vigueur).
		controlerFenetre(rejet.getSiteId(), rejet.getCollaborateurId(), heure, typeDemande);

		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(rejet.getCollaborateurId());
		d.setCollaborateurCompteId(texte(moi.collaborateurCompteId()));
		d.setCollaborateurNom(moi.collaborateurNom());
		d.setSiteId(rejet.getSiteId());
		d.setType(typeDemande);
		d.setHeureDeclaree(heure);
		d.setJustification(request.justification().trim());
		d.setValideurId(texte(moi.valideurId()));
		d.setValideurCompteId(texte(moi.valideurCompteId()));
		d.setValideurNom(moi.valideurNom());
		d.setCreeLe(maintenant);
		d.setNumero(numeroService.attribuer(PrefixeNumero.PM, maintenant));
		PresenceDemandeManuelle saved = demandeRepository.save(d);

		String libelle = libelleType(saved.getType());
		eventPublisher.notifier(
				saved.validationParRh() ? PresenceEventPublisher.DESTINATAIRE_RH : adresse(saved.getValideurCompteId(), saved.getValideurId()),
				"Pointage manuel à valider — " + nom(saved) + " (" + saved.getNumero() + ")",
				nom(saved) + " demande un pointage manuel (" + libelle + " du " + HEURE.format(heure) + ") : "
						+ saved.getJustification());
		log.info("Demande pointage manuel {} ({}) collab={} valideur={}", saved.getNumero(), saved.getId(), saved.getCollaborateurId(),
				saved.validationParRh() ? "RH" : saved.getValideurId());
		return toResponses(List.of(saved), null, d0 -> rejet.getMotifRejet()).get(0);
	}

	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> mesDemandes() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		return toResponses(demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(moi.collaborateurId().toString()),
				null, d -> null);
	}

	/** File du responsable : demandes dont il est le valideur attendu. */
	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> aValider() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		return toResponses(demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(
				moi.collaborateurId().toString(), StatutDemandeManuelle.EN_ATTENTE), null, this::motifRejet);
	}

	/** Vue RH : toutes les demandes, avec le compteur mensuel et l'alerte de seuil. */
	@Transactional(readOnly = true)
	public List<DemandeManuelleResponse> listerAdmin(StatutDemandeManuelle statut) {
		List<PresenceDemandeManuelle> demandes = statut == null
				? demandeRepository.findAllByOrderByCreeLeDesc()
				: demandeRepository.findByStatutOrderByCreeLeDesc(statut);
		return toResponses(demandes, d -> manuelsDuMois(d.getCollaborateurId(), d.getHeureDeclaree()), this::motifRejet);
	}

	@Transactional
	public DemandeManuelleResponse valider(UUID id) {
		PresenceDemandeManuelle d = exigerEnAttente(id);
		String decideur = autoriserDecision(d);

		// D-P12.c : revérifié à la validation, l'horaire a pu changer depuis la demande.
		FenetreHoraireService.DansFenetre dansFenetre =
				controlerFenetre(d.getSiteId(), d.getCollaborateurId(), d.getHeureDeclaree(), d.getType());

		PresencePointage rejet = pointageRepository.findById(d.getPointageRejeteId()).orElse(null);
		PresencePointage p = new PresencePointage();
		p.setCollaborateurId(d.getCollaborateurId());
		p.setType(d.getType());
		p.setStatut(PointageStatut.VALIDE_MANUEL);
		p.setServerTs(d.getHeureDeclaree());
		p.setSiteId(d.getSiteId());
		p.setDemandeManuelleId(d.getId());
		if (dansFenetre != null) {
			p.setJournee(dansFenetre.journee());
			p.setHoraireDebutTs(dansFenetre.horaireDebut());
			p.setHoraireFinTs(dansFenetre.horaireFin());
		}
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
				"Pointage manuel validé — " + saved.getNumero(),
				"Votre pointage manuel " + saved.getNumero() + " (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été validé par " + decideur + ".");

		long duMois = manuelsDuMois(saved.getCollaborateurId(), saved.getHeureDeclaree());
		int seuil = properties.getManuel().getSeuilAlerteMois();
		if (duMois > seuil) {
			eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH,
					"Pointages manuels fréquents — " + nom(saved),
					nom(saved) + " a " + duMois + " pointages manuels validés ce mois-ci (seuil : " + seuil + ").");
		}
		return toResponses(List.of(saved), d0 -> duMois, d0 -> null).get(0);
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
				"Pointage manuel refusé — " + saved.getNumero(),
				"Votre pointage manuel " + saved.getNumero() + " (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été refusé par " + decideur + ". Motif : " + saved.getMotifRefus());
		return toResponses(List.of(saved), null, d0 -> null).get(0);
	}

	/**
	 * Fenêtre horaire du site à l'heure déclarée (D-P12.c). Sans site connu (ex. QR invalide) ou pour un site sans
	 * horaire : aucun contrôle ({@code null}). Hors fenêtre : 422 HEURE_HORS_FENETRE.
	 */
	private FenetreHoraireService.DansFenetre controlerFenetre(
			UUID siteId, String collaborateurId, Instant heure, PointageType type) {
		if (siteId == null) {
			return null;
		}
		PresenceSite site = siteRepository.findById(siteId).orElse(null);
		if (site == null) {
			return null;
		}
		FenetreHoraireService.Resultat resultat = fenetreHoraire.evaluer(site, collaborateurId, heure, type);
		if (resultat instanceof FenetreHoraireService.HorsFenetre hors) {
			throw PresenceMetierException.unprocessable("HEURE_HORS_FENETRE",
					"L'heure déclarée est hors de la fenêtre de pointage du site : " + hors.message());
		}
		return resultat instanceof FenetreHoraireService.DansFenetre dans ? dans : null;
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

	/**
	 * Réponses par lot : noms et matricules résolus par le serveur (D-L01.a). Référentiel injoignable : on garde les
	 * noms figés à la création et le matricule reste absent, sans jamais exposer d'identifiant.
	 */
	private List<DemandeManuelleResponse> toResponses(List<PresenceDemandeManuelle> demandes,
			Function<PresenceDemandeManuelle, Long> duMois,
			Function<PresenceDemandeManuelle, String> motifRejet) {
		Map<String, Affectation> identites = identites(demandes);
		return demandes.stream()
				.map(d -> toResponse(d, identites.get(d.getCollaborateurId()),
						duMois == null ? null : duMois.apply(d), motifRejet.apply(d)))
				.toList();
	}

	private DemandeManuelleResponse toResponse(PresenceDemandeManuelle d, Affectation identite, Long duMois,
			String motifRejet) {
		String nom = d.getCollaborateurNom() != null ? d.getCollaborateurNom()
				: identite != null && identite.nom() != null ? identite.nom() : "Collaborateur introuvable";
		return new DemandeManuelleResponse(
				d.getId(),
				d.getNumero(),
				d.getStatut(),
				d.getType(),
				d.getHeureDeclaree(),
				d.getJustification(),
				d.getCollaborateurId(),
				nom,
				identite != null ? identite.matricule() : null,
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

	/** Identités (matricule, nom) des collaborateurs des demandes, indexées par l'identifiant stocké (fiche ou compte). */
	private Map<String, Affectation> identites(List<PresenceDemandeManuelle> demandes) {
		List<UUID> ids = demandes.stream().map(PresenceDemandeManuelle::getCollaborateurId).distinct()
				.map(PresenceDemandeManuelleService::uuidOuNull).filter(Objects::nonNull).toList();
		Map<String, Affectation> resultat = new HashMap<>();
		if (ids.isEmpty()) {
			return resultat;
		}
		try {
			for (Affectation a : referentielClient.affectations(ids)) {
				if (a.collaborateurId() != null) {
					resultat.put(a.collaborateurId().toString(), a);
				}
				if (a.compteId() != null) {
					resultat.put(a.compteId().toString(), a);
				}
			}
		} catch (RuntimeException ex) {
			log.warn("Identités des demandes manuelles non résolues : {}", ex.getMessage());
		}
		return resultat;
	}

	private static UUID uuidOuNull(String valeur) {
		try {
			return valeur == null ? null : UUID.fromString(valeur);
		} catch (IllegalArgumentException ex) {
			return null;
		}
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
