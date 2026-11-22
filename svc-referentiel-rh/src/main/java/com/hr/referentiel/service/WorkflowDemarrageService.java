package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceRequest;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.referentiel.client.WorkflowMoteurClient.EtapeCouranteDto;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueDemarrage;
import com.hr.referentiel.client.WorkflowMoteurClient.SuiviObjetInterne;
import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.ActionWorkflowFormation;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.FormationWorkflowHistory;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Démarrage d'une instance chez {@code svc-workflow} hors transaction (D-W5-04, D-W5-05).
 * Chaque lecture/écriture en base se fait dans une courte transaction dédiée ; l'appel HTTP est toujours exécuté
 * sans transaction active.
 */
@Service
public class WorkflowDemarrageService {

	public static final String ETAT_A_DEMARRER = "A_DEMARRER";
	public static final String ETAT_DEMARRE = "DEMARRE";
	public static final String ETAT_ECHEC = "ECHEC";
	public static final int MAX_TENTATIVES = 10;

	private static final Logger log = LoggerFactory.getLogger(WorkflowDemarrageService.class);

	private final DemandeAdministrativeRhRepository demandeRepo;
	private final CollaborateurRepository collaborateurRepo;
	private final DemandeAdminWorkflowHistoryRepository historyRepo;
	private final WorkflowMoteurClient moteurClient;
	private final WorkflowVariablesBuilder variablesBuilder;
	private final WorkflowEffetService effetService;
	private final RhNotificationPublisher notificationPublisher;
	private final TransactionTemplate txLecture;
	private final TransactionTemplate txEcriture;
	private final DemandeDocumentAdministratifRhRepository documentRepo;
	private final DemandeFormationRhRepository formationRepo;
	private final FormationWorkflowHistoryRepository formationHistoryRepo;
	private final ParametreService parametreService;

