package com.hr.workflow.config;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DefinitionWorkflow.OptionsWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.ErreurDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.EtapeDefinition.TransitionDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Amorçage idempotent des 7 modèles publiés et 7 activations {@code HERITE} (§ 5.2, D-W3-07).
 * Tourne après {@link WorkflowContraintesRunner} ({@code @Order(200)}) et avant {@link DemarrageOrchestrateur}.
 */
@Component
@Order(200)
public class ModelesAmorceRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ModelesAmorceRunner.class);

	private final WfActivationRepository activationRepository;
	private final WfModeleRepository modeleRepository;
	private final WfModeleVersionRepository versionRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final DefinitionValidateur validateur;
	private final VerrouPlanificateur verrouPlanificateur;
	private final TransactionTemplate txTemplate;
	private final Clock clock;

	public ModelesAmorceRunner(
			WfActivationRepository activationRepository,
			WfModeleRepository modeleRepository,
			WfModeleVersionRepository versionRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			DefinitionValidateur validateur,
			VerrouPlanificateur verrouPlanificateur,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.activationRepository = activationRepository;
		this.modeleRepository = modeleRepository;
		this.versionRepository = versionRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.validateur = validateur;
		this.verrouPlanificateur = verrouPlanificateur;
		this.txTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		verrouPlanificateur.executerSiLibre("wf-modeles-amorce", this::amorcer);
	}

	/** Visible pour les tests. */
	public void amorcer() {
		txTemplate.executeWithoutResult(status -> {
			Instant maintenant = clock.instant();
			int activationsCreees = 0;
			for (TypeObjetCatalogue type : catalogue.listerTypes()) {
				if (activationRepository.findByObjetType(type.objetType()).isEmpty()) {
					WfActivation act = new WfActivation();
					act.setObjetType(type.objetType());
					act.setMode(ModeActivation.HERITE);
					act.setModifieLe(maintenant);
					activationRepository.save(act);
					activationsCreees++;
				}
			}

			int modelesCrees = 0;
			for (ModeleAmorce spec : definitionsAmorcees()) {
				List<ErreurDefinition> erreurs = validateur.valider(spec.definition(), spec.objetType());
				if (!validateur.estValide(erreurs)) {
					throw new IllegalStateException("Définition amorcée invalide pour " + spec.code() + " : " + erreurs);
				}
				if (modeleRepository.existsByCode(spec.code())) {
					continue;
				}
				WfModele modele = new WfModele();
				modele.setCode(spec.code());
				modele.setLibelle(spec.libelle());
				modele.setDescription(spec.description());
				modele.setObjetType(spec.objetType());
				modele.setSousType(null);
				modele.setActif(true);
				modele.setCreeLe(maintenant);
				modele.setModifieLe(maintenant);
				modele = modeleRepository.saveAndFlush(modele);

				WfModeleVersion v1 = new WfModeleVersion();
				v1.setModele(modele);
				v1.setNumero(1);
				v1.setStatut(StatutVersion.PUBLIEE);
				v1.setDefinition(codec.serialiser(spec.definition()));
				v1.setFormatDefinition(1);
				v1.setEmpreinte(codec.calculerEmpreinte(spec.definition()));
				v1.setModifieLe(maintenant);
				v1.setModifieParNom("Système");
				v1.setPublieLe(maintenant);
				versionRepository.saveAndFlush(v1);
				modelesCrees++;
			}

			log.info("Amorçage workflow terminé : {} modèle(s) publié(s) créé(s), {} activation(s) HERITE créée(s) (total : {} modèles, {} activations).",
					modelesCrees, activationsCreees, modeleRepository.count(), activationRepository.count());
		});
	}

	public record ModeleAmorce(String code, String libelle, String description, String objetType, DefinitionWorkflow definition) {
	}

	public static List<ModeleAmorce> definitionsAmorcees() {
		return List.of(
				new ModeleAmorce("M01_CONGE", "Circuit standard — Congé",
						"Reproduit le circuit M01 (responsable D-H03 puis RH).", "CONGE",
						new DefinitionWorkflow(1, "CONGE", new OptionsWorkflow(false), List.of(
								etapeValidation("VALID_RESPONSABLE", "Validation du responsable",
										new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
										new RepliSiVideDefinition("PASSER", null),
										true, null,
										new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null)),
								etapeValidation("VALID_RH", "Validation RH",
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
										new RepliSiVideDefinition("BLOQUER", null),
										true, null,
										new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null))
						))),
				new ModeleAmorce("M01_SORTIE", "Circuit standard — Autorisation de sortie",
						"Reproduit le circuit M01 pour les autorisations de sortie.", "AUTORISATION_SORTIE",
						new DefinitionWorkflow(1, "AUTORISATION_SORTIE", new OptionsWorkflow(false), List.of(
								etapeValidation("VALID_RESPONSABLE", "Validation du responsable",
										new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
										new RepliSiVideDefinition("PASSER", null),
										false, null,
										new DelaiDefinition(4, "HEURES", "date_jour"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null)),
								etapeValidation("VALID_RH", "Validation RH",
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
										new RepliSiVideDefinition("BLOQUER", null),
										false, null,
										new DelaiDefinition(4, "HEURES", "date_jour"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null))
						))),
				new ModeleAmorce("M01_MISSION", "Circuit standard — Ordre de mission",
						"Reproduit le circuit M01 pour les ordres de mission.", "ORDRE_MISSION",
						new DefinitionWorkflow(1, "ORDRE_MISSION", new OptionsWorkflow(false), List.of(
								etapeValidation("VALID_RESPONSABLE", "Validation du responsable",
										new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
										new RepliSiVideDefinition("PASSER", null),
										false, null,
										new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null)),
								etapeValidation("VALID_RH", "Validation RH",
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
										new RepliSiVideDefinition("BLOQUER", null),
										false, null,
										new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null))
						))),
				new ModeleAmorce("M09_POINTAGE_MANUEL", "Circuit standard — Pointage manuel",
						"Reproduit le circuit M09 (D-H03 avec repli RH, délai 24 h, expiration).", "POINTAGE_MANUEL",
						new DefinitionWorkflow(1, "POINTAGE_MANUEL", new OptionsWorkflow(false), List.of(
								etapeValidation("VALID_RESPONSABLE", "Validation du responsable",
										new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
										new RepliSiVideDefinition("ROLE", List.of("RH", "ADMIN")),
										false,
										new ConditionFeuille("signale", "egal", Boolean.TRUE),
										new DelaiDefinition(24, "HEURES", null),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("EXPIRER", null, null))
						))),
				new ModeleAmorce("M09_HEURES_SUP", "Circuit standard — Heures supplémentaires",
						"Reproduit le circuit M09 (approbation directe si créée par la RH, sinon validation RH).", "HEURES_SUP",
						new DefinitionWorkflow(1, "HEURES_SUP", new OptionsWorkflow(false), List.of(
								new EtapeDefinition(
										"VALID_RH", "Validation RH", "VALIDATION",
										new ConditionNon(new ConditionFeuille("cree_par_rh", "egal", Boolean.TRUE)),
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
										new RepliSiVideDefinition("BLOQUER", null),
										null, null,
										List.of("APPROUVER", "REFUSER"),
										false, null,
										new DelaiDefinition(720, "HEURES", "debut_fenetre"),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("EXPIRER", null, null),
										null, new TransitionDefinition("FIN_REFUS"), null
								)
						))),
				new ModeleAmorce("M02_DOCUMENT", "Circuit standard — Document administratif",
						"Reproduit le circuit M02 (préparation par la RH, délai 2 jours ouvrés).", "DOCUMENT",
						new DefinitionWorkflow(1, "DOCUMENT", new OptionsWorkflow(false), List.of(
								new EtapeDefinition(
										"TRAITEMENT_RH", "Préparer le document", "TACHE",
										null,
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), null),
										new RepliSiVideDefinition("BLOQUER", null),
										null, null, null, false, null,
										new DelaiDefinition(2, "JOURS_OUVRES", null),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null),
										null, null, null
								)
						))),
				new ModeleAmorce("M05_FORMATION", "Circuit standard — Demande de formation",
						"Reproduit le circuit M05 (responsable D-H03 puis RH).", "FORMATION",
						new DefinitionWorkflow(1, "FORMATION", new OptionsWorkflow(false), List.of(
								etapeValidation("VALID_RESPONSABLE", "Validation du responsable",
										new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
										new RepliSiVideDefinition("PASSER", null),
										false, null,
										new DelaiDefinition(2, "JOURS_OUVRES", null),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null)),
								etapeValidation("VALID_RH", "Validation RH",
										new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
										new RepliSiVideDefinition("BLOQUER", null),
										false, null,
										new DelaiDefinition(2, "JOURS_OUVRES", null),
										new RelancesDefinition(1, null, null),
										new EcheanceDefinition("RIEN", null, null))
						)))
		);
	}

	private static EtapeDefinition etapeValidation(
			String code, String libelle, IntervenantsDefinition inter, RepliSiVideDefinition repli,
			boolean controleAvant, com.hr.workflow.definition.Condition commentaireObligSi,
			DelaiDefinition delai, RelancesDefinition relances, EcheanceDefinition aEcheance) {
		return new EtapeDefinition(
				code, libelle, "VALIDATION", null, inter, repli, null, null,
				List.of("APPROUVER", "REFUSER"), controleAvant, commentaireObligSi,
				delai, relances, aEcheance, null, new TransitionDefinition("FIN_REFUS"), null);
	}
}
