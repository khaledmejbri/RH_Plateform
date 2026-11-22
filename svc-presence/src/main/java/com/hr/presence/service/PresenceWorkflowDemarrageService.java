package com.hr.presence.service;

import com.hr.presence.client.PresenceWorkflowMoteurClient;
import com.hr.presence.client.PresenceWorkflowMoteurClient.DemarrerInstanceRequest;
import com.hr.presence.client.PresenceWorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.presence.client.PresenceWorkflowMoteurClient.EtapeCouranteDto;
import com.hr.presence.client.PresenceWorkflowMoteurClient.IssueDemarrage;
import com.hr.presence.client.PresenceWorkflowMoteurClient.SuiviObjetInterne;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.ActeurEffet;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.RequeteEffet;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Démarrage d'instances dans {@code svc-workflow} pour {@code POINTAGE_MANUEL} et {@code HEURES_SUP},
 * exécuté strictement hors transaction (D-W6-02 / D-W6-04 / D-W6-05), avec repli automatique vers {@code HERITE}.
 */
@Service
public class PresenceWorkflowDemarrageService {

	private static final Logger log = LoggerFactory.getLogger(PresenceWorkflowDemarrageService.class);
	private static final DateTimeFormatter HEURE_TUNIS = DateTimeFormatter.ofPattern("dd/MM à HH:mm")
			.withLocale(Locale.FRENCH)
			.withZone(ZoneId.of("Africa/Tunis"));

	public static final String ETAT_A_DEMARRER = "A_DEMARRER";
	public static final String ETAT_DEMARRE = "DEMARRE";
	public static final String ETAT_ECHEC = "ECHEC";
	public static final int MAX_TENTATIVES = 10;

	private final PresenceDemandeManuelleRepository demandeManuelleRepo;
	private final PresenceHeuresSupDemandeRepository heuresSupRepo;
	private final PresenceHeuresSupBeneficiaireRepository heuresSupBenefRepo;
	private final PresenceSiteRepository siteRepo;
	private final FenetreHoraireService fenetreService;
	private final PresenceWorkflowMoteurClient moteurClient;
	private final PresenceWorkflowVariablesBuilder variablesBuilder;
	private final PresenceWorkflowEffetService effetService;
	private final ReferentielClient referentielClient;
	private final PresenceEventPublisher eventPublisher;
	private final HeuresSupNotifier heuresSupNotifier;
	private final Clock clock;
	private final TransactionTemplate txLecture;
	private final TransactionTemplate txEcriture;

	public PresenceWorkflowDemarrageService(
			PresenceDemandeManuelleRepository demandeManuelleRepo,
			PresenceHeuresSupDemandeRepository heuresSupRepo,
			PresenceHeuresSupBeneficiaireRepository heuresSupBenefRepo,
			PresenceSiteRepository siteRepo,
			FenetreHoraireService fenetreService,
			PresenceWorkflowMoteurClient moteurClient,
			PresenceWorkflowVariablesBuilder variablesBuilder,
			PresenceWorkflowEffetService effetService,
			ReferentielClient referentielClient,
			PresenceEventPublisher eventPublisher,
			HeuresSupNotifier heuresSupNotifier,
			Clock clock,
			PlatformTransactionManager txManager) {
		this.demandeManuelleRepo = demandeManuelleRepo;
		this.heuresSupRepo = heuresSupRepo;
		this.heuresSupBenefRepo = heuresSupBenefRepo;
		this.siteRepo = siteRepo;
		this.fenetreService = fenetreService;
		this.moteurClient = moteurClient;
		this.variablesBuilder = variablesBuilder;
		this.effetService = effetService;
		this.referentielClient = referentielClient;
		this.eventPublisher = eventPublisher;
		this.heuresSupNotifier = heuresSupNotifier;
		this.clock = clock;
		this.txLecture = new TransactionTemplate(txManager);
		this.txLecture.setReadOnly(true);
		this.txEcriture = new TransactionTemplate(txManager);
		this.txEcriture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
	}

	public List<UUID> selectionnerLotPointagesManuels(int limite) {
		List<UUID> ids = txEcriture.execute(status -> demandeManuelleRepo.findIdsADemarrerPourReprise(limite));
		return ids != null ? ids : List.of();
	}

