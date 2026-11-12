package com.hr.referentiel.service;

import lombok.extern.slf4j.Slf4j;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hr.referentiel.web.ReferentielMetierException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DemandeDocumentAdministratifRhService {

	static final String REFERENTIEL_TYPE = "TYPE_DOCUMENT";

	/** Repli si un type n'a pas de SLA (ne devrait pas arriver : le SLA est obligatoire à la création). */
	static final int SLA_PAR_DEFAUT_HEURES = 24;

	public static final String REGLE_FIFO = "FIFO_PREMIER_ARRIVE_PREMIER_SERVI";

	/** Message retourné lorsque le traitement viole l'ordre FIFO sans justification. */
	public static final String MSG_VIOLATION_FIFO =
			"Des demandes antérieures sont encore en attente. "
					+ "Pour traiter cette demande hors ordre d'arrivée, veuillez fournir une justification obligatoire "
					+ "(champ justification_derogation_fifo).";

	/** D-F06.a : demandes « ouvertes » qui bloquent une nouvelle demande du même type. */
	static final List<StatutDocumentAdministratifDemandeRh> STATUTS_OUVERTS = List.of(
			StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE,
			StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);

	private static final ZoneId FUSEAU_TUNIS = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter FORMAT_JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final DemandeDocumentAdministratifRhRepository repository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final ParametreService parametreService;
	private final RhNotificationPublisher notificationPublisher;
	private final NumeroLisibleService numeroLisibleService;

	public DemandeDocumentAdministratifRhService(DemandeDocumentAdministratifRhRepository repository,
			CollaborateurConnecteService collaborateurConnecteService,
			ParametreService parametreService,
			RhNotificationPublisher notificationPublisher,
			NumeroLisibleService numeroLisibleService) {
		this.repository = repository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.parametreService = parametreService;
		this.notificationPublisher = notificationPublisher;
		this.numeroLisibleService = numeroLisibleService;
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse soumettre(DemandeDocumentAdministratifCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = collaborateurConnecteService.exigerCollaborateur(jwt);
		// Lot 3 : le type est une valeur paramétrable, il doit exister et être actif (lu en base, décision d'écriture)
		ParametreVue type = exigerTypeActif(req.getTypeDocument());
		// D-F06.b : le serveur est le garde ; la fiche est verrouillée pour que deux POST simultanés
		// du même type ne passent pas tous les deux.
		collaborateurConnecteService.verrouiller(demandeur);
		repository.findFirstByDemandeurIdAndTypeDocumentAndStatutInOrderByCreeLeAsc(
				demandeur.getId(), type.code(), STATUTS_OUVERTS).ifPresent(ouverte -> {
					throw ReferentielMetierException.conflict("DEMANDE_DOCUMENT_DEJA_EN_COURS",
							messageDoublon(ouverte));
				});
		// D-R05 : le SLA est lu dans le paramètre et COPIÉ dans la demande (C-M02-02) : modifier le SLA du type
		// plus tard ne change pas l'échéance des demandes déjà déposées.
		int sla = type.slaHeures() != null ? type.slaHeures() : SLA_PAR_DEFAUT_HEURES;
		// D-L02 : numéro attribué dans la transaction de création, rang du mois de l'instant de création
		Instant creation = Instant.now();
		Instant echeance = creation.plus(sla, ChronoUnit.HOURS);

		DemandeDocumentAdministratifRh d = new DemandeDocumentAdministratifRh();
		d.setCreeLe(creation);
		d.setNumero(numeroLisibleService.attribuer(PrefixeNumero.DOC, creation));
		d.setDemandeur(demandeur);
		d.setTypeDocument(type.code());
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		d.setDelaiSlaHeures(sla);
		d.setDateEcheanceTraitement(echeance);
		if (req.getCommentaireDemandeur() != null) {
			d.setCommentaireDemandeur(req.getCommentaireDemandeur().trim());
		}
		d = repository.save(d);
		parametreService.marquerUtilisee(REFERENTIEL_TYPE, type.code());

		try {
			notificationPublisher.notifierDemandeDocumentRecue(demandeur, libelleNotification(type.code()), d.getId(),
					d.getNumero());
		} catch (Exception e) {
			log.error("Erreur lors de l'envoi de la notification Kafka (Nouvelle demande)", e);
		}

		return toResponse(d);
	}

	/**
	 * D-F06.c : pré-contrôle pour l'appli (ne remplace pas le contrôle du POST). Type inconnu → 400.
	 */
	@Transactional(readOnly = true)
	public CheckRestrictionDocumentResponse verifierRestriction(String typeDocument, Jwt jwt) {
		Collaborateur demandeur = collaborateurConnecteService.exigerCollaborateur(jwt);
		String type = parserType(typeDocument);
		return repository.findFirstByDemandeurIdAndTypeDocumentAndStatutInOrderByCreeLeAsc(
						demandeur.getId(), type, STATUTS_OUVERTS)
				.map(ouverte -> new CheckRestrictionDocumentResponse(false, messageDoublon(ouverte)))
				.orElseGet(() -> new CheckRestrictionDocumentResponse(true, null));
	}

	private String parserType(String code) {
		return parametreService.lire(REFERENTIEL_TYPE, code)
				.map(ParametreVue::code)
				.orElseThrow(() -> new IllegalArgumentException("Type de document inconnu."));
	}

	/** Type existant (400 {@code TYPE_DOCUMENT_INVALIDE} sinon) et actif (422 {@code TYPE_DOCUMENT_INACTIF} sinon). */
	private ParametreVue exigerTypeActif(String code) {
		ParametreVue v = parametreService.lire(REFERENTIEL_TYPE, code).orElseThrow(() ->
				ReferentielMetierException.badRequest("TYPE_DOCUMENT_INVALIDE", "Type de document inconnu."));
		if (!v.actif()) {
			throw ReferentielMetierException.unprocessable("TYPE_DOCUMENT_INACTIF",
					"Le type de document « " + v.libelle() + " » n'est plus proposé.");
		}
		return v;
	}

	/** Libellé courant du type de document ; repli lisible si le code n'a plus de ligne. */
	private String libelleType(String code) {
		String l = parametreService.libelleOuDefaut(REFERENTIEL_TYPE, code);
		return l != null ? l : ValeursInitialesReferentiel.libelleParDefaut(code);
	}

	/** Libellé pour une phrase (« attestation de travail »), sans perdre un sigle (« attestation CNSS »). */
	private String libelleNotification(String code) {
		return ParametreService.enMinuscule(libelleType(code));
	}

	private String libelleStatut(StatutDocumentAdministratifDemandeRh statut) {
		String l = parametreService.libelleOuDefaut("STATUT_DOCUMENT", statut.name());
		return l != null ? l : statut.getLibelle();
	}

	private String messageDoublon(DemandeDocumentAdministratifRh ouverte) {
		return "Vous avez déjà une demande de " + libelleNotification(ouverte.getTypeDocument())
				+ " en cours (déposée le " + FORMAT_JOUR.format(ouverte.getCreeLe().atZone(FUSEAU_TUNIS)) + ").";
	}

	@Transactional(readOnly = true)
	public List<DemandeDocumentAdministratifRhResponse> mesDemandes(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return repository.findByDemandeurIdOrderByCreeLeDesc(c.getId()).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeDocumentAdministratifRhResponse> fileAttentePourRh(String q) {
		String numero = NumeroLisibleService.normaliserRecherche(q);
		List<DemandeDocumentAdministratifRh> lignes = repository.findByStatutInOrderByCreeLeAsc(STATUTS_OUVERTS);
		if (numero != null) {
			// D-L02 : recherche par numéro exact dans la file (les demandes clôturées n'y figurent pas)
			lignes = lignes.stream().filter(d -> numero.equals(d.getNumero())).collect(Collectors.toList());
		}
		return lignes.stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	/**
	 * D-N01 : liste back-office de tous les statuts (la file d'attente reste à part), filtrable par demandeur (fiche),
	 * statut et numéro DOC- exact. Plus récentes d'abord. Un demandeur inconnu donne une liste vide.
	 */
	@Transactional(readOnly = true)
	public List<DemandeDocumentAdministratifRhResponse> listerPourRh(UUID collaborateurId,
			StatutDocumentAdministratifDemandeRh statut, String q) {
		String numero = NumeroLisibleService.normaliserRecherche(q);
		List<DemandeDocumentAdministratifRh> lignes = collaborateurId != null
				? repository.findByDemandeurIdOrderByCreeLeDesc(collaborateurId)
				: repository.findAll(org.springframework.data.domain.Sort.by(
						org.springframework.data.domain.Sort.Direction.DESC, "creeLe"));
		return lignes.stream()
				.filter(d -> statut == null || d.getStatut() == statut)
				.filter(d -> numero == null || numero.equals(d.getNumero()))
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeDocumentAdministratifRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new org.springframework.security.access.AccessDeniedException("Accès refusé.");
			}
		}
		return toResponse(d);
	}

	@Transactional(readOnly = true)
	public DemandeDocumentSuiviResponse suivi(UUID id, Jwt jwt, boolean rh) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new org.springframework.security.access.AccessDeniedException("Accès refusé.");
			}
		}
		Integer rang = rangDansFile(d);
		int devant = rang != null ? rang - 1 : 0;
		boolean enRetard = d.getStatut() != StatutDocumentAdministratifDemandeRh.DISPONIBLE
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.REJETEE
				&& Instant.now().isAfter(d.getDateEcheanceTraitement());
		DemandeDocumentSuiviResponse suivi = new DemandeDocumentSuiviResponse(
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
		suivi.setNumero(d.getNumero());
		suivi.setTypeDocumentLibelle(libelleType(d.getTypeDocument()));
		suivi.setStatutLibelle(parametreService.libelleOuInconnu("STATUT_DOCUMENT", d.getStatut().name()));
		return suivi;
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
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Cette demande est déjà décidée : statut « " + libelleStatut(d.getStatut()) + " ».");
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
			notificationPublisher.notifierDocumentDisponible(d.getDemandeur(), libelleNotification(d.getTypeDocument()),
					req.getReferenceLivrable(), d.getId(), d.getNumero());
		} catch (Exception e) {
			log.error("Erreur lors de l'envoi de la notification Kafka (Document disponible)", e);
		}

		return toResponse(saved);
	}

	@Transactional
	public DemandeDocumentAdministratifRhResponse rejeter(UUID id, DocumentRejetRhRequest req, Jwt jwt) {
		DemandeDocumentAdministratifRh d = repository.findById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		if (d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Cette demande est déjà décidée : statut « " + libelleStatut(d.getStatut()) + " ».");
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
		// Demande déjà prise en traitement (via prendre-prochaine ou reprise) :
		// elle est la tête de file active — pas de dérogation à re-demander pour clôturer (D-F04).
		if (demande.getStatut() == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH) {
			return;
		}

		// Si le RH tente de traiter une nouvelle demande alors qu'il y en a déjà une en cours, on bloque.
		if (demande.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			if (repository.existsByStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH)) {
				throw new IllegalStateException("Vous devez terminer le traitement en cours avant de prendre une nouvelle demande.");
			}
		}

		Optional<DemandeDocumentAdministratifRh> plusAncienne = repository
				.findFirstByStatutOrderByCreeLeAsc(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);

		// S'il n'y a aucune demande en attente, ou si la demande courante EST la plus ancienne → OK
		if (plusAncienne.isEmpty() || plusAncienne.get().getId().equals(demande.getId())) {
			return;
		}

		// La demande n'est pas la prochaine dans l'ordre FIFO
		// → une justification est obligatoire (D-F03)
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
						libelleNotification(demande.getTypeDocument()), 
						justificationDerogation.trim(), 
						demande.getDemandeur(),
						demande.getId(),
						demande.getNumero()
				);
			} catch (Exception e) {
				log.error("Erreur lors de l'envoi de la notification au DRH pour dérogation FIFO", e);
			}
		}
	}

	// ─── Workflow Steps ──────────────────────────────────────────────────────

	private static List<WorkflowEtapeResponse> etapesDocument(StatutDocumentAdministratifDemandeRh s) {
		List<WorkflowEtapeResponse> list = new ArrayList<>();
		list.add(new WorkflowEtapeResponse("TRANSMISE_RRH", "Demande transmise à la RH (file unique)", true, false));

		boolean fileTerminee = s == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH
				|| s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		boolean fileEnCours = s == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE;
		list.add(new WorkflowEtapeResponse("FILE_ATTENTE",
				"En file d'attente — ordre chronologique (premier arrivé, premier servi)", fileTerminee, fileEnCours));

		boolean traitTerminee = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		boolean traitEnCours = s == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH;
		list.add(new WorkflowEtapeResponse("TRAITEMENT_RH", "Traitement par la RH", traitTerminee, traitEnCours));

		boolean cloture = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE
				|| s == StatutDocumentAdministratifDemandeRh.REJETEE;
		String libelle = s == StatutDocumentAdministratifDemandeRh.DISPONIBLE ? "Document disponible"
				: s == StatutDocumentAdministratifDemandeRh.REJETEE ? "Demande refusée par la RH" : "Clôture";
		list.add(new WorkflowEtapeResponse("CLOTURE", libelle, cloture, false));

		return list;
	}

	// ─── Response Mapping ────────────────────────────────────────────────────

	/**
	 * Position dans la file unique (SCRUM-5) : la demande en traitement occupe la tête (rang 1),
	 * les demandes en attente suivent par {@code cree_le}. Null une fois la demande clôturée.
	 */
	private Integer rangDansFile(DemandeDocumentAdministratifRh d) {
		if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH) {
			return 1;
		}
		if (d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE) {
			long enTraitement = repository.countByStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);
			long avant = repository.countByStatutAndCreeLeBefore(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, d.getCreeLe());
			return (int) (enTraitement + avant) + 1;
		}
		return null;
	}

	private DemandeDocumentAdministratifRhResponse toResponse(DemandeDocumentAdministratifRh d) {
		Integer rang = rangDansFile(d);
		// « Prochaine » = la plus ancienne encore en attente, la seule que prendre-prochaine sélectionnera.
		// Une demande déjà en traitement n'est pas « prochaine » ; la dérogation FIFO ne la concerne pas (D-F04).
		boolean estProchaineFifo = d.getStatut() == StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE
				&& repository.countByStatutAndCreeLeBefore(
						StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE, d.getCreeLe()) == 0;

		boolean enRetard = d.getStatut() != StatutDocumentAdministratifDemandeRh.DISPONIBLE
				&& d.getStatut() != StatutDocumentAdministratifDemandeRh.REJETEE
				&& Instant.now().isAfter(d.getDateEcheanceTraitement());

		DemandeDocumentAdministratifRhResponse r = new DemandeDocumentAdministratifRhResponse(
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
		r.setNumero(d.getNumero());
		r.setTypeDocumentLibelle(libelleType(d.getTypeDocument()));
		r.setStatutLibelle(parametreService.libelleOuInconnu("STATUT_DOCUMENT", d.getStatut().name()));
		Collaborateur demandeur = d.getDemandeur();
		r.setDemandeurNom((demandeur.getPrenom() + " " + demandeur.getNom()).trim());
		r.setDemandeurMatricule(demandeur.getMatricule());
		r.setDemandeurUnite(demandeur.getUnite() != null ? demandeur.getUnite().getLibelle() : null);
		return r;
	}
}
