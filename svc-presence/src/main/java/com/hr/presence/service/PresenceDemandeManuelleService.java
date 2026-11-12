package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleDetail;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.dto.PresenceCollaborateurDtos;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.numero.NumeroDemandeService;
import com.hr.presence.numero.PrefixeNumero;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.DemandeManuelleReponseMapper.Vue;
import com.hr.presence.web.PresenceMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pointage manuel après un refus (D-P04, D-P21 à D-P26) : demande du collaborateur (contrôles d'éligibilité, données
 * figées), validation par le responsable D-H03 (sinon RH / ADMIN), personne ne valide sa propre demande, expiration
 * après le délai de décision, alerte RH au-delà du seuil mensuel.
 *
 * <p>Valider, refuser et expirer prennent un verrou pessimiste sur la demande, après l'autorisation, et relisent le
 * statut sous verrou. Ces méthodes ne rétablissent pas la transaction sur {@link PresenceMetierException} : le passage
 * en EXPIREE doit rester enregistré quand la décision reçoit son 409.
 */
@Service
public class PresenceDemandeManuelleService {

	private static final Logger log = LoggerFactory.getLogger(PresenceDemandeManuelleService.class);
	private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
	private static final java.util.regex.Pattern NUMERO_PM = java.util.regex.Pattern.compile("^PM-\\d{6}-\\d{4}$");
	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRENCH).withZone(ZONE);

	static final int COMMENTAIRE_MIN = 10;
	static final int COMMENTAIRE_MAX = 500;
	static final int JUSTIFICATION_MIN = 10;
	static final int JUSTIFICATION_MAX = 500;

	private final PresenceDemandeManuelleRepository demandeRepository;
	private final PresencePointageRepository pointageRepository;
	private final ReferentielClient referentielClient;
	private final PresenceAccessService accessService;
	private final FacialIdentiteService identiteService;
	private final PresenceEventPublisher eventPublisher;
	private final PresenceParametresService parametres;
	private final PresenceSiteRepository siteRepository;
	private final FenetreHoraireService fenetreHoraire;
	private final NumeroDemandeService numeroService;
	private final EligibiliteManuelService eligibilite;
	private final DemandeManuelleReponseMapper mapper;
	private final EntityManager entityManager;
	private final Clock clock;

	public PresenceDemandeManuelleService(
			PresenceDemandeManuelleRepository demandeRepository,
			PresencePointageRepository pointageRepository,
			ReferentielClient referentielClient,
			PresenceAccessService accessService,
			FacialIdentiteService identiteService,
			PresenceEventPublisher eventPublisher,
			PresenceParametresService parametres,
			PresenceSiteRepository siteRepository,
			FenetreHoraireService fenetreHoraire,
			NumeroDemandeService numeroService,
			EligibiliteManuelService eligibilite,
			DemandeManuelleReponseMapper mapper,
			EntityManager entityManager,
			Clock clock) {
		this.demandeRepository = demandeRepository;
		this.pointageRepository = pointageRepository;
		this.referentielClient = referentielClient;
		this.accessService = accessService;
		this.identiteService = identiteService;
		this.eventPublisher = eventPublisher;
		this.parametres = parametres;
		this.siteRepository = siteRepository;
		this.fenetreHoraire = fenetreHoraire;
		this.numeroService = numeroService;
		this.eligibilite = eligibilite;
		this.mapper = mapper;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	// ---- Création (D-P21.c) --------------------------------------------------------------------------------------

	/**
	 * Ordre des contrôles : jeton (401, sécurité) puis identité (403 IDENTITE_INCOHERENTE) puis corps (400) puis
	 * périmètre (403 HORS_PERIMETRE, pointage inconnu ou d'un autre : même réponse) puis champs figés (400) puis les
	 * contrôles d'éligibilité. Un refus d'office ne crée rien et ne notifie personne ; il est journalisé sans coordonnées.
	 */
	@Transactional
	public DemandeManuelleResponse creer(DemandeManuelleCreationRequest request) {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		String justification = validerCorps(request);
		PresencePointage rejet = pointageRepository.findById(request.pointageRejeteId()).orElse(null);
		if (rejet == null || !appartientA(rejet.getCollaborateurId(), moi)) {
			log.info("Demande de pointage manuel refusée d'office collab={} pointage={} code=HORS_PERIMETRE",
					texte(moi.collaborateurId()), request.pointageRejeteId());
			throw PresenceMetierException.forbidden("HORS_PERIMETRE", "Ce pointage n'existe pas ou ne vous appartient pas.");
		}
		controlerChampsFiges(request, rejet);

		PresenceSite site = rejet.getSiteId() != null ? siteRepository.findById(rejet.getSiteId()).orElse(null) : null;
		Instant maintenant = clock.instant();
		EligibiliteManuelService.Verdict verdict = eligibilite.evaluer(rejet, site,
				demandeRepository.findByPointageRejeteId(rejet.getId()).orElse(null), maintenant);
		if (!verdict.possible()) {
			log.info("Demande de pointage manuel refusée d'office collab={} pointage={} code={}",
					rejet.getCollaborateurId(), rejet.getId(), verdict.code());
			throw verdict.exception();
		}

		Duration delaiDemande = Duration.between(rejet.getServerTs(), maintenant);
		EligibiliteManuelService.Figees figees = eligibilite.figer(rejet, site, delaiDemande,
				mapper.manuelsDuMois(rejet.getCollaborateurId(), rejet.getServerTs()));

		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(rejet.getCollaborateurId());
		d.setCollaborateurCompteId(texte(moi.collaborateurCompteId()));
		d.setCollaborateurNom(moi.collaborateurNom());
		d.setSiteId(rejet.getSiteId());
		d.setType(rejet.getType());
		d.setHeureDeclaree(rejet.getServerTs());
		d.setJustification(justification);
		d.setValideurId(texte(moi.valideurId()));
		d.setValideurCompteId(texte(moi.valideurCompteId()));
		d.setValideurNom(moi.valideurNom());
		d.setCreeLe(maintenant);
		d.setNumero(numeroService.attribuer(PrefixeNumero.PM, maintenant));
		d.setRefusStatut(figees.refusStatut());
		d.setDistanceMetres(figees.distance());
		d.setPrecisionMetres(figees.precision());
		d.setRayonMetres(figees.rayon());
		d.setModeQr(figees.modeQr().name());
		d.setPlafondMetres(parametres.plafondDistanceManuelMetres());
		d.setDelaiDemandeMinutes(figees.delaiDemandeMinutes());
		d.setEcheance(rejet.getServerTs().plus(parametres.delaiDecisionManuel()));
		d.setSignaux(figees.signaux());
		PresenceDemandeManuelle saved;
		try {
			saved = demandeRepository.saveAndFlush(d);
		} catch (DataIntegrityViolationException ex) {
			// Deux demandes simultanées pour le même refus : la contrainte unique tranche (D-P21.b).
			if (!causeContrainte(ex, CONTRAINTE_UNIQUE_REFUS)) {
				throw ex;
			}
			String ref = demandeRepository.numeroParPointageRejeteId(rejet.getId()).map(n -> " (" + n + ")").orElse("");
			throw PresenceMetierException.conflict("DEMANDE_DEJA_FAITE",
					"Une demande de pointage manuel a déjà été faite pour ce refus" + ref + ".");
		}

		eventPublisher.notifier(
				saved.validationParRh() ? PresenceEventPublisher.DESTINATAIRE_RH : adresse(saved.getValideurCompteId(), saved.getValideurId()),
				"Pointage manuel à valider — " + nom(saved) + " (" + saved.getNumero() + ")",
				nom(saved) + " demande un pointage manuel (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") : " + saved.getJustification(),
				objetManuel(saved, "POINTAGE_MANUEL_DEMANDE"));
		log.info("Demande pointage manuel {} ({}) collab={} valideur={} signaux={}", saved.getNumero(), saved.getId(),
				saved.getCollaborateurId(), saved.validationParRh() ? "RH" : saved.getValideurId(), saved.getSignaux().size());
		return mapper.versReponses(List.of(saved), Vue.DEMANDEUR, d0 -> rejet.getMotifRejet()).get(0);
	}

	private static final String CONTRAINTE_UNIQUE_REFUS = "uk_demande_manuelle_rejet";

	/** Vrai si la contrainte nommée figure dans la chaîne des causes (insensible à la casse). */
	private static boolean causeContrainte(Throwable ex, String contrainte) {
		for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains(contrainte)) {
				return true;
			}
		}
		return false;
	}

	/** 400 VALIDATION_ERROR : pointage obligatoire, justification de 10 à 500 caractères (espaces de bord retirés). */
	private static String validerCorps(DemandeManuelleCreationRequest request) {
		if (request == null || request.pointageRejeteId() == null) {
			throw PresenceMetierException.badRequest("VALIDATION_ERROR", "pointage_rejete_id est obligatoire");
		}
		String justification = request.justification() == null ? "" : request.justification().trim();
		if (justification.length() < JUSTIFICATION_MIN || justification.length() > JUSTIFICATION_MAX) {
			throw PresenceMetierException.badRequest("VALIDATION_ERROR",
					"La justification doit faire entre " + JUSTIFICATION_MIN + " et " + JUSTIFICATION_MAX + " caractères");
		}
		return justification;
	}

	/** D-P23 : heure (à la seconde) et type sont ceux du refus ; identiques tolérés, différents : 400. */
	private static void controlerChampsFiges(DemandeManuelleCreationRequest request, PresencePointage rejet) {
		boolean heureDifferente = request.heureDeclaree() != null
				&& !request.heureDeclaree().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
				.equals(rejet.getServerTs().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
		boolean typeDifferent = request.type() != null
				&& !request.type().trim().equalsIgnoreCase(rejet.getType().name());
		if (heureDifferente || typeDifferent) {
			throw PresenceMetierException.badRequest("CHAMP_NON_MODIFIABLE",
					"L'heure et le type d'un pointage manuel sont ceux de la tentative refusée.");
		}
	}

	// ---- Lectures ------------------------------------------------------------------------------------------------
	// Sans transaction englobante : elles appellent le référentiel (noms, matricules) et ne doivent pas garder
	// une connexion à la base pendant ces appels ; chaque lecture du dépôt a sa propre transaction.

	/** Demandes du collaborateur : vue du demandeur, sans signaux ni compteurs (D-P24.d). */
	public List<DemandeManuelleResponse> mesDemandes() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		return mapper.versReponses(demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(moi.collaborateurId().toString()),
				Vue.DEMANDEUR, d -> null);
	}

	/** File du responsable : demandes dont il est le valideur attendu et dont l'échéance n'est pas atteinte. */
	public List<DemandeManuelleResponse> aValider() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		Instant maintenant = clock.instant();
		List<PresenceDemandeManuelle> enAttente = demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(
						moi.collaborateurId().toString(), StatutDemandeManuelle.EN_ATTENTE).stream()
				.filter(d -> d.getEcheance() == null || maintenant.isBefore(d.getEcheance()))
				.toList();
		return mapper.versReponses(enAttente, Vue.DECIDEUR, this::motifRejet);
	}

	/** Vue RH : toutes les demandes, avec le compteur mensuel et l'alerte de seuil. */
	public List<DemandeManuelleResponse> listerAdmin(StatutDemandeManuelle statut) {
		return listerAdmin(statut, null, null, null, null, null);
	}

	public List<DemandeManuelleResponse> listerAdmin(StatutDemandeManuelle statut, UUID collaborateurId, UUID siteId,
			LocalDate du, LocalDate au) {
		return listerAdmin(statut, collaborateurId, siteId, du, au, null);
	}

	/**
	 * Vue RH filtrée (D-N01) : statut, collaborateur (fiche ; les demandes anciennes portent parfois le compte, il est
	 * cherché aussi), site, période sur l'heure déclarée (jours de Tunis, « au » inclus) et « avec signal de vigilance »
	 * (D-P24.a). Période inversée : 400.
	 */
	public List<DemandeManuelleResponse> listerAdmin(StatutDemandeManuelle statut, UUID collaborateurId, UUID siteId,
			LocalDate du, LocalDate au, Boolean avecSignal) {
		if (du != null && au != null && au.isBefore(du)) {
			throw PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début");
		}
		Set<String> identites = collaborateurId != null ? identitesDe(collaborateurId) : null;
		Instant from = du != null ? du.atStartOfDay(ZONE).toInstant() : null;
		Instant to = au != null ? au.plusDays(1).atStartOfDay(ZONE).toInstant() : null;
		List<PresenceDemandeManuelle> demandes = demandeRepository.findAll(
				filtre(statut, identites, siteId, from, to, avecSignal), Sort.by(Sort.Direction.DESC, "creeLe"));
		return mapper.versReponses(demandes, Vue.DECIDEUR, this::motifRejet);
	}

	/**
	 * Demandes d'un collaborateur (fiche ou compte) dont l'heure déclarée tombe dans [from, to[ : onglet Pointages de la
	 * modale collaborateur et fiche d'équipe mobile (lecteurs : RH, DIRECTION, ADMIN, valideur). Les plus récentes d'abord.
	 */
	public List<DemandeManuelleResponse> pourIdentites(Set<String> identites, Instant from, Instant to) {
		List<PresenceDemandeManuelle> demandes = demandeRepository.findAll(
				filtre(null, identites, null, from, to, null), Sort.by(Sort.Direction.DESC, "creeLe"));
		return mapper.versReponses(demandes, Vue.DECIDEUR, this::motifRejet);
	}

	private static Specification<PresenceDemandeManuelle> filtre(StatutDemandeManuelle statut, Set<String> identites,
			UUID siteId, Instant from, Instant to, Boolean avecSignal) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			if (statut != null) predicats.add(cb.equal(root.get("statut"), statut));
			if (identites != null) {
				predicats.add(cb.or(root.get("collaborateurId").in(identites), root.get("collaborateurCompteId").in(identites)));
			}
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (from != null) predicats.add(cb.greaterThanOrEqualTo(root.get("heureDeclaree"), from));
			if (to != null) predicats.add(cb.lessThan(root.get("heureDeclaree"), to));
			if (Boolean.TRUE.equals(avecSignal)) predicats.add(cb.isNotNull(root.get("signaux")));
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}

	/** Fiche et compte du collaborateur ; référentiel indisponible : la fiche seule (la liste reste servie). */
	private Set<String> identitesDe(UUID ficheId) {
		Set<String> identites = new LinkedHashSet<>();
		identites.add(ficheId.toString());
		try {
			for (Affectation a : referentielClient.affectations(List.of(ficheId))) {
				if (a.compteId() != null) {
					identites.add(a.compteId().toString());
				}
			}
		} catch (PresenceMetierException ex) {
			log.warn("Filtre collaborateur sans élargissement au compte : {}", ex.getMessage());
		}
		return identites;
	}

	// ---- Détail (D-N01.b) ----------------------------------------------------------------------------------------

	/** Détail pour la Plateforme RH (RH, DIRECTION, ADMIN) ; inconnu : 404 DEMANDE_INTROUVABLE. */
	public DemandeManuelleDetail detailAdmin(UUID id) {
		PresenceDemandeManuelle d = demandeRepository.findById(id)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		return versDetail(d, Vue.DECIDEUR);
	}

	/** Détail par numéro lisible PM-AAAAMM-NNNN : mal formé 400 NUMERO_INVALIDE, inconnu 404. */
	public DemandeManuelleDetail detailAdminParNumero(String numero) {
		if (numero == null || !NUMERO_PM.matcher(numero).matches()) {
			throw PresenceMetierException.badRequest("NUMERO_INVALIDE", "Numéro de pointage manuel invalide (attendu PM-AAAAMM-NNNN)");
		}
		PresenceDemandeManuelle d = demandeRepository.findByNumero(numero)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		return versDetail(d, Vue.DECIDEUR);
	}

	/**
	 * Détail pour le mobile (C5) : le demandeur, son valideur D-H03 attendu, ou RH / ADMIN quand la validation revient aux
	 * RH. Identité lue dans le JWT. Tout autre appelant, et une demande inconnue : 403 HORS_PERIMETRE (pas d'énumération).
	 * Signaux, compteurs et commentaire sont réservés à qui décide, pas au demandeur.
	 */
	public DemandeManuelleDetail detailMobile(UUID id) {
		ValideurPointage moi = referentielClient.monValideurPointage();
		identiteService.verifierEntete(texte(moi.collaborateurId()));
		PresenceDemandeManuelle d = demandeRepository.findById(id).orElse(null);
		if (d == null) {
			throw PresenceMetierException.forbidden("HORS_PERIMETRE", "Vous n'avez pas accès à cette demande");
		}
		boolean demandeur = appartientA(d.getCollaborateurId(), moi);
		boolean valideur = d.getValideurId() != null && d.getValideurId().equals(texte(moi.collaborateurId()));
		boolean rh = d.validationParRh() && accessService.isBackofficeEcriture();
		if (!demandeur && !valideur && !rh) {
			throw PresenceMetierException.forbidden("HORS_PERIMETRE", "Vous n'avez pas accès à cette demande");
		}
		return versDetail(d, demandeur ? Vue.DEMANDEUR : Vue.DECIDEUR);
	}

	private DemandeManuelleDetail versDetail(PresenceDemandeManuelle d, Vue vue) {
		Affectation identite = mapper.identites(List.of(d)).get(d.getCollaborateurId());
		PresencePointage rejet = pointageRepository.findById(d.getPointageRejeteId()).orElse(null);
		PresenceSite site = d.getSiteId() != null ? siteRepository.findById(d.getSiteId()).orElse(null) : null;
		String motif = rejet != null ? parametres.motifRejet(rejet, site) : null;
		DemandeManuelleResponse base = mapper.versReponse(d, identite, vue, motif);
		PresenceCollaborateurDtos.PersonneResume valideur = d.validationParRh() ? null
				: new PresenceCollaborateurDtos.PersonneResume(d.getValideurId(), d.getValideurNom(), null);
		DemandeManuelleDetail.PointageRejeteResume refus = rejet == null ? null
				: new DemandeManuelleDetail.PointageRejeteResume(rejet.getStatut(), parametres.libelleStatutPointage(rejet.getStatut()),
				motif, rejet.getDistanceMetres(), rejet.getServerTs());
		return new DemandeManuelleDetail(base, site != null ? site.getLibelle() : null,
				identite != null ? identite.uniteAffichee() : null, valideur, refus);
	}

	// ---- Décision ------------------------------------------------------------------------------------------------

	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeManuelleResponse valider(UUID id, String commentaire) {
		Verrouillee v = verrouillerPourDecision(id);
		PresenceDemandeManuelle d = v.demande();
		String decideur = v.decideur();
		String note = validerCommentaire(d, commentaire);

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

		Instant maintenant = clock.instant();
		d.setStatut(StatutDemandeManuelle.VALIDEE);
		d.setDecidePar(decideur);
		d.setDecideLe(maintenant);
		d.setPointageId(pointage.getId());
		d.setCommentaireValideur(note);
		d.setDelaiDecisionMinutes(minutesDepuisTentative(d, maintenant));
		PresenceDemandeManuelle saved = demandeRepository.save(d);

		eventPublisher.notifier(adresse(saved.getCollaborateurCompteId(), saved.getCollaborateurId()),
				"Pointage manuel validé — " + saved.getNumero(),
				"Votre pointage manuel " + saved.getNumero() + " (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été validé par " + decideur + ".",
				objetManuel(saved, "POINTAGE_MANUEL_VALIDE"));

		long duMois = mapper.manuelsDuMois(saved.getCollaborateurId(), saved.getHeureDeclaree());
		int seuil = parametres.seuilPointagesManuelsMois();
		if (duMois > seuil) {
			eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH,
					"Pointages manuels fréquents — " + nom(saved),
					nom(saved) + " a " + duMois + " pointages manuels validés ce mois-ci (seuil : " + seuil + ").",
					objetManuel(saved, "POINTAGE_MANUEL_FREQUENT"));
		}
		return mapper.versReponse(saved, v.identite(), Vue.DECIDEUR, null);
	}

	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeManuelleResponse refuser(UUID id, String motif) {
		Verrouillee v = verrouillerPourDecision(id);
		PresenceDemandeManuelle d = v.demande();
		String decideur = v.decideur();
		Instant maintenant = clock.instant();
		d.setStatut(StatutDemandeManuelle.REFUSEE);
		d.setDecidePar(decideur);
		d.setDecideLe(maintenant);
		d.setMotifRefus(motif.trim());
		d.setDelaiDecisionMinutes(minutesDepuisTentative(d, maintenant));
		PresenceDemandeManuelle saved = demandeRepository.save(d);
		eventPublisher.notifier(adresse(saved.getCollaborateurCompteId(), saved.getCollaborateurId()),
				"Pointage manuel refusé — " + saved.getNumero(),
				"Votre pointage manuel " + saved.getNumero() + " (" + libelleType(saved.getType()) + " du " + HEURE.format(saved.getHeureDeclaree())
						+ ") a été refusé par " + decideur + ". Motif : " + saved.getMotifRefus(),
				objetManuel(saved, "POINTAGE_MANUEL_REFUSE"));
		return mapper.versReponse(saved, v.identite(), Vue.DECIDEUR, null);
	}

	/** Demande relue sous verrou et nom du décideur autorisé. */
	private record Verrouillee(PresenceDemandeManuelle demande, String decideur, Affectation identite) {
	}

	/**
	 * Autorise l'appelant (404, 403) sur une lecture simple, puis prend le verrou et relit la demande sous verrou :
	 * expirée (statut ou échéance atteinte) : EXPIREE enregistré puis 409 DEMANDE_EXPIREE ; autre statut final : 409
	 * DEMANDE_DEJA_TRAITEE. L'autorisation précède le verrou pour ne pas le garder pendant les appels au référentiel ;
	 * la première lecture est détachée pour que la relecture sous verrou ne ressorte pas un état périmé.
	 */
	private Verrouillee verrouillerPourDecision(UUID id) {
		PresenceDemandeManuelle apercu = demandeRepository.findById(id)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		String decideur = autoriserDecision(apercu);
		// Identité résolue ici, avant le verrou : aucun appel HTTP ne doit avoir lieu pendant le FOR UPDATE.
		Affectation identite = mapper.identites(List.of(apercu)).get(apercu.getCollaborateurId());
		entityManager.detach(apercu);

		PresenceDemandeManuelle d = demandeRepository.findByIdPourDecision(id)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		Instant maintenant = clock.instant();
		if (d.getStatut() == StatutDemandeManuelle.EN_ATTENTE && estEchue(d, maintenant)) {
			passerEnExpiree(d);
		}
		if (d.getStatut() == StatutDemandeManuelle.EXPIREE) {
			throw PresenceMetierException.conflict("DEMANDE_EXPIREE", "Cette demande a expiré le "
					+ (d.getEcheance() != null ? LibellesPresence.jourHeure(d.getEcheance()) : "—")
					+ " : elle ne peut plus être validée ni refusée.");
		}
		if (d.getStatut() != StatutDemandeManuelle.EN_ATTENTE) {
			throw PresenceMetierException.conflict("DEMANDE_DEJA_TRAITEE", "Cette demande a déjà été traitée");
		}
		return new Verrouillee(d, decideur, identite);
	}

	/**
	 * Commentaire du valideur (D-P24.b), compté après suppression des espaces de bord : obligatoire si la demande porte
	 * un signal (RH et ADMIN compris), de 10 à 500 caractères quand il est fourni, avec ou sans signal.
	 */
	private static String validerCommentaire(PresenceDemandeManuelle d, String commentaire) {
		String c = commentaire == null ? "" : commentaire.trim();
		if (c.isEmpty()) {
			if (!d.getSignaux().isEmpty()) {
				throw PresenceMetierException.badRequest("COMMENTAIRE_OBLIGATOIRE",
						"Un commentaire est obligatoire pour valider une demande signalée.");
			}
			return null;
		}
		if (c.length() < COMMENTAIRE_MIN) {
			throw PresenceMetierException.badRequest("COMMENTAIRE_TROP_COURT",
					"Le commentaire doit faire au moins " + COMMENTAIRE_MIN + " caractères.");
		}
		if (c.length() > COMMENTAIRE_MAX) {
			throw PresenceMetierException.badRequest("COMMENTAIRE_TROP_LONG",
					"Le commentaire ne peut pas dépasser " + COMMENTAIRE_MAX + " caractères.");
		}
		return c;
	}

	private static boolean estEchue(PresenceDemandeManuelle d, Instant maintenant) {
		return d.getEcheance() != null && !maintenant.isBefore(d.getEcheance());
	}

	private static long minutesDepuisTentative(PresenceDemandeManuelle d, Instant maintenant) {
		return Math.max(0, Duration.between(d.getHeureDeclaree(), maintenant).toMinutes());
	}

	// ---- Expiration (D-P23.b) ------------------------------------------------------------------------------------

	/** Demandes en attente dont l'échéance est atteinte (lues par le planificateur). */
	@Transactional(readOnly = true)
	public List<UUID> idsAExpirer() {
		return demandeRepository.idsEchues(clock.instant());
	}

	/**
	 * Une transaction par demande, idempotent : sous verrou, une demande déjà décidée ou déjà expirée n'est pas touchée,
	 * et une échéance non atteinte non plus.
	 */
	@Transactional
	public boolean expirer(UUID id) {
		PresenceDemandeManuelle d = demandeRepository.findByIdPourDecision(id).orElse(null);
		if (d == null || d.getStatut() != StatutDemandeManuelle.EN_ATTENTE || !estEchue(d, clock.instant())) {
			return false;
		}
		passerEnExpiree(d);
		return true;
	}

	/** Statut EXPIREE, sans pointage créé ; le collaborateur et le valideur figé (ou les RH) sont notifiés (D-P23.c). */
	private void passerEnExpiree(PresenceDemandeManuelle d) {
		d.setStatut(StatutDemandeManuelle.EXPIREE);
		PresenceDemandeManuelle saved = demandeRepository.save(d);
		String libelle = libelleType(saved.getType());
		String tentative = LibellesPresence.jourHeure(saved.getHeureDeclaree());
		String echeance = saved.getEcheance() != null ? LibellesPresence.jourHeure(saved.getEcheance()) : "—";
		ObjetNotification objet = objetManuel(saved, "POINTAGE_MANUEL_EXPIRE");
		eventPublisher.notifier(adresse(saved.getCollaborateurCompteId(), saved.getCollaborateurId()),
				"Pointage manuel expiré — " + saved.getNumero(),
				"Votre pointage manuel " + saved.getNumero() + " n'a pas été traité à temps et a expiré. "
						+ "Contactez votre responsable ou les RH.",
				objet);
		eventPublisher.notifier(
				saved.validationParRh() ? PresenceEventPublisher.DESTINATAIRE_RH : adresse(saved.getValideurCompteId(), saved.getValideurId()),
				"Pointage manuel expiré — " + nom(saved) + " (" + saved.getNumero() + ")",
				"La demande de pointage manuel " + saved.getNumero() + " de " + nom(saved) + " (" + libelle + " du " + tentative
						+ ") a expiré le " + echeance + " sans décision. Aucun pointage n'a été enregistré.",
				objet);
		log.info("Demande pointage manuel {} expirée collab={}", saved.getNumero(), saved.getCollaborateurId());
	}

	// ---- Outils --------------------------------------------------------------------------------------------------

	private static ObjetNotification objetManuel(PresenceDemandeManuelle d, String evenement) {
		return ObjetNotification.de(ObjetNotification.POINTAGE_MANUEL, evenement, d.getId(), d.getNumero());
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

	private String motifRejet(PresenceDemandeManuelle d) {
		return pointageRepository.findById(d.getPointageRejeteId()).map(PresencePointage::getMotifRejet).orElse(null);
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
}