	public List<UUID> selectionnerLotHeuresSup(int limite) {
		List<UUID> ids = txEcriture.execute(status -> heuresSupRepo.findIdsADemarrerPourReprise(limite));
		return ids != null ? ids : List.of();
	}

	// ---- POINTAGE_MANUEL -----------------------------------------------------------------------------------------

	/** Démarre le workflow d'une demande de pointage manuel — toujours appelé hors transaction. */
	public void demarrerPointageManuel(UUID demandeId) {
		DemarrerInstanceRequest req = txLecture.execute(status -> preparerContextePointageManuel(demandeId));
		if (req == null) {
			return;
		}
		String cle = PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL + ":" + demandeId;
		IssueDemarrage issue = moteurClient.demarrer(cle, req);

		if (issue instanceof IssueDemarrage.Demarre d) {
			DemarrerInstanceResponse rep = d.reponse();
			boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
					status -> rattacherPointageManuel(demandeId, rep.instanceId(), rep.etapeCourante())));
			if (!rattache) {
				annulerInstanceOrpheline(demandeId, rep.instanceId());
			} else if ("APPROUVER".equals(rep.effetImmediat())) {
				effetService.appliquer(new RequeteEffet(
						"demarrage:" + rep.instanceId(),
						PresenceWorkflowEffetService.MODE_APPLIQUER,
						PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL,
						demandeId,
						"APPROUVER",
						new ActeurEffet(null, null, PresenceWorkflowEffetService.NOM_SYSTEME, null),
						null,
						rep.instanceId(),
						null));
			}
		} else if (issue instanceof IssueDemarrage.InstanceExistante) {
			Optional<SuiviObjetInterne> existante = moteurClient.obtenirParObjet(
					PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL, demandeId);
			if (existante.isPresent() && existante.get().instanceId() != null) {
				SuiviObjetInterne s = existante.get();
				boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
						status -> rattacherPointageManuel(demandeId, s.instanceId(), s.etapeCourante())));
				if (!rattache) {
					annulerInstanceOrpheline(demandeId, s.instanceId());
				}
			} else {
				enregistrerEchecPointageManuelEtReplierSiSeuil(demandeId);
			}
		} else if (issue instanceof IssueDemarrage.ModeHerite || issue instanceof IssueDemarrage.WorkflowAbsent) {
			log.warn("Repli sur circuit hérité pour POINTAGE_MANUEL demandeId={} (issue={})",
					demandeId, issue.getClass().getSimpleName());
			ValideurPointage valideur = resoudreValideurSiJwtPresent();
			txEcriture.executeWithoutResult(status -> replierPointageManuel(demandeId, false, valideur));
		} else if (issue instanceof IssueDemarrage.Indisponible) {
			enregistrerEchecPointageManuelEtReplierSiSeuil(demandeId);
		}
	}

	private DemarrerInstanceRequest preparerContextePointageManuel(UUID demandeId) {
		Optional<PresenceDemandeManuelle> opt = demandeManuelleRepo.findById(demandeId);
		if (opt.isEmpty()) {
			return null;
		}
		PresenceDemandeManuelle d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() != StatutDemandeManuelle.EN_COURS) {
			return null;
		}
		return new DemarrerInstanceRequest(
				PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL,
				d.getId(),
				d.getNumero(),
				d.getType() != null ? d.getType().name() : null,
				parseUuid(d.getCollaborateurCompteId()),
				parseUuid(d.getCollaborateurId()),
				d.getCollaborateurNom(),
				List.of(),
				variablesBuilder.pourPointageManuel(d));
	}

	private boolean rattacherPointageManuel(UUID demandeId, UUID instanceId, EtapeCouranteDto etape) {
		Optional<PresenceDemandeManuelle> opt = demandeManuelleRepo.findByIdPourDecision(demandeId);
		if (opt.isEmpty()) {
			return false;
		}
		PresenceDemandeManuelle d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| d.getStatut() != StatutDemandeManuelle.EN_COURS) {
			return false;
		}
		d.setWorkflowInstanceId(instanceId);
		d.setWorkflowEtat(ETAT_DEMARRE);
		if (etape != null) {
			d.setEtapeCouranteLibelle(etape.libelle());
			d.setEcheanceLe(etape.echeanceLe());
		}
		demandeManuelleRepo.save(d);
		return true;
	}

	private void enregistrerEchecPointageManuelEtReplierSiSeuil(UUID demandeId) {
		ValideurPointage valideur = resoudreValideurSiJwtPresent();
		txEcriture.executeWithoutResult(status -> {
			Optional<PresenceDemandeManuelle> opt = demandeManuelleRepo.findByIdPourDecision(demandeId);
			if (opt.isEmpty()) {
				return;
			}
			PresenceDemandeManuelle d = opt.get();
			if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
					|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getStatut() != StatutDemandeManuelle.EN_COURS) {
				return;
			}
			int tentatives = d.getWorkflowTentatives() + 1;
			d.setWorkflowTentatives(tentatives);
			if (tentatives >= MAX_TENTATIVES) {
				log.warn("Seuil de {} tentatives atteint pour POINTAGE_MANUEL demandeId={} : repli hérité",
						MAX_TENTATIVES, demandeId);
				appliquerRepliPointageManuelSousVerrou(d, true, valideur);
			} else {
				demandeManuelleRepo.save(d);
			}
		});
	}

	private void replierPointageManuel(UUID demandeId, boolean garderEtatEchec, ValideurPointage valideur) {
		Optional<PresenceDemandeManuelle> opt = demandeManuelleRepo.findByIdPourDecision(demandeId);
		if (opt.isEmpty()) {
			return;
		}
		PresenceDemandeManuelle d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() != StatutDemandeManuelle.EN_COURS) {
			return;
		}
		appliquerRepliPointageManuelSousVerrou(d, garderEtatEchec, valideur);
	}

	private void appliquerRepliPointageManuelSousVerrou(
			PresenceDemandeManuelle d, boolean garderEtatEchec, ValideurPointage valideur) {
		d.setWorkflowMode(PresenceWorkflowActivationService.MODE_HERITE);
		d.setWorkflowEtat(garderEtatEchec ? ETAT_ECHEC : null);
		d.setStatut(StatutDemandeManuelle.EN_ATTENTE);
		if (valideur != null) {
			d.setValideurId(valideur.valideurId() != null ? valideur.valideurId().toString() : null);
			d.setValideurCompteId(valideur.valideurCompteId() != null ? valideur.valideurCompteId().toString() : null);
			d.setValideurNom(valideur.valideurNom());
		}
		PresenceDemandeManuelle saved = demandeManuelleRepo.save(d);

		String nom = saved.getCollaborateurNom() != null ? saved.getCollaborateurNom() : "Un collaborateur";
		String libelleType = saved.getType() == PointageType.SORTIE ? "sortie" : "entrée";
		String destinataire = saved.validationParRh()
				? PresenceEventPublisher.DESTINATAIRE_RH
				: (saved.getValideurCompteId() != null ? saved.getValideurCompteId() : saved.getValideurId());
		eventPublisher.notifier(
				destinataire,
				"Pointage manuel à valider — " + nom + " (" + saved.getNumero() + ")",
				nom + " demande un pointage manuel (" + libelleType + " du " + HEURE_TUNIS.format(saved.getHeureDeclaree())
						+ ") : " + saved.getJustification(),
				ObjetNotification.de(ObjetNotification.POINTAGE_MANUEL, "POINTAGE_MANUEL_DEMANDE",
						saved.getId(), saved.getNumero()));
	}

	// ---- HEURES_SUP ----------------------------------------------------------------------------------------------

	/** Démarre le workflow d'une demande d'heures supplémentaires — toujours appelé hors transaction. */
	public void demarrerHeuresSup(UUID demandeId) {
		DemarrerInstanceRequest req = txLecture.execute(status -> preparerContexteHeuresSup(demandeId));
		if (req == null) {
			return;
		}
		String cle = PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP + ":" + demandeId;
		IssueDemarrage issue = moteurClient.demarrer(cle, req);

		if (issue instanceof IssueDemarrage.Demarre d) {
			DemarrerInstanceResponse rep = d.reponse();
			boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
					status -> rattacherHeuresSup(demandeId, rep.instanceId(), rep.etapeCourante())));
			if (!rattache) {
				annulerInstanceOrpheline(demandeId, rep.instanceId());
			} else if ("APPROUVER".equals(rep.effetImmediat())) {
				effetService.appliquer(new RequeteEffet(
						"demarrage:" + rep.instanceId(),
						PresenceWorkflowEffetService.MODE_APPLIQUER,
						PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP,
						demandeId,
						"APPROUVER",
						new ActeurEffet(req.demandeurCompteId(), req.demandeurCollaborateurId(),
								req.demandeurNom() != null ? req.demandeurNom() : PresenceWorkflowEffetService.NOM_SYSTEME,
								null),
						null,
						rep.instanceId(),
						null));
			}
		} else if (issue instanceof IssueDemarrage.InstanceExistante) {
			Optional<SuiviObjetInterne> existante = moteurClient.obtenirParObjet(
					PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP, demandeId);
			if (existante.isPresent() && existante.get().instanceId() != null) {
				SuiviObjetInterne s = existante.get();
				boolean rattache = Boolean.TRUE.equals(txEcriture.execute(
						status -> rattacherHeuresSup(demandeId, s.instanceId(), s.etapeCourante())));
				if (!rattache) {
					annulerInstanceOrpheline(demandeId, s.instanceId());
				}
			} else {
				enregistrerEchecHeuresSupEtReplierSiSeuil(demandeId);
			}
		} else if (issue instanceof IssueDemarrage.ModeHerite || issue instanceof IssueDemarrage.WorkflowAbsent) {
			log.warn("Repli sur circuit hérité pour HEURES_SUP demandeId={} (issue={})",
					demandeId, issue.getClass().getSimpleName());
			txEcriture.executeWithoutResult(status -> replierHeuresSup(demandeId, false));
		} else if (issue instanceof IssueDemarrage.Indisponible) {
			enregistrerEchecHeuresSupEtReplierSiSeuil(demandeId);
		}
	}

	private DemarrerInstanceRequest preparerContexteHeuresSup(UUID demandeId) {
		Optional<PresenceHeuresSupDemande> opt = heuresSupRepo.findById(demandeId);
		if (opt.isEmpty()) {
			return null;
		}
		PresenceHeuresSupDemande d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() != StatutHeuresSup.EN_COURS) {
			return null;
		}
		List<PresenceHeuresSupBeneficiaire> benefs = heuresSupBenefRepo.findByDemandeId(d.getId());
		List<UUID> beneficiairesIds = benefs.stream()
				.map(PresenceHeuresSupBeneficiaire::getCollaborateurId)
				.filter(Objects::nonNull)
				.toList();
		PresenceSite site = d.getSiteId() != null ? siteRepo.findById(d.getSiteId()).orElse(null) : null;
		return new DemarrerInstanceRequest(
				PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP,
				d.getId(),
				d.getNumero() != null ? d.getNumero() : d.getReference(),
				null,
				parseUuid(d.getDemandeurCompteId()),
				parseUuid(d.getDemandeurFicheId()),
				d.getDemandeurNom(),
				beneficiairesIds,
				variablesBuilder.pourHeuresSup(d, benefs.size(), site));
	}

	private boolean rattacherHeuresSup(UUID demandeId, UUID instanceId, EtapeCouranteDto etape) {
		Optional<PresenceHeuresSupDemande> opt = heuresSupRepo.findByIdPourDecision(demandeId);
		if (opt.isEmpty()) {
			return false;
		}
		PresenceHeuresSupDemande d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| d.getStatut() != StatutHeuresSup.EN_COURS) {
			return false;
		}
		d.setWorkflowInstanceId(instanceId);
		d.setWorkflowEtat(ETAT_DEMARRE);
		if (etape != null) {
			d.setEtapeCouranteLibelle(etape.libelle());
			d.setEcheanceLe(etape.echeanceLe());
		}
		heuresSupRepo.save(d);
		return true;
	}

	private void enregistrerEchecHeuresSupEtReplierSiSeuil(UUID demandeId) {
		txEcriture.executeWithoutResult(status -> {
			Optional<PresenceHeuresSupDemande> opt = heuresSupRepo.findByIdPourDecision(demandeId);
			if (opt.isEmpty()) {
				return;
			}
			PresenceHeuresSupDemande d = opt.get();
			if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
					|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
					|| d.getStatut() != StatutHeuresSup.EN_COURS) {
				return;
			}
			int tentatives = d.getWorkflowTentatives() + 1;
			d.setWorkflowTentatives(tentatives);
			if (tentatives >= MAX_TENTATIVES) {
				log.warn("Seuil de {} tentatives atteint pour HEURES_SUP demandeId={} : repli hérité",
						MAX_TENTATIVES, demandeId);
				appliquerRepliHeuresSupSousVerrou(d, true);
			} else {
				heuresSupRepo.save(d);
			}
		});
	}

	private void replierHeuresSup(UUID demandeId, boolean garderEtatEchec) {
		Optional<PresenceHeuresSupDemande> opt = heuresSupRepo.findByIdPourDecision(demandeId);
		if (opt.isEmpty()) {
			return;
		}
		PresenceHeuresSupDemande d = opt.get();
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| !ETAT_A_DEMARRER.equals(d.getWorkflowEtat())
				|| d.getStatut() != StatutHeuresSup.EN_COURS) {
			return;
		}
		appliquerRepliHeuresSupSousVerrou(d, garderEtatEchec);
	}

	private void appliquerRepliHeuresSupSousVerrou(PresenceHeuresSupDemande d, boolean garderEtatEchec) {
		d.setWorkflowMode(PresenceWorkflowActivationService.MODE_HERITE);
		d.setWorkflowEtat(garderEtatEchec ? ETAT_ECHEC : null);
		List<PresenceHeuresSupBeneficiaire> benefs = heuresSupBenefRepo.findByDemandeId(d.getId());
		String resume = construireResumeHeuresSup(d);

		if (d.isCreeeValidee()) {
			d.setStatut(StatutHeuresSup.VALIDEE);
			d.setDecideParCompteId(d.getDemandeurCompteId());
			d.setDecideParNom(d.getDemandeurNom());
			d.setDecideLe(clock.instant());
			PresenceHeuresSupDemande saved = heuresSupRepo.save(d);
			heuresSupNotifier.creeeValidee(saved, resume, benefs);
		} else {
			d.setStatut(StatutHeuresSup.EN_ATTENTE);
			PresenceHeuresSupDemande saved = heuresSupRepo.save(d);
			heuresSupNotifier.creeeEnAttente(saved, resume);
		}
	}

	private String construireResumeHeuresSup(PresenceHeuresSupDemande d) {
		PresenceSite site = d.getSiteId() != null ? siteRepo.findById(d.getSiteId()).orElse(null) : null;
		String siteLibelle = site != null ? site.getLibelle() : "Site";
		String fenetre = "";
		if (site != null && d.getDateDebut() != null) {
			var horaires = fenetreService.horaires(site.getId());
			var h = horaires.get(d.getDateDebut().getDayOfWeek());
			if (h != null) {
				fenetre = HeuresSupNotifier.fenetreEtHeuresSup(
						FenetreHoraireService.fenetre(d.getDateDebut(), h,
								site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(),
								d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes()),
						d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
			}
		}
		if (fenetre.isEmpty()) {
			fenetre = "+" + d.getOuvertureAvantMinutes() + " min / +" + d.getOuvertureApresMinutes() + " min";
		}
		return HeuresSupNotifier.resume(siteLibelle, d.getDateDebut(), d.getDateFin(), fenetre);
	}

	// ---- Outils --------------------------------------------------------------------------------------------------

	private void annulerInstanceOrpheline(UUID demandeId, UUID instanceId) {
		if (instanceId == null) {
			return;
		}
		PresenceWorkflowMoteurClient.IssueAnnulation issue = moteurClient.annuler(
				instanceId,
				"Demande annulée ou repliée sur le circuit standard pendant le démarrage",
				new PresenceWorkflowMoteurClient.ActeurInterne(null, null, PresenceWorkflowEffetService.NOM_SYSTEME));
		if (issue != PresenceWorkflowMoteurClient.IssueAnnulation.ANNULEE) {
			log.warn("Instance orpheline non annulée instanceId={} demandeId={} (issue={})",
					instanceId, demandeId, issue);
		}
	}

	private ValideurPointage resoudreValideurSiJwtPresent() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof JwtAuthenticationToken)) {
			return null;
		}
		try {
			return referentielClient.monValideurPointage();
		} catch (RuntimeException ex) {
			log.warn("Impossible de résoudre le valideur au repli : {}", ex.getMessage());
			return null;
		}
	}

	private static UUID parseUuid(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(s.trim());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