	/** Constructeur historique M01 conservé pour les tests existants. */
	public WorkflowDemarrageService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepo,
			DemandeAdminWorkflowHistoryRepository historyRepo,
			WorkflowMoteurClient moteurClient,
			WorkflowVariablesBuilder variablesBuilder,
			WorkflowEffetService effetService,
			RhNotificationPublisher notificationPublisher,
			PlatformTransactionManager txManager) {
		this(demandeRepo, collaborateurRepo, historyRepo, moteurClient, variablesBuilder, effetService,
				notificationPublisher, txManager, null, null, null, null);
	}

	@Autowired
	public WorkflowDemarrageService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepo,
			DemandeAdminWorkflowHistoryRepository historyRepo,
			WorkflowMoteurClient moteurClient,
			WorkflowVariablesBuilder variablesBuilder,
			WorkflowEffetService effetService,
			RhNotificationPublisher notificationPublisher,
			PlatformTransactionManager txManager,
			DemandeDocumentAdministratifRhRepository documentRepo,
			DemandeFormationRhRepository formationRepo,
			FormationWorkflowHistoryRepository formationHistoryRepo,
			ParametreService parametreService) {
		this.demandeRepo = demandeRepo;
		this.collaborateurRepo = collaborateurRepo;
		this.historyRepo = historyRepo;
		this.moteurClient = moteurClient;
		this.variablesBuilder = variablesBuilder;
		this.effetService = effetService;
		this.notificationPublisher = notificationPublisher;
		this.txLecture = new TransactionTemplate(txManager);
		this.txLecture.setReadOnly(true);
		this.txEcriture = new TransactionTemplate(txManager);
		this.documentRepo = documentRepo;
		this.formationRepo = formationRepo;
		this.formationHistoryRepo = formationHistoryRepo;
		this.parametreService = parametreService;
	}

	private record ContexteDemarrage(
			UUID demandeId,
			TypeDemandeAdministrativeRh type,
			DemarrerInstanceRequest requete
	) {
	}

	/** Sélectionne un lot de demandes {@code A_DEMARRER} non annulées (courte transaction, {@code SKIP LOCKED}). */
	public List<UUID> selectionnerLotADemarrer(int limite) {
		UUID societeId = SocieteContexte.courante();
		if (societeId == null) {
			return List.of();
		}
		List<UUID> ids = txEcriture.execute(status -> demandeRepo.findIdsADemarrerPourReprise(societeId, limite));
		return ids != null ? ids : List.of();
	}

	/**
	 * Tente de démarrer l'instance workflow pour {@code demandeId}.
	 * Toujours appelé <strong>hors transaction</strong>.
	 */
	public void demarrer(UUID demandeId) {
		ContexteDemarrage ctx = txLecture.execute(status -> preparerContexte(demandeId));
		if (ctx == null) {
			return;
		}
		String cle = ctx.type().name() + ":" + demandeId;
		IssueDemarrage issue = moteurClient.demarrer(cle, ctx.requete());

		if (issue instanceof IssueDemarrage.Demarre d) {
			DemarrerInstanceResponse rep = d.reponse();
			boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
					status -> rattacherInstance(demandeId, rep.instanceId(), rep.etapeCourante())));
			if (!rattache) {
				annulerInstanceOrpheline(demandeId, rep.instanceId());
			} else if ("APPROUVER".equals(rep.effetImmediat())) {
				effetService.appliquer(new RequeteEffet(
						"demarrage:" + rep.instanceId(),
						WorkflowEffetService.MODE_APPLIQUER,
						ctx.type().name(),
						demandeId,
						"APPROUVER",
						new ActeurEffet(null, null, AuteurParametre.NOM_SYSTEME, null),
						null,
						rep.instanceId(),
						null));
			}
		} else if (issue instanceof IssueDemarrage.InstanceExistante) {
			Optional<SuiviObjetInterne> existante = moteurClient.obtenirParObjet(ctx.type().name(), demandeId);
			if (existante.isPresent() && existante.get().instanceId() != null) {
				SuiviObjetInterne s = existante.get();
				boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
						status -> rattacherInstance(demandeId, s.instanceId(), s.etapeCourante())));
				if (!rattache) {
					annulerInstanceOrpheline(demandeId, s.instanceId());
				}
			} else {
				enregistrerEchecEtReplierSiSeuil(demandeId);
			}
		} else if (issue instanceof IssueDemarrage.ModeHerite || issue instanceof IssueDemarrage.WorkflowAbsent) {
			log.warn("Repli sur circuit hérité pour demandeId={} (issue={})",
					demandeId, issue.getClass().getSimpleName());
			txEcriture.executeWithoutResult(status -> replierSurHerite(demandeId, false));
		} else if (issue instanceof IssueDemarrage.Indisponible) {
			enregistrerEchecEtReplierSiSeuil(demandeId);
		}
	}

	/**
	 * L'instance a été créée chez le moteur mais la demande ne peut plus la porter (annulée ou repliée
	 * sur le circuit hérité pendant le démarrage) : on l'annule pour ne pas laisser de tâche fantôme.
	 * Appelé hors transaction ; un échec est seulement journalisé.
	 */
	private void annulerInstanceOrpheline(UUID demandeId, UUID instanceId) {
		if (instanceId == null) {
			return;
		}
		WorkflowMoteurClient.IssueAnnulation issue = moteurClient.annuler(instanceId,
				"Demande annulée ou repliée sur le circuit standard pendant le démarrage",
				new WorkflowMoteurClient.ActeurInterne(null, null, AuteurParametre.NOM_SYSTEME));
		if (issue != WorkflowMoteurClient.IssueAnnulation.ANNULEE) {
			log.warn("Instance orpheline non annulée instanceId={} demandeId={} (issue={})",
					instanceId, demandeId, issue);
		}
	}

	private ContexteDemarrage preparerContexte(UUID demandeId) {
		Optional<DemandeAdministrativeRh> opt = demandeRepo.findByIdAvecDemandeur(demandeId);
		if (opt.isEmpty()) {
			return null;
		}
		DemandeAdministrativeRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE) {
			return null;
		}
		Collaborateur dem = d.getDemandeur();
		Map<String, Object> variables = variablesBuilder.construire(d);
		String sousType = variablesBuilder.sousType(d);
		String nomDemandeur = ((dem.getPrenom() != null ? dem.getPrenom() : "") + " "
				+ (dem.getNom() != null ? dem.getNom() : "")).trim();
		DemarrerInstanceRequest req = new DemarrerInstanceRequest(
				d.getTypeDemande().name(),
				d.getId(),
				d.getNumero(),
				sousType,
				dem.getCompteUtilisateurId(),
				dem.getId(),
				nomDemandeur,
				List.of(),
				variables);
		return new ContexteDemarrage(demandeId, d.getTypeDemande(), req);
	}

	private boolean rattacherInstance(UUID demandeId, UUID instanceId, EtapeCouranteDto etape) {
		Optional<DemandeAdministrativeRh> opt = demandeRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return false;
		}
		DemandeAdministrativeRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE) {
			return false;
		}
		d.setWorkflowInstanceId(instanceId);
		d.setWorkflowEtat(ETAT_DEMARRE);
		if (etape != null) {
			d.setWorkflowEtapeLibelle(etape.libelle());
			d.setWorkflowEcheanceLe(etape.echeanceLe());
		}
		demandeRepo.save(d);
		return true;
	}

	private void enregistrerEchecEtReplierSiSeuil(UUID demandeId) {
		txEcriture.executeWithoutResult(status -> {
			Optional<DemandeAdministrativeRh> opt = demandeRepo.findByIdForUpdate(demandeId);
			if (opt.isEmpty()) {
				return;
			}
			DemandeAdministrativeRh d = opt.get();
			if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
					|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE) {
				return;
			}
			int tentatives = d.getWorkflowTentatives() + 1;
			d.setWorkflowTentatives(tentatives);
			if (tentatives >= MAX_TENTATIVES) {
				log.warn("Seuil de {} tentatives atteint pour demandeId={} : repli sur circuit hérité",
						MAX_TENTATIVES, demandeId);
				appliquerRepliHeriteSousVerrou(d, true);
			} else {
				demandeRepo.save(d);
			}
		});
	}

	private void replierSurHerite(UUID demandeId, boolean garderEtatEchec) {
		Optional<DemandeAdministrativeRh> opt = demandeRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return;
		}
		DemandeAdministrativeRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDemandeAdministrativeRh.ANNULEE) {
			return;
		}
		appliquerRepliHeriteSousVerrou(d, garderEtatEchec);
	}

	private void appliquerRepliHeriteSousVerrou(DemandeAdministrativeRh d, boolean garderEtatEchec) {
		Collaborateur demandeur = collaborateurRepo.findDetailById(d.getDemandeur().getId())
				.orElse(d.getDemandeur());
		Collaborateur valideurAttendu = SuperieurHierarchieRules.resoudreValideurM01(demandeur);
		d.setWorkflowMode(WorkflowActivationService.MODE_HERITE);
		d.setWorkflowEtat(garderEtatEchec ? ETAT_ECHEC : null);
		d.setValideurAttendu(valideurAttendu);
		d.setStatut(valideurAttendu != null
				? StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				: StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		demandeRepo.save(d);

		DemandeAdminWorkflowHistory h = new DemandeAdminWorkflowHistory();
		h.setDemandeAdministrative(d);
		h.setAction(ActionWorkflowAdministratif.SOUMISE_A_RO);
		h.setActeurIdentifiant(demandeur.getId());
		h.setActeurNom((demandeur.getPrenom() + " " + demandeur.getNom()).trim());
		h.setCommentaire("Demande soumise pour validation (repli sur le circuit standard)");
		historyRepo.save(h);

		notificationPublisher.notifierDemandeRecue(
				demandeur, d.getTypeDemande().name(), d.getId(), d.getNumero(), valideurAttendu);
		if (garderEtatEchec) {
			notificationPublisher.notifierRepliWorkflowRh(demandeur, d.getId(), d.getNumero());
		}
	}

	// ---- M02 : DOCUMENT -----------------------------------------------------------------------------------------

	public List<UUID> selectionnerDocumentsADemarrer(int limite) {
		if (documentRepo == null) {
			return List.of();
		}
		UUID societeId = SocieteContexte.courante();
		if (societeId == null) {
			return List.of();
		}
		List<UUID> ids = txEcriture.execute(status -> documentRepo.findIdsADemarrerPourReprise(societeId, limite));
		return ids != null ? ids : List.of();
	}

	public void demarrerDocument(UUID demandeId) {
		if (documentRepo == null) {
			return;
		}
		DemarrerInstanceRequest req = txLecture.execute(status -> preparerContexteDocument(demandeId));
		if (req == null) {
			return;
		}
		IssueDemarrage issue = moteurClient.demarrer("DOCUMENT:" + demandeId, req);

		if (issue instanceof IssueDemarrage.Demarre d) {
			DemarrerInstanceResponse rep = d.reponse();
			boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
					status -> rattacherDocument(demandeId, rep.instanceId(), rep.etapeCourante())));
			if (!rattache) {
				annulerInstanceOrpheline(demandeId, rep.instanceId());
			} else if ("APPROUVER".equals(rep.effetImmediat())) {
				effetService.appliquer(new RequeteEffet(
						"demarrage:" + rep.instanceId(),
						WorkflowEffetService.MODE_APPLIQUER,
						"DOCUMENT",
						demandeId,
						"APPROUVER",
						new ActeurEffet(null, null, AuteurParametre.NOM_SYSTEME, null),
						null,
						rep.instanceId(),
						null));
			}
		} else if (issue instanceof IssueDemarrage.InstanceExistante) {
			Optional<SuiviObjetInterne> existante = moteurClient.obtenirParObjet("DOCUMENT", demandeId);
			if (existante.isPresent() && existante.get().instanceId() != null) {
				SuiviObjetInterne s = existante.get();
				boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
						status -> rattacherDocument(demandeId, s.instanceId(), s.etapeCourante())));
				if (!rattache) {
					annulerInstanceOrpheline(demandeId, s.instanceId());
				}
			} else {
				enregistrerEchecDocumentEtReplierSiSeuil(demandeId);
			}
		} else if (issue instanceof IssueDemarrage.ModeHerite || issue instanceof IssueDemarrage.WorkflowAbsent) {
			log.warn("Repli sur circuit hérité pour DOCUMENT demandeId={} (issue={})",
					demandeId, issue.getClass().getSimpleName());
			txEcriture.executeWithoutResult(status -> replierDocument(demandeId, false));
		} else if (issue instanceof IssueDemarrage.Indisponible) {
			enregistrerEchecDocumentEtReplierSiSeuil(demandeId);
		}
	}

	private DemarrerInstanceRequest preparerContexteDocument(UUID demandeId) {
		Optional<DemandeDocumentAdministratifRh> opt = documentRepo.findByIdAvecDemandeur(demandeId);
		if (opt.isEmpty()) {
			return null;
		}
		DemandeDocumentAdministratifRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDocumentAdministratifDemandeRh.ANNULEE) {
			return null;
		}
		Collaborateur dem = d.getDemandeur();
		String nomDemandeur = ((dem.getPrenom() != null ? dem.getPrenom() : "") + " "
				+ (dem.getNom() != null ? dem.getNom() : "")).trim();
		return new DemarrerInstanceRequest(
				"DOCUMENT",
				d.getId(),
				d.getNumero(),
				d.getTypeDocument(),
				dem.getCompteUtilisateurId(),
				dem.getId(),
				nomDemandeur,
				List.of(),
				variablesBuilder.construireDocument(d));
	}

	private boolean rattacherDocument(UUID demandeId, UUID instanceId, EtapeCouranteDto etape) {
		Optional<DemandeDocumentAdministratifRh> opt = documentRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return false;
		}
		DemandeDocumentAdministratifRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| d.getStatut() == StatutDocumentAdministratifDemandeRh.ANNULEE) {
			return false;
		}
		d.setWorkflowInstanceId(instanceId);
		d.setWorkflowEtat(ETAT_DEMARRE);
		if (etape != null) {
			d.setWorkflowEtapeLibelle(etape.libelle());
			d.setWorkflowEcheanceLe(etape.echeanceLe());
		}
		documentRepo.save(d);
		return true;
	}

	private void enregistrerEchecDocumentEtReplierSiSeuil(UUID demandeId) {
		txEcriture.executeWithoutResult(status -> {
			Optional<DemandeDocumentAdministratifRh> opt = documentRepo.findByIdForUpdate(demandeId);
			if (opt.isEmpty()) {
				return;
			}
			DemandeDocumentAdministratifRh d = opt.get();
			if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
					|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getStatut() == StatutDocumentAdministratifDemandeRh.ANNULEE) {
				return;
			}
			int tentatives = d.getWorkflowTentatives() + 1;
			d.setWorkflowTentatives(tentatives);
			if (tentatives >= MAX_TENTATIVES) {
				log.warn("Seuil de {} tentatives atteint pour DOCUMENT demandeId={} : repli sur circuit hérité",
						MAX_TENTATIVES, demandeId);
				appliquerRepliDocumentSousVerrou(d, true);
			} else {
				documentRepo.save(d);
			}
		});
	}

	private void replierDocument(UUID demandeId, boolean garderEtatEchec) {
		Optional<DemandeDocumentAdministratifRh> opt = documentRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return;
		}
		DemandeDocumentAdministratifRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDocumentAdministratifDemandeRh.ANNULEE) {
			return;
		}
		appliquerRepliDocumentSousVerrou(d, garderEtatEchec);
	}

	private void appliquerRepliDocumentSousVerrou(DemandeDocumentAdministratifRh d, boolean garderEtatEchec) {
		d.setWorkflowMode(WorkflowActivationService.MODE_HERITE);
		d.setWorkflowEtat(garderEtatEchec ? ETAT_ECHEC : null);
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		documentRepo.save(d);
		Collaborateur demandeur = collaborateurRepo.findDetailById(d.getDemandeur().getId()).orElse(d.getDemandeur());
		String libelle = parametreService != null
				? parametreService.libelleOuDefaut("TYPE_DOCUMENT", d.getTypeDocument()) : null;
		if (libelle == null) {
			libelle = ValeursInitialesReferentiel.libelleParDefaut(d.getTypeDocument());
		}
		notificationPublisher.notifierDemandeDocumentRecue(
				demandeur, ParametreService.enMinuscule(libelle), d.getId(), d.getNumero());
		if (garderEtatEchec) {
			notificationPublisher.notifierRepliWorkflowRh(demandeur, d.getId(), d.getNumero());
		}
	}

	// ---- M05 : FORMATION ----------------------------------------------------------------------------------------

	public List<UUID> selectionnerFormationsADemarrer(int limite) {
		if (formationRepo == null) {
			return List.of();
		}
		UUID societeId = SocieteContexte.courante();
		if (societeId == null) {
			return List.of();
		}
		List<UUID> ids = txEcriture.execute(status -> formationRepo.findIdsADemarrerPourReprise(societeId, limite));
		return ids != null ? ids : List.of();
	}

	public void demarrerFormation(UUID demandeId) {
		if (formationRepo == null) {
			return;
		}
		DemarrerInstanceRequest req = txLecture.execute(status -> preparerContexteFormation(demandeId));
		if (req == null) {
			return;
		}
		IssueDemarrage issue = moteurClient.demarrer("FORMATION:" + demandeId, req);

		if (issue instanceof IssueDemarrage.Demarre d) {
			DemarrerInstanceResponse rep = d.reponse();
			boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
					status -> rattacherFormation(demandeId, rep.instanceId(), rep.etapeCourante())));
			if (!rattache) {
				annulerInstanceOrpheline(demandeId, rep.instanceId());
			} else if ("APPROUVER".equals(rep.effetImmediat())) {
				effetService.appliquer(new RequeteEffet(
						"demarrage:" + rep.instanceId(),
						WorkflowEffetService.MODE_APPLIQUER,
						"FORMATION",
						demandeId,
						"APPROUVER",
						new ActeurEffet(null, null, AuteurParametre.NOM_SYSTEME, null),
						null,
						rep.instanceId(),
						null));
			}
		} else if (issue instanceof IssueDemarrage.InstanceExistante) {
			Optional<SuiviObjetInterne> existante = moteurClient.obtenirParObjet("FORMATION", demandeId);
			if (existante.isPresent() && existante.get().instanceId() != null) {
				SuiviObjetInterne s = existante.get();
				boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
						status -> rattacherFormation(demandeId, s.instanceId(), s.etapeCourante())));
				if (!rattache) {
					annulerInstanceOrpheline(demandeId, s.instanceId());
				}
			} else {
				enregistrerEchecFormationEtReplierSiSeuil(demandeId);
			}
		} else if (issue instanceof IssueDemarrage.ModeHerite || issue instanceof IssueDemarrage.WorkflowAbsent) {
			log.warn("Repli sur circuit hérité pour FORMATION demandeId={} (issue={})",
					demandeId, issue.getClass().getSimpleName());
			txEcriture.executeWithoutResult(status -> replierFormation(demandeId, false));
		} else if (issue instanceof IssueDemarrage.Indisponible) {
			enregistrerEchecFormationEtReplierSiSeuil(demandeId);
		}
	}

	private DemarrerInstanceRequest preparerContexteFormation(UUID demandeId) {
		Optional<DemandeFormationRh> opt = formationRepo.findByIdAvecDemandeur(demandeId);
		if (opt.isEmpty()) {
			return null;
		}
		DemandeFormationRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDemandeFormationRh.ANNULEE) {
			return null;
		}
		Collaborateur dem = d.getDemandeur();
		String nomDemandeur = ((dem.getPrenom() != null ? dem.getPrenom() : "") + " "
				+ (dem.getNom() != null ? dem.getNom() : "")).trim();
		List<UUID> beneficiaires = d.getCollaborateursCibles() != null
				? new ArrayList<>(d.getCollaborateursCibles())
				: List.of();
		return new DemarrerInstanceRequest(
				"FORMATION",
				d.getId(),
				d.getNumero(),
				null,
				dem.getCompteUtilisateurId(),
				dem.getId(),
				nomDemandeur,
				beneficiaires,
				variablesBuilder.construireFormation(d));
	}

	private boolean rattacherFormation(UUID demandeId, UUID instanceId, EtapeCouranteDto etape) {
		Optional<DemandeFormationRh> opt = formationRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return false;
		}
		DemandeFormationRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| d.getStatut() == StatutDemandeFormationRh.ANNULEE) {
			return false;
		}
		d.setWorkflowInstanceId(instanceId);
		d.setWorkflowEtat(ETAT_DEMARRE);
		if (etape != null) {
			d.setWorkflowEtapeLibelle(etape.libelle());
			d.setWorkflowEcheanceLe(etape.echeanceLe());
		}
		formationRepo.save(d);
		return true;
	}

	private void enregistrerEchecFormationEtReplierSiSeuil(UUID demandeId) {
		txEcriture.executeWithoutResult(status -> {
			Optional<DemandeFormationRh> opt = formationRepo.findByIdForUpdate(demandeId);
			if (opt.isEmpty()) {
				return;
			}
			DemandeFormationRh d = opt.get();
			if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
					|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getStatut() == StatutDemandeFormationRh.ANNULEE) {
				return;
			}
			int tentatives = d.getWorkflowTentatives() + 1;
			d.setWorkflowTentatives(tentatives);
			if (tentatives >= MAX_TENTATIVES) {
				log.warn("Seuil de {} tentatives atteint pour FORMATION demandeId={} : repli sur circuit hérité",
						MAX_TENTATIVES, demandeId);
				appliquerRepliFormationSousVerrou(d, true);
			} else {
				formationRepo.save(d);
			}
		});
	}

	private void replierFormation(UUID demandeId, boolean garderEtatEchec) {
		Optional<DemandeFormationRh> opt = formationRepo.findByIdForUpdate(demandeId);
		if (opt.isEmpty()) {
			return;
		}
		DemandeFormationRh d = opt.get();
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() == StatutDemandeFormationRh.ANNULEE) {
			return;
		}
		appliquerRepliFormationSousVerrou(d, garderEtatEchec);
	}

	private void appliquerRepliFormationSousVerrou(DemandeFormationRh d, boolean garderEtatEchec) {
		d.setWorkflowMode(WorkflowActivationService.MODE_HERITE);
		d.setWorkflowEtat(garderEtatEchec ? ETAT_ECHEC : null);
		d.setStatut(StatutDemandeFormationRh.EN_VALIDATION_RRH);
		formationRepo.save(d);

		Collaborateur demandeur = collaborateurRepo.findDetailById(d.getDemandeur().getId()).orElse(d.getDemandeur());
		if (formationHistoryRepo != null) {
			FormationWorkflowHistory h = new FormationWorkflowHistory();
			h.setDemandeFormation(d);
			h.setAction(ActionWorkflowFormation.SOUMISE_A_RH);
			h.setActeurIdentifiant(demandeur.getId());
			h.setActeurNom((demandeur.getPrenom() + " " + demandeur.getNom()).trim());
			h.setCommentaire("Demande soumise au RH pour validation (repli sur le circuit standard)");
			formationHistoryRepo.save(h);
		}
		notificationPublisher.notifierDemandeFormationSoumise(
				demandeur, d.getTypeFormation(), d.getId(), d.getNumero());
		if (garderEtatEchec) {
			notificationPublisher.notifierRepliWorkflowRh(demandeur, d.getId(), d.getNumero());
		}
	}
}
