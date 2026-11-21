package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceRequest;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.referentiel.client.WorkflowMoteurClient.EtapeCouranteDto;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueDemarrage;
import com.hr.referentiel.client.WorkflowMoteurClient.SuiviObjetInterne;
import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

	@Autowired
	public WorkflowDemarrageService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepo,
			DemandeAdminWorkflowHistoryRepository historyRepo,
			WorkflowMoteurClient moteurClient,
			WorkflowVariablesBuilder variablesBuilder,
			WorkflowEffetService effetService,
			RhNotificationPublisher notificationPublisher,
			PlatformTransactionManager txManager) {
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
			if (rattache && "APPROUVER".equals(rep.effetImmediat())) {
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
				txEcriture.executeWithoutResult(
						status -> rattacherInstance(demandeId, s.instanceId(), s.etapeCourante()));
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
}
