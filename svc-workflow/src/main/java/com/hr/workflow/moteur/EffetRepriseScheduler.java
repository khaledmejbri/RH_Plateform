package com.hr.workflow.moteur;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.entity.WfAppelEffet;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.moteur.EffetClient.ActeurEffet;
import com.hr.workflow.moteur.EffetClient.RequeteEffet;
import com.hr.workflow.moteur.EffetClient.ResultatAppelEffet;
import com.hr.workflow.repository.WfAppelEffetRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Planificateur de reprise des effets incertains (D-W29, § 9.6.6) :
 * 1 min, 5 min, 15 min, puis toutes les heures pendant 24 h ; au-delà, instance {@code ERREUR}
 * et notification aux {@code ADMIN}.
 * Sous {@link VerrouPlanificateur} et {@code FOR UPDATE SKIP LOCKED}, appels HTTP strictement hors transaction.
 */
@Component
public class EffetRepriseScheduler {

	private static final Logger log = LoggerFactory.getLogger(EffetRepriseScheduler.class);
	private static final int TAILLE_LOT = 50;
	private static final Duration DUREE_MAX_REPRISE = Duration.ofHours(24);

	private final WfAppelEffetRepository appelEffetRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfTacheRepository tacheRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final CatalogueTypesObjet catalogue;
	private final EffetClient effetClient;
	private final OutboxService outboxService;
	private final VerrouPlanificateur verrouPlanificateur;
	private final TransactionTemplate txTemplate;
	private final Clock clock;

	public EffetRepriseScheduler(
			WfAppelEffetRepository appelEffetRepository,
			WfInstanceRepository instanceRepository,
			WfTacheRepository tacheRepository,
			WfHistoriqueRepository historiqueRepository,
			CatalogueTypesObjet catalogue,
			EffetClient effetClient,
			OutboxService outboxService,
			VerrouPlanificateur verrouPlanificateur,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.appelEffetRepository = appelEffetRepository;
		this.instanceRepository = instanceRepository;
		this.tacheRepository = tacheRepository;
		this.historiqueRepository = historiqueRepository;
		this.catalogue = catalogue;
		this.effetClient = effetClient;
		this.outboxService = outboxService;
		this.verrouPlanificateur = verrouPlanificateur;
		this.txTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(cron = "${workflow.effets.reprise-cron:0 * * * * *}", zone = "Africa/Tunis")
	public void executer() {
		verrouPlanificateur.executerSiLibre("wf-effet-reprise", this::reprendreIncertains);
	}

	private record EffetARejouer(
			UUID appelEffetId,
			String cleIdempotence,
			UUID instanceId,
			UUID tacheId,
			String effet,
			String serviceProprietaire,
			String cheminEffet,
			String objetType,
			String objetLibelle,
			UUID objetId,
			String objetNumero,
			String commentaire,
			String libelleTache,
			UUID decideurCompteId,
			UUID decideurCollabId,
			String decideurNom,
			String pourNom,
			int tentatives,
			Instant creeLe) {
	}

	/** Visible pour les tests. */
	public int reprendreIncertains() {
		// 1. Transaction courte : verrouiller les effets INCERTAIN échus et préparer les requêtes
		List<EffetARejouer> aRejouer = txTemplate.execute(status -> {
			List<WfAppelEffet> lot = appelEffetRepository.verrouillerLotIncertains(clock.instant(), TAILLE_LOT);
			return lot.stream().map(ae -> {
				WfInstance inst = instanceRepository.findById(ae.getInstanceId()).orElse(null);
				WfTache t = ae.getTacheId() != null ? tacheRepository.findById(ae.getTacheId()).orElse(null) : null;
				if (inst == null) {
					return null;
				}
				TypeObjetCatalogue tc = catalogue.findByObjetType(inst.getObjetType()).orElse(null);
				if (tc == null) {
					return null;
				}
				return new EffetARejouer(
						ae.getId(), ae.getCleIdempotence(), inst.getId(), ae.getTacheId(),
						ae.getEffet(), ae.getServiceProprietaire(), tc.cheminEffet(),
						inst.getObjetType(), tc.libelle(), inst.getObjetId(), inst.getObjetNumero(),
						t != null ? t.getCommentaire() : null,
						t != null ? t.getLibelle() : null,
						t != null ? t.getDecideParCompteId() : null,
						t != null ? t.getDecideParCollaborateurId() : null,
						t != null ? t.getDecideParNom() : "Système",
						t != null ? t.getPourNom() : null,
						ae.getTentatives(),
						ae.getCreeLe());
			}).filter(java.util.Objects::nonNull).toList();
		});

		if (aRejouer == null || aRejouer.isEmpty()) {
			return 0;
		}

		int traites = 0;
		for (EffetARejouer item : aRejouer) {
			// 2. HORS TRANSACTION : rejeu de l'appel APPLIQUER avec la même clé d'idempotence
			ActeurEffet acteur = new ActeurEffet(item.decideurCompteId(), item.decideurCollabId(),
					item.decideurNom(), item.pourNom());
			ResultatAppelEffet res = effetClient.appeler(
					item.serviceProprietaire(),
					item.cheminEffet(),
					new RequeteEffet(item.cleIdempotence(), "APPLIQUER", item.objetType(), item.objetId(),
							item.effet(), acteur, item.commentaire(), item.instanceId(), item.libelleTache()));

			// 3. Transaction courte : enregistrer l'issue
			txTemplate.executeWithoutResult(status -> traiterIssueReprise(item, res));
			traites++;
		}
		return traites;
	}

	private void traiterIssueReprise(EffetARejouer item, ResultatAppelEffet res) {
		WfInstance inst = instanceRepository.findByIdForUpdate(item.instanceId()).orElse(null);
		WfTache t = item.tacheId() != null ? tacheRepository.findByIdForUpdate(item.tacheId()).orElse(null) : null;
		WfAppelEffet ae = appelEffetRepository.findById(item.appelEffetId()).orElse(null);
		if (inst == null || ae == null) {
			return;
		}
		Instant maintenant = clock.instant();
		int tentatives = ae.getTentatives() + 1;
		ae.setTentatives(tentatives);

		if (res instanceof ResultatAppelEffet.Succes succes) {
			ae.setStatut(StatutAppelEffet.APPLIQUE);
			ae.setReponse(succes.corps());
			ae.setTermineLe(maintenant);
			appelEffetRepository.save(ae);

			if (t != null && t.getStatut() == StatutTache.RESERVEE) {
				t.setStatut(StatutTache.TRAITEE);
				t.setDecideLe(maintenant);
				tacheRepository.save(t);
			}
			if ("APPROUVER".equals(item.effet())) {
				inst.setStatut(StatutInstance.APPROUVEE);
				inst.setEtapeCouranteCode(null);
				inst.setTermineLe(maintenant);
			} else if ("REFUSER".equals(item.effet())) {
				inst.setStatut(StatutInstance.REFUSEE);
				inst.setEtapeCouranteCode(null);
				inst.setTermineLe(maintenant);
				inst.setResultatMotif(item.commentaire());
			}
			instanceRepository.save(inst);
			outboxService.publierEvenementWorkflow(UUID.randomUUID(), "INSTANCE_TERMINEE", inst, null, null, null);
			return;
		}

		if (res instanceof ResultatAppelEffet.RejetMetier rejet) {
			ae.setStatut(StatutAppelEffet.REJETE);
			ae.setReponse(rejet.corps());
			ae.setTermineLe(maintenant);
			appelEffetRepository.save(ae);
			if (t != null && t.getStatut() == StatutTache.RESERVEE) {
				t.setStatut(StatutTache.A_TRAITER);
				t.setCleIdempotence(null);
				tacheRepository.save(t);
			}
			if (item.decideurCompteId() != null) {
				outboxService.publierNotification(UUID.randomUUID(), item.decideurCompteId().toString(),
						"WEBSOCKET", "WF_DECISION", "Décision refusée par le service métier",
						rejet.message(), item.objetType(), item.objetId(), item.objetNumero());
			}
			enregistrerHistorique(inst.getId(), item.tacheId(), "EFFET_REJETE",
					Map.of("effet", item.effet(), "code", rejet.code()), maintenant);
			return;
		}

		// Toujours INCERTAIN : vérifier si 24 h dépassées (D-W29)
		if (Duration.between(item.creeLe(), maintenant).compareTo(DUREE_MAX_REPRISE) >= 0) {
			ae.setStatut(StatutAppelEffet.INCERTAIN);
			ae.setProchaineTentativeLe(null);
			ae.setTermineLe(maintenant);
			appelEffetRepository.save(ae);

			inst.setStatut(StatutInstance.ERREUR);
			inst.setTermineLe(maintenant);
			inst.setResultatMotif("Effet impossible à appliquer après 24 h de reprise.");
			instanceRepository.save(inst);

			String texte = TextesNotificationWorkflow.erreurEffet(item.objetLibelle(), item.objetNumero());
			UUID evId = UUID.randomUUID();
			outboxService.publierNotification(evId, "ADMIN", "WEBSOCKET", "WF_BLOQUEE",
					"Erreur d'enregistrement workflow", texte, item.objetType(), item.objetId(), item.objetNumero());
			outboxService.publierEvenementWorkflow(evId, "INSTANCE_TERMINEE", inst, null, null, null);
			enregistrerHistorique(inst.getId(), item.tacheId(), "INSTANCE_ERREUR",
					Map.of("effet", item.effet(), "tentatives", tentatives), maintenant);
			log.error("Instance {} passée en ERREUR après 24 h de reprise sur l'effet {}", inst.getId(), item.effet());
		} else {
			ae.setProchaineTentativeLe(maintenant.plus(delaiReprise(tentatives)));
			appelEffetRepository.save(ae);
		}
	}

	/** Cadence D-W29 : 1 min, 5 min, 15 min, puis toutes les heures. */
	static Duration delaiReprise(int tentatives) {
		return switch (tentatives) {
			case 1 -> Duration.ofMinutes(1);
			case 2 -> Duration.ofMinutes(5);
			case 3 -> Duration.ofMinutes(15);
			default -> Duration.ofHours(1);
		};
	}

	private void enregistrerHistorique(UUID instanceId, UUID tacheId, String typeEvenement,
			Map<String, Object> details, Instant horodatage) {
		WfHistorique h = new WfHistorique();
		h.setInstanceId(instanceId);
		h.setTacheId(tacheId);
		h.setTypeEvenement(typeEvenement);
		h.setActeurNom("Système");
		h.setDetails(details);
		h.setHorodatage(horodatage);
		historiqueRepository.save(h);
	}
}
